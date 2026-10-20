package team.elrant.bubbles.model

import org.jxmpp.jid.BareJid

enum class Status(val label: String) {
  Available("Available"),
  Busy("Busy"),
  Away("Away"),
  Offline("Offline");

  companion object {
    /** Statuses the user can pick for themselves. */
    val selectable = listOf(Available, Busy, Away)
  }
}

data class Contact(
  val jid: BareJid,
  val name: String?,
  val groups: List<String>,
  val status: Status,
  val personalMessage: String?,
) {
  val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: jid.toString()

  companion object {
    /** Placeholder for someone who messaged us but is not in the roster. */
    fun stranger(jid: BareJid) = Contact(jid, null, emptyList(), Status.Offline, null)
  }
}
