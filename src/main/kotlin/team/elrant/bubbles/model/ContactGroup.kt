package team.elrant.bubbles.model

/**
 * A collapsible section of the contact list.
 *
 * @property name heading, a roster group name or one of the built-in groups.
 * @property contacts members, sorted by name.
 */
data class ContactGroup(val name: String, val contacts: List<Contact>)

private const val OTHER_CONTACTS = "Other contacts"
private const val OFFLINE = "Offline"

/**
 * Messenger-style grouping: online contacts under their roster groups (ungrouped ones under
 * "Other contacts", always last), followed by a single "Offline" group.
 *
 * A contact in several roster groups appears in each. Groups and their members are sorted by name,
 * ignoring case; empty groups are left out.
 *
 * @param contacts roster to group.
 * @param query keeps only contacts whose display name or JID contains it, ignoring case; blank keeps all.
 */
fun groupContacts(contacts: List<Contact>, query: String): List<ContactGroup> {
  val matches = contacts.filter {
    query.isBlank() ||
      it.displayName.contains(query, ignoreCase = true) ||
      it.jid.toString().contains(query, ignoreCase = true)
  }
  val byName = compareBy(String.CASE_INSENSITIVE_ORDER, Contact::displayName)
  val (online, offline) = matches.partition { it.status != Status.Offline }

  val groups = online
    .flatMap { contact -> contact.groups.ifEmpty { listOf(OTHER_CONTACTS) }.map { it to contact } }
    .groupBy({ it.first }, { it.second })
    .toSortedMap(compareBy<String> { it == OTHER_CONTACTS }.thenBy(String.CASE_INSENSITIVE_ORDER) { it })
    .map { (name, members) -> ContactGroup(name, members.sortedWith(byName)) }

  return if (offline.isEmpty()) groups else groups + ContactGroup(OFFLINE, offline.sortedWith(byName))
}
