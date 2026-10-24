package team.elrant.bubbles.xmpp

import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smackx.chat_markers.ChatMarkersManager
import org.jivesoftware.smackx.chat_markers.ChatMarkersState
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersElements
import org.jxmpp.jid.BareJid
import org.slf4j.LoggerFactory

/**
 * Read receipts as chat markers (XEP-0333): tells contacts what we have read and reports what they have read.
 *
 * @param own the signed-in account's JID; markers from our other devices are not contacts' receipts.
 * @param connection the current connection, `null` before sign-in.
 * @param onRead invoked with the contact and the stanza id of our message they have seen.
 */
class ReadReceipts(
  private val own: () -> BareJid,
  private val connection: () -> XMPPConnection?,
  private val onRead: (contact: BareJid, id: String) -> Unit,
) {
  /** Starts reporting contacts' "displayed" markers on [conn] to `onRead`. */
  internal fun listen(conn: XMPPConnection) {
    ChatMarkersManager.getInstanceFor(conn).addIncomingChatMarkerMessageListener { state, message, _ ->
      val contact = message.from?.asBareJid()?.takeIf { it != own() }
      val id = ChatMarkersElements.DisplayedExtension.from(message)?.id
      if (state == ChatMarkersState.displayed && contact != null && id != null) onRead(contact, id)
    }
  }

  /**
   * Tells [contact] that we have seen their message with stanza id [id] and everything before it. Blocking. Failures
   * are logged, not thrown.
   */
  fun markDisplayed(contact: BareJid, id: String) {
    val conn = connection()
    val entity = contact.asEntityBareJidIfPossible()
    if (conn == null || entity == null) return
    try {
      conn.sendStanza(
        conn.stanzaFactory.buildMessageStanza()
          .ofType(Message.Type.chat)
          .to(entity)
          .addExtension(ChatMarkersElements.DisplayedExtension(id))
          .build(),
      )
    } catch (e: SmackException) {
      logger.warn("Could not send a read receipt to {}: {}", contact, e.message)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.warn("Interrupted while sending a read receipt to {}: {}", contact, e.message)
    }
  }

  private companion object {
    val logger = LoggerFactory.getLogger(ReadReceipts::class.java)
  }
}
