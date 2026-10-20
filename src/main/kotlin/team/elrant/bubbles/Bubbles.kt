package team.elrant.bubbles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jxmpp.jid.BareJid
import org.slf4j.LoggerFactory
import team.elrant.bubbles.xmpp.ConnectedUser
import kotlin.system.exitProcess

data class ChatMessage(val sender: String, val body: String)

data class RosterContact(val jid: BareJid, val name: String?) {
  val label: String get() = name ?: jid.toString()
}

/** Application state; everything the UI renders is observable from here. */
class Bubbles {
  val logger = LoggerFactory.getLogger(Bubbles::class.java)!!
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  var user by mutableStateOf<ConnectedUser?>(null)
    private set
  var contacts by mutableStateOf<List<RosterContact>>(emptyList())
    private set
  var activeContact by mutableStateOf<RosterContact?>(null)
    private set
  private val histories = mutableStateMapOf<BareJid, MutableList<ChatMessage>>()

  private fun mutableHistory(contact: BareJid) = histories.getOrPut(contact) { mutableStateListOf() }

  fun history(contact: BareJid): List<ChatMessage> = mutableHistory(contact)

  /** Connects and logs in; throws on failure. Switches the UI to the roster on success. */
  suspend fun login(username: String, password: String, serviceName: String) {
    logger.info("Attempting login for: {}@{}", username, serviceName)
    val connected = withContext(Dispatchers.IO) {
      ConnectedUser(username, password, serviceName) { from, body ->
        scope.launch { mutableHistory(from.asBareJid()).add(ChatMessage(from.toString(), body)) }
      }.also { it.initializeConnection() }
    }
    val entries = connected.roster().entries
    logger.info("Roster entries: {}", entries)
    contacts = entries.map { RosterContact(it.jid, it.name) }
    user = connected
  }

  fun openChat(contact: RosterContact) {
    activeContact = contact
  }

  fun closeChat() {
    activeContact = null
  }

  fun sendMessage(text: String) {
    val user = user ?: return
    val contact = activeContact ?: return
    mutableHistory(contact.jid).add(ChatMessage(user.username, text))
    scope.launch(Dispatchers.IO) { user.sendMessage(contact.jid, text) }
  }

  fun shutdown(): Nothing {
    logger.info("Shutting down Bubbles...")
    exitProcess(0)
  }
}
