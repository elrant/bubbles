package team.elrant.bubbles.xmpp

import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smackx.forward.packet.Forwarded
import org.jivesoftware.smackx.mam.MamManager
import org.jxmpp.jid.BareJid
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.ChatMessage
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * Reads the server-side message archive (XEP-0313) of the account [own] on [connection].
 *
 * @param connection the logged-in connection.
 * @param own the account's JID, to tell sent from received messages.
 * @param before only messages from before this moment are fetched; later ones reached us live (or as a carbon copy),
 *   so fetching them again would only duplicate messages we sent ourselves.
 */
class MessageArchive(private val connection: XMPPConnection, private val own: BareJid, private val before: Date) {
    /**
     * One page of the archive, grouped by the other party of each message and oldest first. Blocking. Empty if the
     * server has no archive. Encrypted messages are skipped: their keys were used up when they first arrived, and
     * decrypting them again would disturb the sessions.
     *
     * @param contact only fetch messages exchanged with this contact; `null` for any.
     * @param max how many of the most recent messages to fetch.
     * @throws org.jivesoftware.smack.SmackException or [org.jivesoftware.smack.XMPPException] if the request fails.
     */
    fun fetch(contact: BareJid?, max: Int): Map<BareJid, List<ChatMessage>> {
        val archive = MamManager.getInstanceFor(connection)
        if (!archive.isSupported) return emptyMap()
        val query = MamManager.MamQueryArgs.builder()
            .limitResultsBefore(before)
            .setResultPageSizeTo(max)
            .queryLastPage()
        if (contact != null) query.limitResultsToJid(contact)
        val (encrypted, plain) = archive.queryArchive(query.build()).mamResultExtensions
            .partition { it.forwarded.forwardedStanza.isOmemo() }
        val history = plain.mapNotNull { archived(it.forwarded) }
        logger.info("Fetched {} archived messages, skipped {} encrypted", history.size, encrypted.size)
        return history.groupBy({ it.first }, { it.second }).mapValues { (_, messages) -> messages.sortedBy { it.time } }
    }

    /** The conversation partner and message of an archived [forwarded] message; `null` if it has no body. */
    private fun archived(forwarded: Forwarded<Message>): Pair<BareJid, ChatMessage>? {
        val message = forwarded.forwardedStanza
        val body = message.body
        val outgoing = message.from?.asBareJid() == own
        val other = (if (outgoing) message.to else message.from)?.asBareJid()
        if (body == null || other == null) return null
        val stamp = forwarded.delayInformation?.stamp
        val time = stamp?.let { LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault()) } ?: LocalDateTime.now()
        return other to ChatMessage(body, outgoing, time, id = message.stanzaId)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(MessageArchive::class.java)
    }
}
