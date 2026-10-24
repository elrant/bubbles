package team.elrant.bubbles.xmpp

import org.bouncycastle.jce.provider.BouncyCastleProvider
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
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersElements
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoMessage
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.listener.OmemoMessageListener
import org.jivesoftware.smackx.omemo.signal.SignalFileBasedOmemoStore
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.TrustState
import org.jivesoftware.smackx.pubsub.PubSubException
import org.jivesoftware.smackx.time.EntityTimeManager
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.ChatMessage
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.model.OmemoIdentity
import team.elrant.bubbles.model.Status
import team.elrant.bubbles.store.AccountLock
import team.elrant.bubbles.store.OmemoVault
import java.io.IOException
import java.net.InetAddress
import java.security.Security
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.time.ZoneOffset
import java.util.Date
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
 *   `source` and the message as it arrived (encryption, stanza id and whether it asks for read receipts). This
 *   includes messages that other devices of ours received (message carbons).
 * @param onRead invoked when `contact` reports having seen our message with stanza id `id` (chat markers).
 * @param onReadElsewhere invoked when another device of ours has read the conversation with `contact` up to their
 *   message with stanza id `id`.
 * @param onSynced invoked with messages that belong to the conversation with `contact` but didn't come in live,
 *   namely ones we sent from another device (message carbons).
 * @param onRosterChanged invoked with this connection when roster entries or contact presences change.
 * @param requireApproval whether OMEMO devices not seen before must be approved instead of trusted on first use;
 *   read whenever a device turns up.
 */
class ConnectedUser(
    val username: String,
    private val password: String,
    val serviceName: String,
    private val onMessage: (source: ConnectedUser, from: BareJid, message: ChatMessage) -> Unit = { _, _, _ ->
    },
    private val onRead: (source: ConnectedUser, contact: BareJid, id: String) -> Unit = { _, _, _ ->
    },
    private val onReadElsewhere: (source: ConnectedUser, contact: BareJid, id: String) -> Unit = { _, _, _ ->
    },
    private val onSynced: (source: ConnectedUser, contact: BareJid, messages: List<ChatMessage>) -> Unit =
        { _, _, _ ->
        },
    private val onRosterChanged: (source: ConnectedUser) -> Unit = {},
    private val requireApproval: () -> Boolean = { false },
) {
    private var connection: XMPPTCPConnection? = null
    private var chat: ChatManager? = null
    private var roster: Roster? = null
    private var vCards: VCardManager? = null
    private var omemo: OmemoManager? = null
    private var trust: DeviceTrust? = null
    private var vault: OmemoVault? = null

    /**
     * When this connection was about to log in. History pulled from the archive stops here: everything later reached
     * us live (or as a carbon copy), so fetching it again would only duplicate messages we sent ourselves, which
     * carry no id to recognise them by.
     */
    private var connectedAt = Date()

    /** Read receipts, both ways. */
    val readReceipts = ReadReceipts(
        own = { jid },
        connection = { connection },
        onRead = { contact, id -> onRead(this, contact, id) },
    )

    private val lock = AccountLock(address)

    private val carbons = CarbonSync(
        onReceived = { from, message -> onMessage(this, from, message) },
        onSent = { to, message -> onSynced(this, to, listOf(message)) },
        onContactRead = { contact, id -> onRead(this, contact, id) },
        onReadElsewhere = { contact, id -> onReadElsewhere(this, contact, id) },
    )

    /** The server-side message archive from before this connection was made; `null` before [initializeConnection]. */
    val archive: MessageArchive? get() = connection?.let { MessageArchive(it, jid, connectedAt) }

    /** The account's bare JID, `username@serviceName`. */
    val address: String get() = "$username@$serviceName"

    /** [address] as a JID. */
    val jid: BareJid get() = JidCreate.bareFrom(address)

    /**
     * Connects, logs in, sets up the chat manager and populates the roster. Blocking.
     *
     * First takes the account's [AccountLock], so only one Bubbles instance can be signed in as it at a time.
     *
     * TLS is required, and the certificate is checked against [serviceName] by [verifyCertHostname].
     * Throws if the connection or login fails.
     */
    fun initializeConnection() {
        lock.acquire()
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
        connectedAt = Date()
        conn.connect().login()
        logger.info("...welcome, {}!", username)

        chat = ChatManager.getInstanceFor(conn).also { manager ->
            manager.addIncomingListener { from, message, _ ->
                // An encrypted message may carry a plain-text hint body, which must not be shown as the message.
                if (message.isOmemo()) {
                    return@addIncomingListener
                }
                val body = message.body ?: return@addIncomingListener
                logger.info("Received message from {}: {}", from, body)
                val received =
                    ChatMessage(body, outgoing = false, id = message.stanzaId, markable = message.isMarkable())
                onMessage(this@ConnectedUser, from, received)
            }
        }
        carbons.enable(conn)
        readReceipts.listen(conn)
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
        if (omemo == null) {
            vault?.close()
            vault = null
        }
    }

    /**
     * Starts OMEMO on [conn]: opens the key store, publishes our device and bundle, and routes decrypted messages to
     * `onMessage`. Returns `null` if that fails, in which case messages go out unencrypted; signing in still works.
     */
    private fun setUpOmemo(conn: XMPPTCPConnection): OmemoManager? = try {
        SignalOmemoService.acknowledgeLicense()
        SignalOmemoService.setup()
        val keys = OmemoVault(address)
        val directory = keys.open()
        vault = keys
        (SignalOmemoService.getInstance() as SignalOmemoService).omemoStoreBackend =
            SignalFileBasedOmemoStore(directory.toFile())
        OmemoManager.getInstanceFor(conn).also { manager ->
            val deviceTrust = DeviceTrust(directory.resolve("trust.json"), requireApproval)
            trust = deviceTrust
            manager.setTrustCallback(deviceTrust)
            manager.addOmemoMessageListener(object : OmemoMessageListener {
                override fun onOmemoMessageReceived(stanza: Stanza, decrypted: OmemoMessage.Received) {
                    val body = decrypted.body ?: return
                    val from = (stanza as? Message)?.from?.asBareJid() ?: return
                    logger.info("Received encrypted message from {}", from)
                    val markable = (stanza as? Message)?.isMarkable() == true
                    onMessage(
                        this@ConnectedUser, from,
                        ChatMessage(
                            body,
                            outgoing = false,
                            encrypted = true,
                            id = stanza.stanzaId,
                            markable = markable,
                        ),
                    )
                    sealKeys()
                }

                override fun onOmemoCarbonCopyReceived(
                    direction: CarbonExtension.Direction,
                    carbonCopy: Message,
                    wrappingMessage: Message,
                    decryptedCarbonCopy: OmemoMessage.Received,
                ) {
                    val body = decryptedCarbonCopy.body ?: return
                    carbons.deliver(direction, carbonCopy, body, encrypted = true)
                    sealKeys()
                }
            })
            manager.initialize()
            sealKeys()
            logger.info("OMEMO ready, device {}", manager.deviceId)
        }
    } catch (e: SmackException) {
        omemoUnavailable("server error", e)
    } catch (e: XMPPException) {
        omemoUnavailable("server refused", e)
    } catch (e: CorruptedOmemoKeyException) {
        omemoUnavailable("stored keys are corrupted", e)
    } catch (e: PubSubException.NotALeafNodeException) {
        omemoUnavailable("the server's PEP node is not a leaf", e)
    } catch (e: IOException) {
        omemoUnavailable("keys can't be stored safely", e)
    }

    /** Logs why OMEMO can't run, for [reason], and returns no manager. */
    private fun omemoUnavailable(reason: String, cause: Exception): OmemoManager? {
        logger.warn("OMEMO unavailable, {}: {}", reason, cause.message)
        return null
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
     * sent as plain text, which the result reports along with the stanza id it went out under.
     */
    fun sendMessage(contact: BareJid, message: String, encrypt: Boolean): SendReport {
        val entity = contact.asEntityBareJidIfPossible()
        val conn = connection
        if (entity == null || conn == null) {
            logger.error("Cannot chat with {}: not a user JID or not connected", contact)
            return SendReport(SendResult.Failed)
        }
        return try {
            val manager = omemo?.takeIf { encrypt && it.contactSupportsOmemo(contact) }
            if (manager != null) {
                val sent = manager.encrypt(contact, message)
                if (sent.isMissingRecipients) {
                    logger.warn("Not encrypted for all devices of {}: {}", contact, sent.skippedDevices)
                }
                val stanza = sent.buildMessage(
                    conn.stanzaFactory.buildMessageStanza()
                        .addExtension(ChatMarkersElements.MarkableExtension.INSTANCE),
                    entity,
                )
                conn.sendStanza(stanza)
                sealKeys()
                SendReport(SendResult.Encrypted, stanza.stanzaId)
            } else {
                // Built here rather than through the chat manager, to learn the stanza id.
                val stanza = conn.stanzaFactory.buildMessageStanza()
                    .ofType(Message.Type.chat)
                    .to(entity)
                    .setBody(message)
                    .addExtension(ChatMarkersElements.MarkableExtension.INSTANCE)
                    .build()
                conn.sendStanza(stanza)
                SendReport(SendResult.Plain, stanza.stanzaId)
            }
        } catch (e: SmackException) {
            sendFailed(contact, "connection problem", e)
        } catch (e: XMPPException) {
            sendFailed(contact, "server refused it", e)
        } catch (e: UndecidedOmemoIdentityException) {
            sendFailed(contact, "undecided OMEMO identities ${e.undecidedDevices}", e)
        } catch (e: CryptoFailedException) {
            sendFailed(contact, "encryption failed", e)
        } catch (e: PubSubException.NotALeafNodeException) {
            sendFailed(contact, "OMEMO devices couldn't be looked up", e)
        } catch (e: IOException) {
            sendFailed(contact, "I/O error", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            sendFailed(contact, "interrupted", e)
        }
    }

    /** Logs that a message to [contact] wasn't sent, for [reason], and reports the failure. */
    private fun sendFailed(contact: BareJid, reason: String, cause: Exception): SendReport {
        logger.error("Could not send a message to {}: {}: {}", contact, reason, cause.message)
        return SendReport(SendResult.Failed)
    }

    /**
     * The UTC offset of [contact]'s clock (XEP-0202), asked of their most available device; `null` if they are offline
     * or before [initializeConnection]. Blocking. Throws if the device doesn't answer or doesn't support it.
     */
    fun localOffset(contact: BareJid): ZoneOffset? {
        val conn = connection
        val presence = roster?.getPresence(contact)?.takeIf { it.isAvailable }
        if (conn == null || presence == null) return null
        return EntityTimeManager.getInstanceFor(conn).getTime(presence.from)?.zonedDateTime?.offset
    }

    /**
     * Our own OMEMO device and its fingerprint; `null` if OMEMO isn't running. Blocking. Throws if the keys can't be
     * read.
     */
    fun ownIdentity(): OmemoIdentity? {
        val manager = omemo ?: return null
        return OmemoIdentity(
            jid, manager.deviceId, manager.ownFingerprint.toString(), OmemoIdentity.Trust.Trusted,
        )
    }

    /**
     * The OMEMO devices of [contact] (or of another of our own devices if it is [jid]) with their fingerprints and
     * trust. Empty if OMEMO isn't running. Blocking, and contacts' bundles are fetched. Throws if that fails.
     */
    fun identities(contact: BareJid): List<OmemoIdentity> {
        val manager = omemo
        val decisions = trust
        if (manager == null || decisions == null) return emptyList()
        return manager.getActiveFingerprints(contact).map { (device, fingerprint) ->
            OmemoIdentity(
                contact, device.deviceId, fingerprint.toString(),
                when (decisions.peek(device, fingerprint)) {
                    TrustState.trusted -> OmemoIdentity.Trust.Trusted
                    TrustState.untrusted -> OmemoIdentity.Trust.Untrusted
                    TrustState.undecided -> OmemoIdentity.Trust.Undecided
                },
            )
        }.sortedBy { it.deviceId }
    }

    /** Trusts or distrusts [identity] from now on. Does nothing if OMEMO isn't running. */
    fun setTrusted(identity: OmemoIdentity, trusted: Boolean) {
        val manager = omemo ?: return
        val device = OmemoDevice(identity.jid, identity.deviceId)
        val fingerprint = OmemoFingerprint(identity.fingerprint)
        if (trusted) {
            manager.trustOmemoIdentity(device, fingerprint)
        } else {
            manager.distrustOmemoIdentity(device, fingerprint)
        }
        sealKeys()
    }

    /**
     * The vCard (XEP-0054) photo of [contact], or of our own account if it is [jid]; `null` if they
     * have no vCard or no photo, or before [initializeConnection]. Blocking. Throws if the request fails.
     */
    fun avatar(contact: BareJid): ByteArray? {
        val manager = vCards ?: return null
        val vCard = try {
            if (contact == jid) {
                manager.loadVCard()
            } else {
                contact.asEntityBareJidIfPossible()?.let { manager.loadVCard(it) }
            }
        } catch (e: XMPPException.XMPPErrorException) {
            if (e.stanzaError.condition != StanzaError.Condition.item_not_found) throw e
            null
        }
        return vCard?.avatar?.takeIf { it.isNotEmpty() }
    }

    /** Closes the connection. Blocking. */
    fun disconnect() {
        connection?.disconnect()
        vault?.close()
        vault = null
        lock.release()
    }

    /** Saves the OMEMO keys back into the encrypted vault; a failure is logged, since the keys stay in memory. */
    private fun sealKeys() {
        try {
            vault?.flush()
        } catch (e: IOException) {
            logger.error("Could not save the OMEMO keys: {}", e.message)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectedUser::class.java)

        /** `subjectAltName` type of a DNS name (RFC 5280 GeneralName `dNSName`). */
        private const val DNS_NAME = 2

        // Smack's SASL login needs a Base64 encoder, normally registered by smack-java11, which isn't a
        // dependency; back it with the JDK's.
        init {
            // OMEMO's AES/GCM cipher needs a provider that accepts an IvParameterSpec, which the JDK's doesn't.
            Security.insertProviderAt(BouncyCastleProvider(), 1)
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

/**
 * The outcome of [ConnectedUser.sendMessage].
 *
 * @property result what became of the message.
 * @property id the stanza id it was sent under; `null` if it wasn't sent.
 */
data class SendReport(val result: SendResult, val id: String? = null)
