package team.elrant.bubbles.xmpp

import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterListener
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.stringencoder.Base64
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.model.Status
import java.net.InetAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

/**
 * The app's signed-in XMPP account: connection, roster, presence and messaging.
 *
 * Callbacks are invoked on Smack threads.
 *
 * @param onMessage invoked for every incoming chat message that has a body.
 * @param onRosterChanged invoked when roster entries or contact presences change.
 */
class ConnectedUser(
  val username: String,
  private val password: String,
  val serviceName: String,
  private val onMessage: (from: BareJid, body: String) -> Unit = { _, _ -> },
  private val onRosterChanged: () -> Unit = {},
) {
  private var connection: XMPPTCPConnection? = null
  private var chat: ChatManager? = null
  private var roster: Roster? = null

  val address: String get() = "$username@$serviceName"

  /**
   * Connects, logs in, sets up the chat manager and populates the roster. Blocking.
   */
  fun initializeConnection() {
    logger.info("logging in {}...", serviceName)
    val service = serviceName
    val conn = XMPPTCPConnection(
      XMPPTCPConnectionConfiguration.builder()
        .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
        .setXmppDomain(JidCreate.domainBareFrom(service))
        .setHostAddress(InetAddress.getByName(service))
        .setUsernameAndPassword(username, password)
        .setHostnameVerifier { _, session -> verifyCertHostname(service, session) }
        .setResource("meow")
        .build()
    )
    connection = conn
    conn.connect().login()
    logger.info("...welcome, {}!", username)

    chat = ChatManager.getInstanceFor(conn).also { manager ->
      manager.addIncomingListener { from, message, _ ->
        val body = message.body ?: return@addIncomingListener
        logger.info("Received message from {}: {}", from, body)
        onMessage(from, body)
      }
    }
    roster = Roster.getInstanceFor(conn).also {
      it.addRosterListener(object : RosterListener {
        override fun entriesAdded(addresses: Collection<Jid>) = onRosterChanged()
        override fun entriesUpdated(addresses: Collection<Jid>) = onRosterChanged()
        override fun entriesDeleted(addresses: Collection<Jid>) = onRosterChanged()
        override fun presenceChanged(presence: Presence) = onRosterChanged()
      })
      it.reloadAndWait()
    }
  }

  /** Snapshot of the roster with each contact's current presence. */
  fun contacts(): List<Contact> {
    val roster = roster ?: return emptyList()
    return roster.entries.map { entry ->
      val presence = roster.getPresence(entry.jid)
      Contact(
        jid = entry.jid,
        name = entry.name,
        groups = entry.groups.map { it.name },
        status = presence.toStatus(),
        personalMessage = presence.status?.takeIf { it.isNotBlank() },
      )
    }
  }

  /** Broadcasts our presence. Blocking. */
  fun setPresence(status: Status, personalMessage: String) {
    val conn = connection ?: return
    val presence = conn.stanzaFactory.buildPresenceStanza()
      .ofType(Presence.Type.available)
      .setMode(
        when (status) {
          Status.Busy -> Presence.Mode.dnd
          Status.Away -> Presence.Mode.away
          Status.Available, Status.Offline -> Presence.Mode.available
        }
      )
    if (personalMessage.isNotBlank()) presence.setStatus(personalMessage)
    conn.sendStanza(presence.build())
  }

  /** Sends [message] to [contact] (user@service.name). Blocking. */
  fun sendMessage(contact: BareJid, message: String) {
    try {
      val entity = contact.asEntityBareJidIfPossible()
      if (entity == null) {
        logger.error("Cannot chat with {}: not a user JID", contact)
        return
      }
      chat?.chatWith(entity)?.send(message)
    } catch (e: Exception) {
      logger.error("Error sending XMPP message: {}", e.message)
    }
  }

  /** Closes the connection. Blocking. */
  fun disconnect() {
    connection?.disconnect()
  }

  companion object {
    private val logger = LoggerFactory.getLogger(ConnectedUser::class.java)

    init {
      Base64.setEncoder(object : Base64.Encoder {
        private val encoder = java.util.Base64.getEncoder()
        private val decoder = java.util.Base64.getDecoder()

        override fun decode(string: String): ByteArray = decoder.decode(string)
        override fun encodeToString(input: ByteArray): String = encoder.encodeToString(input)
        override fun encodeToStringWithoutPadding(input: ByteArray): String = encoder.encodeToString(input)
        override fun encode(input: ByteArray): ByteArray = encoder.encode(input)
      })
    }

    private fun Presence.toStatus(): Status = when {
      !isAvailable -> Status.Offline
      mode == Presence.Mode.dnd -> Status.Busy
      mode == Presence.Mode.away || mode == Presence.Mode.xa -> Status.Away
      else -> Status.Available
    }

    /** Verifies [hostname] against the peer certificate's SAN/CN without relying on the SSLSession peer host. */
    private fun verifyCertHostname(hostname: String, session: SSLSession): Boolean = try {
      val cert = session.peerCertificates.firstOrNull() as? X509Certificate
      when {
        cert == null -> false
        cert.subjectAlternativeNames.orEmpty().any { it[0] == 2 && hostname.equals(it[1] as String, ignoreCase = true) } -> true
        else -> cert.subjectX500Principal.getName("RFC2253").split(",")
          .map { it.trim() }
          .any { it.startsWith("CN=") && hostname.equals(it.removePrefix("CN="), ignoreCase = true) }
      }
    } catch (e: Exception) {
      logger.error("Hostname verification error: {}", e.message)
      false
    }
  }
}
