package team.elrant.bubbles.model

import org.jxmpp.jid.BareJid

/**
 * Presence of the user or a contact.
 *
 * @property label user-facing name.
 */
enum class Status(val label: String) {
  Available("Available"),
  Busy("Busy"),
  Away("Away"),
  Offline("Offline"),
  ;

  companion object {
    /** Statuses the user can pick for themselves. */
    val selectable = listOf(Available, Busy, Away)
  }
}

/**
 * A roster entry with its current presence.
 *
 * @property jid the contact's bare JID.
 * @property name roster nickname, if set.
 * @property groups roster group names; empty if ungrouped.
 * @property status current presence; [Status.Offline] if unavailable.
 * @property personalMessage the contact's presence status text, or `null` if none.
 * @property avatarHash vCard photo hash from the contact's presence (XEP-0153): `""` for no photo,
 *   `null` if not advertised.
 */
data class Contact(
  val jid: BareJid,
  val name: String?,
  val groups: List<String>,
  val status: Status,
  val personalMessage: String?,
  val avatarHash: String? = null,
) {
  /** The nickname, falling back to the JID. */
  val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: jid.toString()

  companion object {
    /** Placeholder for someone who messaged us but is not in the roster. */
    fun stranger(jid: BareJid) = Contact(jid, null, emptyList(), Status.Offline, null)
  }
}
