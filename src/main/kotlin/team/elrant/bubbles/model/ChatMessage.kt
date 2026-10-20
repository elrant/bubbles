package team.elrant.bubbles.model

import java.time.LocalDateTime

/**
 * One message in a conversation's history.
 *
 * @param outgoing `true` if we sent it, `false` if the contact did.
 * @param time local time it was sent or received.
 */
data class ChatMessage(
  val body: String,
  val outgoing: Boolean,
  val time: LocalDateTime = LocalDateTime.now(),
)
