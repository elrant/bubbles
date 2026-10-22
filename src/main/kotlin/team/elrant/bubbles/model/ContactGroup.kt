package team.elrant.bubbles.model

/**
 * A collapsible section of the contact list.
 *
 * @property name heading, a roster group name or one of the built-in groups.
 * @property contacts members, sorted by name.
 * @property kind whether this is a roster group or a built-in one.
 */
data class ContactGroup(val name: String, val contacts: List<Contact>, val kind: Kind = Kind.Roster) {
  /** What a [ContactGroup] stands for. */
  enum class Kind {
    /** A group from the roster, stored on the server. */
    Roster,

    /** Online contacts that are in no roster group. */
    Other,

    /** All offline contacts, whatever their roster groups. */
    Offline,
  }
}

private const val OTHER_CONTACTS = "Other contacts"
private const val OFFLINE = "Offline"

/**
 * Messenger-style grouping: online contacts under their roster groups (ungrouped ones under
 * "Other contacts", always last), followed by a single "Offline" group.
 *
 * A contact in several roster groups appears in each. Roster groups follow [order], then any others by name;
 * members are sorted by name. Name comparisons ignore case; empty groups are left out.
 *
 * @param contacts roster to group.
 * @param query keeps only contacts whose display name or JID contains it, ignoring case; blank keeps all.
 * @param order preferred order of roster groups.
 */
fun groupContacts(contacts: List<Contact>, query: String, order: List<String> = emptyList()): List<ContactGroup> {
  val matches = contacts.filter {
    query.isBlank() ||
      it.displayName.contains(query, ignoreCase = true) ||
      it.jid.toString().contains(query, ignoreCase = true)
  }
  val byName = compareBy(String.CASE_INSENSITIVE_ORDER, Contact::displayName)
  val (online, offline) = matches.partition { it.status != Status.Offline }
  val (grouped, ungrouped) = online.partition { it.groups.isNotEmpty() }

  val rank = order.withIndex().associate { (index, name) -> name to index }
  val groups = grouped
    .flatMap { contact -> contact.groups.map { it to contact } }
    .groupBy({ it.first }, { it.second })
    .toSortedMap(compareBy<String> { rank[it] ?: Int.MAX_VALUE }.thenBy(String.CASE_INSENSITIVE_ORDER) { it })
    .map { (name, members) -> ContactGroup(name, members.sortedWith(byName)) }

  return groups +
    listOfNotNull(
      ungrouped.takeIf { it.isNotEmpty() }
        ?.let { ContactGroup(OTHER_CONTACTS, it.sortedWith(byName), ContactGroup.Kind.Other) },
      offline.takeIf { it.isNotEmpty() }
        ?.let { ContactGroup(OFFLINE, it.sortedWith(byName), ContactGroup.Kind.Offline) },
    )
}
