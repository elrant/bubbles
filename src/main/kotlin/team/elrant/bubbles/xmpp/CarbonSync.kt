package team.elrant.bubbles.xmpp

import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smackx.carbons.CarbonManager
import org.jivesoftware.smackx.carbons.packet.CarbonExtension
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersElements
import org.jxmpp.jid.BareJid
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.ChatMessage

/**
 * Mirrors the conversations of our other devices onto this one with message carbons (XEP-0280).
 *
 * @param onReceived invoked with the sender and message when another device of ours received a message.
 * @param onSent invoked with the recipient and message when another device of ours sent one.
 * @param onContactRead invoked with the contact and our message's id when a contact's read receipt for a message sent
 *   from another device of ours reaches us.
 * @param onReadElsewhere invoked with the contact and their message's id when another device of ours sent a read
 *   receipt, i.e. the conversation was read there.
 */
internal class CarbonSync(
  private val onReceived: (from: BareJid, message: ChatMessage) -> Unit,
  private val onSent: (to: BareJid, message: ChatMessage) -> Unit,
  private val onContactRead: (contact: BareJid, id: String) -> Unit,
  private val onReadElsewhere: (contact: BareJid, id: String) -> Unit,
) {
  /**
   * Asks the server to copy messages exchanged with our other devices to [connection] and routes plain-text copies to
   * the callbacks; encrypted ones arrive through the OMEMO listener, which calls [deliver]. Not fatal if the server
   * can't or won't: we then only see what reaches this device.
   */
  fun enable(connection: XMPPConnection) {
    val carbons = CarbonManager.getInstanceFor(connection)
    carbons.addCarbonCopyReceivedListener { direction, carbonCopy, _ ->
      val body = carbonCopy.body
      if (body != null && !carbonCopy.isOmemo()) {
        deliver(direction, carbonCopy, body, encrypted = false)
      } else {
        deliverMarker(direction, carbonCopy)
      }
    }
    try {
      if (carbons.isSupportedByServer) {
        carbons.enableCarbons()
        logger.info("Message carbons enabled")
      } else {
        logger.info("The server doesn't support message carbons; other devices' messages won't be mirrored")
      }
    } catch (e: SmackException) {
      logger.warn("Could not enable message carbons: {}", e.message)
    } catch (e: XMPPException) {
      logger.warn("Server refused message carbons: {}", e.message)
    }
  }

  /** Hands a copy of a read receipt (a "displayed" chat marker) to the callbacks; other copies are ignored. */
  private fun deliverMarker(direction: CarbonExtension.Direction, copy: Message) {
    val id = ChatMarkersElements.DisplayedExtension.from(copy)?.id ?: return
    when (direction) {
      CarbonExtension.Direction.received -> copy.from?.asBareJid()?.let { onContactRead(it, id) }
      CarbonExtension.Direction.sent -> copy.to?.asBareJid()?.let { onReadElsewhere(it, id) }
    }
  }

  /**
   * Hands a carbon copy with [body] to the callbacks: a message another device of ours received comes in like any
   * other, one it sent joins the conversation with the recipient.
   */
  fun deliver(direction: CarbonExtension.Direction, copy: Message, body: String, encrypted: Boolean) {
    when (direction) {
      CarbonExtension.Direction.received -> copy.from?.asBareJid()?.let { from ->
        logger.info("Received message for another device from {}", from)
        onReceived(
          from,
          ChatMessage(body, outgoing = false, encrypted = encrypted, id = copy.stanzaId, markable = copy.isMarkable()),
        )
      }

      CarbonExtension.Direction.sent -> copy.to?.asBareJid()?.let { to ->
        logger.info("Message sent to {} from another device", to)
        onSent(to, ChatMessage(body, outgoing = true, encrypted = encrypted, id = copy.stanzaId))
      }
    }
  }

  private companion object {
    val logger = LoggerFactory.getLogger(CarbonSync::class.java)
  }
}
