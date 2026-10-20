package team.elrant.bubbles.model

import java.time.LocalDateTime

data class ChatMessage(
  val body: String,
  val outgoing: Boolean,
  val time: LocalDateTime = LocalDateTime.now(),
)
