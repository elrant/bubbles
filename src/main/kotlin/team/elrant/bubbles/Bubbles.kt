package team.elrant.bubbles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.ChatMessage
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.model.Settings
import team.elrant.bubbles.model.Status
import team.elrant.bubbles.store.AccountStore
import team.elrant.bubbles.store.SettingsStore
import team.elrant.bubbles.xmpp.ConnectedUser
import team.elrant.bubbles.xmpp.SendResult
import java.io.IOException

/**
 * Application state; everything the UI renders is observable from here.
 *
 * State is Compose snapshot state, so call everything from the Swing thread (for [signIn], from a
 * coroutine on [Dispatchers.Main]). Network work is pushed to [Dispatchers.IO], and Smack callbacks
 * are moved back to the main dispatcher before they touch state.
 *
 * @param accounts where the "Remember me" address is kept.
 * @param settingsStore where the user's [Settings] are kept.
 */
class Bubbles(accounts: AccountStore = AccountStore(), settingsStore: SettingsStore = SettingsStore()) {
  private val logger = LoggerFactory.getLogger(Bubbles::class.java)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  /** The settings and the "Remember me" address. */
  val preferences = Preferences(accounts, settingsStore, scope)

  /** OMEMO fingerprints and trust of the signed-in account and its contacts. */
  val omemoKeys = OmemoKeys { user }

  /** Passwords remembered in the system keyring. */
  val credentials = Credentials()

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
  val conversations: List<BareJid>
    field = mutableStateListOf<BareJid>()
  private val histories = mutableStateMapOf<BareJid, SnapshotStateList<ChatMessage>>()

  /** Messages received per contact since their conversation was last opened; contacts without any are absent. */
  val unread: Map<BareJid, Int>
    field = mutableStateMapOf<BareJid, Int>()

  /**
   * Messages received per contact since sign-in; contacts without any are absent. A change means the
   * conversation window should ask for attention (see [Settings.raiseOnMessage]).
   */
  val incoming: Map<BareJid, Int>
    field = mutableStateMapOf<BareJid, Int>()

  private val avatars = mutableStateMapOf<BareJid, ImageBitmap>()

  /** The photo hash each avatar was last requested for (`null`: not known); see [requestAvatar]. */
  private val requestedAvatars = mutableMapOf<BareJid, String?>()
  private val avatarDispatcher = Dispatchers.IO.limitedParallelism(4)

  /** Read-only view; does not create an entry, so it is safe to call during composition. */
  fun history(contact: BareJid): List<ChatMessage> = histories[contact].orEmpty()

  /** The display picture of [jid] (a contact or ourselves), or `null` if there is none or it hasn't loaded yet. */
  fun avatar(jid: BareJid): ImageBitmap? = avatars[jid]

  /** The roster entry for [jid], or a [Contact.stranger] placeholder if they aren't in the roster. */
  fun contact(jid: BareJid): Contact = contacts.firstOrNull { it.jid == jid } ?: Contact.stranger(jid)

  /**
   * Signs in as [address] (`name@server`) and switches the UI to the contact list. Cancelling the
   * calling coroutine aborts the attempt.
   *
   * @param address account address, `name@server`.
   * @param password account password; not stored.
   * @param status initial presence, published right after login.
   * @param rememberMe whether to save [address] for next time; `false` forgets any saved one.
   * @param rememberPassword whether to keep [password] in the system keyring; `false` deletes any kept one.
   * @throws SignInException if [address] isn't a valid bare JID, or connecting or logging in fails.
   */
  suspend fun signIn(
    address: String,
    password: String,
    status: Status,
    rememberMe: Boolean,
    rememberPassword: Boolean,
  ) {
    val jid = try {
      JidCreate.entityBareFrom(address)
    } catch (e: XmppStringprepException) {
      throw SignInException("“$address” isn't a valid address. Use name@server.", e)
    }
    logger.info("Attempting sign-in for: {}", jid)

    // The callbacks drop events from a connection that is no longer (or not yet) the signed-in user.
    val connected = ConnectedUser(
      username = jid.localpart.toString(),
      password = password,
      serviceName = jid.domain.toString(),
      onMessage = { source, from, body, encrypted ->
        scope.launch { if (user === source) receive(from, body, encrypted) }
      },
      onRosterChanged = { source -> scope.launch { if (user === source) updateContacts(source) } },
      requireApproval = { preferences.settings.requireDeviceApproval },
    )
    connect(connected, status)

    preferences.rememberAddress(address.takeIf { rememberMe })
    if (rememberPassword) credentials.save(address, password) else credentials.forget(address)
    this.status = status
    personalMessage = ""
    user = connected
    requestAvatar(connected, connected.jid, null)
    updateContacts(connected)
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
    conversations.clear()
    histories.clear()
    unread.clear()
    incoming.clear()
    avatars.clear()
    requestedAvatars.clear()
    status = Status.Available
    personalMessage = ""
    disconnectInBackground(current)
  }

  /**
   * Connects and logs in [connected], then publishes [status] unless it is the default. On any
   * failure, including cancellation, the connection is closed again.
   *
   * @throws SignInException if connecting or logging in fails.
   */
  private suspend fun connect(connected: ConnectedUser, status: Status) {
    var established = false
    val failure = try {
      runInterruptible(Dispatchers.IO) {
        connected.initializeConnection()
        if (status != Status.Available) connected.setPresence(status, "")
      }
      established = true
      null
    } catch (e: SmackException) {
      e
    } catch (e: XMPPException) {
      e
    } catch (e: IOException) {
      e
    } finally {
      // Cancellation and unexpected errors propagate unchanged, but must not leave the connection open either.
      if (!established) disconnectInBackground(connected)
    }
    if (failure != null) {
      logger.error("Could not sign in as {}", connected.jid, failure)
      throw SignInException(failure.message ?: failure::class.simpleName.orEmpty(), failure)
    }
  }

  /** Closes [connected] on [Dispatchers.IO] without waiting; a failure to close is only logged. */
  private fun disconnectInBackground(connected: ConnectedUser) {
    scope.launch(Dispatchers.IO) {
      runCatching { connected.disconnect() }
        .onFailure { logger.warn("Could not close the connection of {}: {}", connected.jid, it.message) }
    }
  }

  /**
   * Sets our presence and personal message and broadcasts both.
   *
   * @param status new presence; defaults to the current one.
   * @param personalMessage new personal message, trimmed; defaults to the current one.
   */
  fun changePresence(status: Status = this.status, personalMessage: String = this.personalMessage) {
    this.status = status
    this.personalMessage = personalMessage.trim()
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

  /**
   * Moves [jid] from roster group [from] into roster group [to] on the server, in the background. Other groups
   * of the contact are kept. A `null` [from] keeps all current groups (the contact was dragged out of a built-in
   * group); a `null` [to] only removes [from], leaving the contact ungrouped if it was its only group. Failures
   * are only logged; on success the roster push refreshes [contacts].
   */
  fun moveContact(jid: BareJid, from: String?, to: String?) {
    val current = user ?: return
    val groups = contact(jid).groups.filter { it != from }.let { if (to == null || to in it) it else it + to }
    scope.launch {
      try {
        runInterruptible(Dispatchers.IO) { current.setGroups(jid, groups) }
      } catch (e: SmackException) {
        logger.error("Could not move {} to group {}: {}", jid, to, e.message)
      } catch (e: XMPPException) {
        logger.error("Server refused moving {} to group {}: {}", jid, to, e.message)
      }
    }
  }

  /** Opens a conversation window with [jid], unless one is already open, and marks its messages read. */
  fun openConversation(jid: BareJid) {
    if (jid !in conversations) conversations += jid
    unread -= jid
    user?.let { requestAvatar(it, jid, contact(jid).avatarHash) }
  }

  /** Closes the conversation window with [jid]. Its history is kept until sign-out. */
  fun closeConversation(jid: BareJid) {
    conversations -= jid
  }

  /**
   * Adds [text] to the history with [to] and sends it in the background, encrypted if [Settings.encryptMessages] is
   * on and the contact supports it. The history entry then shows whether it was encrypted, or that it failed.
   */
  fun sendMessage(to: BareJid, text: String) {
    val current = user ?: return
    val history = mutableHistory(to)
    val pending = ChatMessage(text, outgoing = true, pending = true)
    history += pending
    val encrypt = preferences.settings.encryptMessages
    scope.launch {
      val result = withContext(Dispatchers.IO) { current.sendMessage(to, text, encrypt) }
      val index = history.indexOf(pending)
      if (index >= 0) {
        history[index] = pending.copy(
          encrypted = result == SendResult.Encrypted,
          failed = result == SendResult.Failed,
          pending = false,
        )
      }
    }
  }

  /**
   * Records an incoming message. Its conversation window is popped up, or, if it is closed and
   * [Settings.openOnMessage] is off, the contact is marked unread.
   */
  private fun receive(from: BareJid, body: String, encrypted: Boolean) {
    mutableHistory(from) += ChatMessage(body, outgoing = false, encrypted = encrypted)
    incoming[from] = (incoming[from] ?: 0) + 1
    if (from in conversations || preferences.settings.openOnMessage) {
      openConversation(from)
    } else {
      unread[from] = (unread[from] ?: 0) + 1
    }
  }

  /** Takes a fresh roster snapshot from [connected] and requests any new or changed avatars. */
  private fun updateContacts(connected: ConnectedUser) {
    contacts = connected.contacts()
    contacts.forEach { requestAvatar(connected, it.jid, it.avatarHash) }
  }

  /**
   * Fetches and decodes the avatar of [jid] in the background the first time it is requested, and
   * again whenever their presence advertises a different photo [hash] (`null`: not advertised).
   * Failures are only logged; the fetch is retried when the hash next changes.
   */
  private fun requestAvatar(connected: ConnectedUser, jid: BareJid, hash: String?) {
    if (jid in requestedAvatars && (hash == null || hash == requestedAvatars[jid])) return
    requestedAvatars[jid] = hash
    scope.launch { updateAvatar(connected, jid, hash) }
  }

  /** Loads the avatar of [jid] for [requestAvatar]; on failure, logs and keeps the current one. */
  private suspend fun updateAvatar(connected: ConnectedUser, jid: BareJid, hash: String?) {
    try {
      val picture = loadAvatar(connected, jid)
      // Drop the result if we signed out or a newer request superseded this one meanwhile.
      if (user === connected && requestedAvatars[jid] == hash) {
        if (picture == null) avatars -= jid else avatars[jid] = picture
      }
    } catch (e: SmackException) {
      logger.warn("Could not fetch the avatar of {}: {}", jid, e.message)
    } catch (e: XMPPException) {
      logger.warn("Server returned an error for the avatar of {}: {}", jid, e.message)
    } catch (e: IllegalArgumentException) {
      logger.warn("Could not decode the avatar of {}: {}", jid, e.message)
    }
  }

  /**
   * Fetches and decodes the avatar of [jid]; `null` if they have none. Cancelling interrupts the
   * blocking fetch.
   *
   * @throws SmackException or [XMPPException] if the request fails.
   * @throws IllegalArgumentException if the image can't be decoded.
   */
  private suspend fun loadAvatar(connected: ConnectedUser, jid: BareJid): ImageBitmap? =
    runInterruptible(avatarDispatcher) {
      connected.avatar(jid)?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
    }

  /** The history with [contact], created if missing. Not for use during composition (see [history]). */
  private fun mutableHistory(contact: BareJid) = histories.getOrPut(contact) { mutableStateListOf() }

  /** Disconnects for good. Blocking; the UI exits the process afterwards (see `exitApplication`). */
  fun shutdown() {
    logger.info("Shutting down Bubbles...")
    runCatching { user?.disconnect() }
  }
}
