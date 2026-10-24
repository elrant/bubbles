package team.elrant.bubbles.model

import java.time.LocalDateTime

/**
 * One message in a conversation's history.
 *
 * @property body message text.
 * @property outgoing `true` if we sent it, `false` if the contact did.
 * @property time local time it was sent or received.
 * @property encrypted `true` if it travelled OMEMO-encrypted.
 * @property failed `true` if it could not be sent.
 * @property pending `true` while an outgoing message is still being sent, so its outcome is not yet known.
 * @property read `true` once the contact has told us they saw this message (outgoing messages only).
 * @property markable `true` if the sender asked for chat markers (read receipts) for it (incoming messages only);
 *   not saved.
 * @property id the message's XMPP stanza id if known, so the same message arriving twice (live, as a carbon copy and
 *   from the server archive) is only shown once; `null` for messages we just sent.
 */
data class ChatMessage(
    val body: String,
    val outgoing: Boolean,
    val time: LocalDateTime = LocalDateTime.now(),
    val encrypted: Boolean = false,
    val failed: Boolean = false,
    val pending: Boolean = false,
    val read: Boolean = false,
    val markable: Boolean = false,
    val id: String? = null,
)
