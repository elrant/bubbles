package team.elrant.bubbles.xmpp

import com.fasterxml.jackson.annotation.JsonProperty
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.stringencoder.Base64
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Path
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

/**
 * Describes how the app's current user connects, behaves, sends messages, etc.
 *
 * @param password the user's password, or `null` if it was not persisted.
 * @param onMessage invoked (on a Smack thread) for every incoming chat message that has a body.
 */
class ConnectedUser(
  username: String,
  private val password: String?,
  serviceName: String,
  private val onMessage: (from: BareJid, body: String) -> Unit = { _, _ -> },
) : AbstractUser(username, serviceName) {

  private var connection: XMPPTCPConnection? = null
  private var chat: ChatManager? = null
  private var roster: Roster? = null

  /** The roster of this user. Only available after [initializeConnection]. */
  fun roster(): Roster = checkNotNull(roster) { "Roster is not initialized." }

  /** `true` if the password was not persisted, i.e. the user must re-enter it. */
  fun passwordUnInit(): Boolean = password == null

  val isLoggedIn: Boolean get() = connection?.isAuthenticated == true

  /**
   * Connects, logs in, sets up the chat manager and populates the roster. Blocking.
   */
  fun initializeConnection() {
    checkNotNull(password) { "Cannot connect: no password available." }
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
    roster = Roster.getInstanceFor(conn).also { it.reloadAndWait() }
  }

  /** Sends [message] to [contact] (user@service.name). Blocking. */
  fun sendMessage(contact: BareJid, message: String) {
    try {
      chat?.chatWith(contact as EntityBareJid)?.send(message)
    } catch (e: Exception) {
      logger.error("Error sending XMPP message: {}", e.message)
    }
  }

  /** Saves user information to a JSON file, including the password only if [savePassword] is set. */
  fun saveUserToFile(filename: String, savePassword: Boolean) {
    val data = UserData(username, serviceName, if (savePassword) password else null)
    MAPPER.writerWithDefaultPrettyPrinter().writeValue(File(filename), data)
    logger.info("User information saved to {}", filename)
  }

  private data class UserData(
    @param:JsonProperty("username") val username: String,
    @param:JsonProperty("serviceName") val serviceName: String,
    @param:JsonProperty("password") val password: String?,
  )

  companion object {
    private val logger = LoggerFactory.getLogger(ConnectedUser::class.java)
    private val MAPPER = ObjectMapper()

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

    /** Loads a [ConnectedUser] from a JSON file previously written by [saveUserToFile]. */
    @Throws(IOException::class)
    fun fromFile(file: Path): ConnectedUser {
      val data = MAPPER.readValue(file.toFile(), UserData::class.java)
      logger.info("User information loaded from {}", file)
      return ConnectedUser(data.username, data.password, data.serviceName)
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
