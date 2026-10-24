package team.elrant.bubbles.xmpp

import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Presence
import org.jivesoftware.smack.packet.StandardExtensionElement
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.StanzaError
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.RosterListener
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.stringencoder.Base64
import org.jivesoftware.smackx.carbons.packet.CarbonExtension
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService
import org.jivesoftware.smackx.omemo.util.OmemoConstants
import org.jivesoftware.smackx.pubsub.PubSubException
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.model.Status
import team.elrant.bubbles.store.omemoDirectory
import java.io.IOException
import java.net.InetAddress
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

/**
 * The app's signed-in XMPP account: connection, roster, presence and messaging.
 *
 * Nothing happens on the network until [initializeConnection]. Callbacks are invoked on Smack
 * threads.
 *
 * @property username localpart of the account's JID.
 * @param password account password; used only for login.
 * @property serviceName XMPP domain; also resolved as the host to connect to.
 * @param onMessage invoked for every incoming chat message that has a body, with this connection as
 *   `source`; `encrypted` tells whether it arrived OMEMO-encrypted.
 * @param onRosterChanged invoked with this connection when roster entries or contact presences change.
 */
class ConnectedUser(
  val username: String,
  private val password: String,
  val serviceName: String,
  private val onMessage: (source: ConnectedUser, from: BareJid, body: String, encrypted: Boolean) -> Unit =
    { _, _, _, _ ->
    },
  private val onRosterChanged: (source: ConnectedUser) -> Unit = {},
) {
  private var connection: XMPPTCPConnection? = null
  private var chat: ChatManager? = null
  private var roster: Roster? = null
  private var vCards: VCardManager? = null
  private var omemo: OmemoManager? = null

  /** The account's bare JID, `username@serviceName`. */
  val address: String get() = "$username@$serviceName"

  /** [address] as a JID. */
  val jid: BareJid get() = JidCreate.bareFrom(address)

  /**
   * Connects, logs in, sets up the chat manager and populates the roster. Blocking.
   *
   * TLS is required, and the certificate is checked against [serviceName] by [verifyCertHostname].
   * Throws if the connection or login fails.
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
        .build(),
    )
    connection = conn
    conn.connect().login()
    logger.info("...welcome, {}!", username)

    chat = ChatManager.getInstanceFor(conn).also { manager ->
      manager.addIncomingListener { from, message, _ ->
        // An encrypted message may carry a plain-text hint body, which must not be shown as the message.
        if (message.hasExtension(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL)) {
          return@addIncomingListener
        }
        val body = message.body ?: return@addIncomingListener
        logger.info("Received message from {}: {}", from, body)
        onMessage(this@ConnectedUser, from, body, false)
      }
    }
    roster = Roster.getInstanceFor(conn).apply {
      addRosterListener(object : RosterListener {
        override fun entriesAdded(addresses: Collection<Jid>) = onRosterChanged(this@ConnectedUser)

        override fun entriesUpdated(addresses: Collection<Jid>) = onRosterChanged(this@ConnectedUser)

        override fun entriesDeleted(addresses: Collection<Jid>) = onRosterChanged(this@ConnectedUser)

        override fun presenceChanged(presence: Presence) = onRosterChanged(this@ConnectedUser)
      })
      reloadAndWait()
    }
    vCards = VCardManager.getInstanceFor(conn)
    omemo = setUpOmemo(conn)
  }

  /**
   * Starts OMEMO on [conn]: opens the key store, publishes our device and bundle, and routes decrypted messages to
   * `onMessage`. Returns `null` if that fails, in which case messages go out unencrypted; signing in still works.
   */
  private fun setUpOmemo(conn: XMPPTCPConnection): OmemoManager? = try {
    SignalOmemoService.acknowledgeLicense()
    SignalOmemoService.setup()
    (SignalOmemoService.getInstance() as SignalOmemoService).omemoStoreBackend =
      SignalFileBasedOmemoStore(omemoDirectory(address).toFile())
    OmemoManager.getInstanceFor(conn).also { manager ->
      manager.setTrustCallback(TofuTrust(omemoDirectory(address).resolve("trust.json")))
      manager.addOmemoMessageListener(object : OmemoMessageListener {
        override fun onOmemoMessageReceived(stanza: Stanza, decrypted: OmemoMessage.Received) {
          val body = decrypted.body ?: return
          val from = (stanza as? Message)?.from?.asBareJid() ?: return
          logger.info("Received encrypted message from {}", from)
          onMessage(this@ConnectedUser, from, body, true)
        }

        override fun onOmemoCarbonCopyReceived(
          direction: CarbonExtension.Direction,
          carbonCopy: Message,
          wrappingMessage: Message,
          decryptedCarbonCopy: OmemoMessage.Received,
        ) = Unit
      })
      manager.initialize()
      logger.info("OMEMO ready, device {}", manager.deviceId)
    }
  } catch (e: SmackException) {
    logger.warn(OMEMO_UNAVAILABLE, e.message)
    null
  } catch (e: XMPPException) {
    logger.warn(OMEMO_UNAVAILABLE, e.message)
    null
  } catch (e: CorruptedOmemoKeyException) {
    logger.warn("OMEMO unavailable, stored keys are corrupted: {}", e.message)
    null
  } catch (e: PubSubException.NotALeafNodeException) {
    logger.warn("OMEMO unavailable, the server's PEP node is not a leaf: {}", e.message)
    null
  } catch (e: IOException) {
    logger.warn(OMEMO_UNAVAILABLE, e.message)
    null
  }

  /** Snapshot of the roster with each contact's current presence; empty before [initializeConnection]. */
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
        avatarHash = presence.avatarHash(),
      )
    }
  }

  /**
   * Broadcasts our presence. Blocking. [Status.Offline] is sent as plain available, since it
   * isn't selectable.
   *
   * @param status presence to broadcast.
   * @param personalMessage presence status text; blank sends none.
   */
  fun setPresence(status: Status, personalMessage: String) {
    val conn = connection ?: return
    val presence = conn.stanzaFactory.buildPresenceStanza()
      .ofType(Presence.Type.available)
      .setMode(
        when (status) {
          Status.Busy -> Presence.Mode.dnd
          Status.Away -> Presence.Mode.away
          Status.Available, Status.Offline -> Presence.Mode.available
        },
      )
    if (personalMessage.isNotBlank()) presence.setStatus(personalMessage)
    conn.sendStanza(presence.build())
  }

  /**
   * Replaces the roster groups of [contact] with [groups] (empty: no group), keeping its nickname. Blocking.
   * Does nothing if [contact] isn't in the roster or before [initializeConnection]. Throws if the request fails.
   */
  fun setGroups(contact: BareJid, groups: List<String>) {
    val roster = roster ?: return
    val entry = roster.getEntry(contact)
    if (entry == null) {
      logger.warn("Cannot change the groups of {}: not in the roster", contact)
      return
    }
    // A roster set for an existing item updates it. Unlike createItemAndRequestSubscription, this
    // sends no subscription request.
    roster.createItem(contact, entry.name, groups.toTypedArray())
  }

  /**
   * Sends [message] to [contact] (user@service.name). Blocking. Failures are logged, not thrown.
   *
   * With [encrypt], the message is OMEMO-encrypted if OMEMO is running and [contact] supports it; otherwise it is
   * sent as plain text, which the result reports.
   */
  fun sendMessage(contact: BareJid, message: String, encrypt: Boolean): SendResult {
    val entity = contact.asEntityBareJidIfPossible()
    val conn = connection
    if (entity == null || conn == null) {
      logger.error("Cannot chat with {}: not a user JID or not connected", contact)
      return SendResult.Failed
    }
    return try {
      val manager = omemo?.takeIf { encrypt && it.contactSupportsOmemo(contact) }
      if (manager != null) {
        val sent = manager.encrypt(contact, message)
        if (sent.isMissingRecipients) {
          logger.warn("Not encrypted for all devices of {}: {}", contact, sent.skippedDevices)
        }
        conn.sendStanza(sent.buildMessage(conn.stanzaFactory.buildMessageStanza(), entity))
        SendResult.Encrypted
      } else {
        chat?.chatWith(entity)?.send(message)
        SendResult.Plain
      }
    } catch (e: SmackException) {
      logger.error(SEND_ERROR, e.message)
      SendResult.Failed
    } catch (e: XMPPException) {
      logger.error(SEND_ERROR, e.message)
      SendResult.Failed
    } catch (e: UndecidedOmemoIdentityException) {
      logger.error("Not sending to {}, undecided OMEMO identities: {}", contact, e.undecidedDevices)
      SendResult.Failed
    } catch (e: CryptoFailedException) {
      logger.error("Could not encrypt the message to {}: {}", contact, e.message)
      SendResult.Failed
    } catch (e: PubSubException.NotALeafNodeException) {
      logger.error("Could not look up OMEMO devices of {}: {}", contact, e.message)
      SendResult.Failed
    } catch (e: IOException) {
      logger.error(SEND_ERROR, e.message)
      SendResult.Failed
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.error("Interrupted while sending XMPP message: {}", e.message)
      SendResult.Failed
    }
  }

  /**
   * The vCard (XEP-0054) photo of [contact], or of our own account if it is [jid]; `null` if they
   * have no vCard or no photo, or before [initializeConnection]. Blocking. Throws if the request fails.
   */
  fun avatar(contact: BareJid): ByteArray? {
    val manager = vCards ?: return null
    val vCard = try {
      if (contact == jid) manager.loadVCard() else contact.asEntityBareJidIfPossible()?.let { manager.loadVCard(it) }
    } catch (e: XMPPException.XMPPErrorException) {
      if (e.stanzaError.condition != StanzaError.Condition.item_not_found) throw e
      null
    }
    return vCard?.avatar?.takeIf { it.isNotEmpty() }
  }

  /** Closes the connection. Blocking. */
  fun disconnect() {
    connection?.disconnect()
  }

  companion object {
    private val logger = LoggerFactory.getLogger(ConnectedUser::class.java)

    private const val OMEMO_UNAVAILABLE = "OMEMO unavailable: {}"
    private const val SEND_ERROR = "Error sending XMPP message: {}"

    /** `subjectAltName` type of a DNS name (RFC 5280 GeneralName `dNSName`). */
    private const val DNS_NAME = 2

    // Smack's SASL login needs a Base64 encoder, normally registered by smack-java11, which isn't a
    // dependency; back it with the JDK's.
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

    /** Maps a contact's presence to a [Status]; extended away (`xa`) counts as [Status.Away]. */
    private fun Presence.toStatus(): Status = when {
      !isAvailable -> Status.Offline
      mode == Presence.Mode.dnd -> Status.Busy
      mode == Presence.Mode.away || mode == Presence.Mode.xa -> Status.Away
      else -> Status.Available
    }

    /**
     * The vCard photo hash advertised in this presence (XEP-0153): `""` if the contact has no photo,
     * `null` if the presence doesn't say. Unavailable presences and clients without XEP-0153 don't
     * say, which must not be mistaken for a changed photo.
     */
    private fun Presence.avatarHash(): String? {
      val update = getExtensionElement("x", "vcard-temp:x:update") as? StandardExtensionElement
      val photo = update?.getFirstElement("photo") ?: return null
      return photo.text.orEmpty().trim()
    }

    /** Verifies [hostname] against the peer certificate's SAN/CN without relying on the SSLSession peer host. */
    private fun verifyCertHostname(hostname: String, session: SSLSession): Boolean = try {
      val cert = session.peerCertificates.firstOrNull() as? X509Certificate
      when {
        cert == null -> false

        cert.subjectAlternativeNames.orEmpty()
          .any { it[0] == DNS_NAME && hostname.equals(it[1] as String, ignoreCase = true) } -> true

        else -> cert.subjectX500Principal.getName("RFC2253").split(",")
          .map { it.trim() }
          .any { it.startsWith("CN=") && hostname.equals(it.removePrefix("CN="), ignoreCase = true) }
      }
    } catch (e: SSLPeerUnverifiedException) {
      logger.error("Hostname verification failed, peer is unverified: {}", e.message)
      false
    } catch (e: CertificateParsingException) {
      logger.error("Hostname verification failed, unparsable certificate: {}", e.message)
      false
    }
  }
}

/** What became of a message passed to [ConnectedUser.sendMessage]. */
enum class SendResult {
  /** Sent OMEMO-encrypted. */
  Encrypted,

  /** Sent as plain text. */
  Plain,

  /** Not sent. */
  Failed,
}
