package team.elrant.bubbles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.ChatMessage
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.model.Status
import team.elrant.bubbles.store.AccountStore
import team.elrant.bubbles.xmpp.ConnectedUser

/**
 * Application state; everything the UI renders is observable from here.
 *
 * State is Compose snapshot state, so call everything from the Swing thread (for [signIn], from a
 * coroutine on [Dispatchers.Main]). Network work is pushed to [Dispatchers.IO], and Smack callbacks
 * are moved back to the main dispatcher before they touch state.
 *
 * @param accounts where the "Remember me" address is kept.
 */
class Bubbles(private val accounts: AccountStore = AccountStore()) {
  private val logger = LoggerFactory.getLogger(Bubbles::class.java)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  /** Address remembered from the last "Remember me" sign-in. */
  val savedAddress: String? = accounts.load()

  /** The signed-in account, or `null` while on the sign-in screen. */
  var user by mutableStateOf<ConnectedUser?>(null)
    private set

  /** Latest roster snapshot of [user]; refreshed whenever the roster or a presence changes. */
  var contacts by mutableStateOf<List<Contact>>(emptyList())
    private set

  /** Our own presence, as last chosen by the user. */
  var status by mutableStateOf(Status.Available)
    private set

  /** Our own personal (presence status) message; empty for none. */
  var personalMessage by mutableStateOf("")
    private set

  /** Contacts with an open conversation window, in opening order. */
  val conversations: List<BareJid> get() = openConversations
  private val openConversations = mutableStateListOf<BareJid>()
  private val histories = mutableStateMapOf<BareJid, SnapshotStateList<ChatMessage>>()

  /** Read-only view; does not create an entry, so it is safe to call during composition. */
  fun history(contact: BareJid): List<ChatMessage> = histories[contact].orEmpty()

  /** The roster entry for [jid], or a [Contact.stranger] placeholder if they aren't in the roster. */
  fun contact(jid: BareJid): Contact = contacts.firstOrNull { it.jid == jid } ?: Contact.stranger(jid)

  /**
   * Signs in as [address] (`name@server`) and switches the UI to the contact list. Throws on
   * failure; cancelling the calling coroutine aborts the attempt.
   *
   * @param status initial presence, published right after login.
   * @param rememberMe whether to save [address] for next time; `false` forgets any saved one.
   * @throws IllegalArgumentException if [address] isn't a valid bare JID.
   */
  suspend fun signIn(address: String, password: String, status: Status, rememberMe: Boolean) {
    val jid = try {
      JidCreate.entityBareFrom(address)
    } catch (e: XmppStringprepException) {
      throw IllegalArgumentException("“$address” isn't a valid address. Use name@server.", e)
    }
    logger.info("Attempting sign-in for: {}", jid)

    lateinit var connected: ConnectedUser
    connected = ConnectedUser(
      username = jid.localpart.toString(),
      password = password,
      serviceName = jid.domain.toString(),
      onMessage = { from, body -> scope.launch { if (user === connected) receive(from, body) } },
      onRosterChanged = { scope.launch { if (user === connected) contacts = connected.contacts() } },
    )
    try {
      runInterruptible(Dispatchers.IO) {
        connected.initializeConnection()
        if (status != Status.Available) connected.setPresence(status, "")
      }
    } catch (e: Throwable) {
      scope.launch(Dispatchers.IO) { runCatching { connected.disconnect() } }
      if (e !is kotlinx.coroutines.CancellationException) logger.error("couldn't establish connection", e)
      throw e
    }

    withContext(Dispatchers.IO) { if (rememberMe) accounts.save(address) else accounts.clear() }
    this.status = status
    personalMessage = ""
    user = connected
    contacts = connected.contacts()
    logger.info("Roster: {}", contacts)
  }

  /**
   * Returns to the sign-in screen, dropping contacts, conversations and history. The connection
   * is closed in the background. Does nothing when not signed in.
   */
  fun signOut() {
    val current = user ?: return
    user = null
    contacts = emptyList()
    openConversations.clear()
    histories.clear()
    status = Status.Available
    personalMessage = ""
    scope.launch(Dispatchers.IO) { runCatching { current.disconnect() } }
  }

  /** Sets our presence and broadcasts it. */
  fun changeStatus(status: Status) {
    this.status = status
    publishPresence()
  }

  /** Sets our personal message (trimmed) and broadcasts it along with the current [status]. */
  fun changePersonalMessage(message: String) {
    personalMessage = message.trim()
    publishPresence()
  }

  /** Sends the current [status] and [personalMessage] in the background; failures are only logged. */
  private fun publishPresence() {
    val current = user ?: return
    val status = status
    val message = personalMessage
    scope.launch(Dispatchers.IO) {
      runCatching { current.setPresence(status, message) }
        .onFailure { logger.error("Could not update presence: {}", it.message) }
    }
  }

  /** Opens a conversation window with [jid], unless one is already open. */
  fun openConversation(jid: BareJid) {
    if (jid !in openConversations) openConversations += jid
  }

  /** Closes the conversation window with [jid]. Its history is kept until sign-out. */
  fun closeConversation(jid: BareJid) {
    openConversations -= jid
  }

  /** Adds [text] to the history with [to] and sends it in the background. */
  fun sendMessage(to: BareJid, text: String) {
    val current = user ?: return
    mutableHistory(to) += ChatMessage(text, outgoing = true)
    scope.launch(Dispatchers.IO) { current.sendMessage(to, text) }
  }

  /** Records an incoming message and pops up its conversation window. */
  private fun receive(from: BareJid, body: String) {
    mutableHistory(from) += ChatMessage(body, outgoing = false)
    openConversation(from)
  }

  /** The history with [contact], created if missing. Not for use during composition (see [history]). */
  private fun mutableHistory(contact: BareJid) = histories.getOrPut(contact) { mutableStateListOf() }

  /** Disconnects for good. Blocking; the UI exits the process afterwards (see `exitApplication`). */
  fun shutdown() {
    logger.info("Shutting down Bubbles...")
    runCatching { user?.disconnect() }
  }
}
