package team.elrant.bubbles.xmpp

import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersElements
import org.jivesoftware.smackx.omemo.element.OmemoElement
import org.jivesoftware.smackx.omemo.util.OmemoConstants

/** Whether the sender asks for chat markers (read receipts) for this message. */
internal fun Message.isMarkable(): Boolean = ChatMarkersElements.MarkableExtension.from(this) != null

/** Whether this message carries an OMEMO payload. */
internal fun Message.isOmemo(): Boolean =
  hasExtension(OmemoElement.NAME_ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL)
