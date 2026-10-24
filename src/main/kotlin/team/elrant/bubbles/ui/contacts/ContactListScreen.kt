package team.elrant.bubbles.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jxmpp.jid.BareJid
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.model.ContactGroup
import team.elrant.bubbles.model.groupContacts
import team.elrant.bubbles.ui.components.contextMenuBox
import team.elrant.bubbles.ui.components.copyToClipboard
import team.elrant.bubbles.ui.components.displayPicture
import team.elrant.bubbles.ui.components.menuItem
import team.elrant.bubbles.ui.components.statusMenuItems
import team.elrant.bubbles.ui.components.statusSelector
import team.elrant.bubbles.ui.components.textPromptDialog
import team.elrant.bubbles.xmpp.ConnectedUser

/**
 * Main window once signed in: own profile header, contact search and the grouped contact list.
 * [onOpenSettings] is offered in the profile header's menus.
 */
@Composable
fun contactListScreen(
    bubbles: Bubbles,
    user: ConnectedUser,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    Surface(modifier.fillMaxSize()) {
        Column {
            selfHeader(bubbles, user, onOpenSettings)
            OutlinedTextField(
                query, { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search contacts…") }, singleLine = true,
            )
            HorizontalDivider()
            contactList(
                bubbles,
                groupContacts(bubbles.contacts, query, bubbles.preferences.settings.groupOrder),
                emptyText = if (query.isBlank()) "No contacts yet" else "No matches",
            )
        }
    }
}

/**
 * Own display picture, name, status menu and inline-editable personal message. Right-clicking it offers the
 * status menu plus "Edit personal message".
 */
@Composable
private fun selfHeader(bubbles: Bubbles, user: ConnectedUser, onOpenSettings: () -> Unit) {
    val editMessage = remember { FocusRequester() }
    contextMenuBox(
        menu = { close ->
            menuItem("Edit personal message", onClose = close, onClick = { editMessage.requestFocus() })
            HorizontalDivider()
            statusMenuItems(
                onSelect = { bubbles.changePresence(status = it) },
                onClose = close,
                onOpenSettings = onOpenSettings,
                onSignOut = bubbles::signOut,
            )
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                displayPicture(user.username, bubbles.status, 64.dp, picture = bubbles.avatar(user.jid))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    statusSelector(
                        onSelect = { bubbles.changePresence(status = it) },
                        onOpenSettings = onOpenSettings,
                        onSignOut = bubbles::signOut,
                    ) {
                        Text(
                            "${user.username} (${bubbles.status.label})",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    personalMessageField(
                        bubbles.personalMessage,
                        { bubbles.changePresence(personalMessage = it) },
                        Modifier.padding(start = 4.dp, top = 2.dp).focusRequester(editMessage),
                    )
                }
            }
        }
    }
}

/**
 * Borderless single-line editor for the personal message. Edits are committed through [onCommit]
 * when the field loses focus (Enter clears focus), and only if the text changed.
 */
@Composable
private fun personalMessageField(current: String, onCommit: (String) -> Unit, modifier: Modifier = Modifier) {
    var draft by remember(current) { mutableStateOf(current) }
    val focus = LocalFocusManager.current
    val style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    BasicTextField(
        draft, { draft = it },
        modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused && draft.trim() != current) onCommit(draft) }
            .onPreviewKeyEvent {
                val enter = it.key == Key.Enter
                if (enter) focus.clearFocus()
                enter
            },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { field ->
            Box {
                if (draft.isEmpty()) {
                    Text("<Share a quick message>", style = style.copy(color = style.color.copy(alpha = 0.6f)))
                }
                field()
            }
        },
    )
}

/**
 * Collapsible [groups] of contacts. A click selects a contact, a double-click opens its conversation, and a
 * right-click opens its menu. Contacts can be dragged into other groups and roster groups dragged to reorder
 * them (see [onDrop]). Shows [emptyText] when there are no groups.
 */
@Composable
private fun contactList(bubbles: Bubbles, groups: List<ContactGroup>, emptyText: String) {
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    var selected by remember { mutableStateOf<BareJid?>(null) }
    var newGroupFor by remember { mutableStateOf<DragItem.ContactItem?>(null) }
    val drag = remember { ContactDragState() }
    val drop = { item: DragItem, target: ContactGroup -> onDrop(bubbles, item, target) }

    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Box(Modifier.fillMaxSize().dragOverlay(drag)) {
        LazyColumn(Modifier.fillMaxSize()) {
            for (group in groups) {
                val name = group.name
                val isCollapsed = collapsed[name] == true
                item(key = groupKey(name)) {
                    groupHeaderEntry(
                        group, isCollapsed, drag, drop,
                        onToggle = { collapsed[name] = !isCollapsed },
                        onCollapseAll = { collapse -> groups.forEach { collapsed[it.name] = collapse } },
                    )
                }
                if (!isCollapsed) {
                    items(group.contacts, key = { contactKey(name, it.jid) }) { contact ->
                        contactEntry(
                            bubbles, contact, group, drag, drop,
                            selected = contact.jid == selected,
                            onSelect = { selected = contact.jid },
                            onNewGroup = { newGroupFor = DragItem.ContactItem(contact, group) },
                        )
                    }
                }
            }
        }
        dragGhost(drag)
    }

    newGroupFor?.let { item ->
        textPromptDialog(
            title = "New group",
            label = "Move ${item.contact.displayName} to the group",
            confirmText = "Create",
            onConfirm = { name ->
                newGroupFor = null
                bubbles.moveContact(item.contact.jid, rosterName(item.from), name)
            },
            onDismiss = { newGroupFor = null },
        )
    }
}

/**
 * A group's header in the list: click to collapse or expand it with [onToggle], drag it to reorder, right-click
 * for "Collapse all" / "Expand all" ([onCollapseAll] with `true` / `false`).
 */
@Composable
private fun groupHeaderEntry(
    group: ContactGroup,
    collapsed: Boolean,
    drag: ContactDragState,
    onDrop: (DragItem, ContactGroup) -> Unit,
    onToggle: () -> Unit,
    onCollapseAll: (Boolean) -> Unit,
) {
    val key = groupKey(group.name)
    DisposableEffect(key) { onDispose { drag.forget(key) } }
    contextMenuBox(
        menu = { close ->
            menuItem("Collapse all", onClose = close, onClick = { onCollapseAll(true) })
            menuItem("Expand all", onClose = close, onClick = { onCollapseAll(false) })
        },
    ) {
        groupHeader(
            group,
            collapsed,
            highlighted = drag.target?.name == group.name && canDrop(drag.dragged, drag.target),
            Modifier
                .dragRow(drag, key, group, DragItem.GroupItem(group).takeIf { canDrag(group) }, onDrop)
                .clickable(onClick = onToggle),
        )
    }
}

/**
 * A contact's row in [group]: click selects it ([onSelect]), double-click opens the conversation, drag moves it
 * to another group, right-click opens its menu ("New group…" calls [onNewGroup]).
 */
@Composable
private fun contactEntry(
    bubbles: Bubbles,
    contact: Contact,
    group: ContactGroup,
    drag: ContactDragState,
    onDrop: (DragItem, ContactGroup) -> Unit,
    selected: Boolean,
    onSelect: () -> Unit,
    onNewGroup: () -> Unit,
) {
    val key = contactKey(group.name, contact.jid)
    DisposableEffect(key) { onDispose { drag.forget(key) } }
    contextMenuBox(
        menu = { close ->
            menuItem("Open conversation", onClose = close, onClick = { bubbles.openConversation(contact.jid) })
            menuItem("Copy address", onClose = close, onClick = { copyToClipboard(contact.jid.toString()) })
            HorizontalDivider()
            menuItem("New group…", onClose = close, onClick = onNewGroup)
        },
    ) {
        contactRow(
            contact,
            picture = bubbles.avatar(contact.jid),
            unread = bubbles.unread[contact.jid] ?: 0,
            selected = selected,
            Modifier
                .dragRow(drag, key, group, DragItem.ContactItem(contact, group), onDrop)
                .combinedClickable(onClick = onSelect, onDoubleClick = { bubbles.openConversation(contact.jid) }),
        )
    }
}

/** Lazy-list and drag key of [group]'s header. */
private fun groupKey(group: String) = "group:$group"

/** Lazy-list and drag key of [contact]'s row in [group]; a contact can be in several groups. */
private fun contactKey(group: String, contact: BareJid) = "contact:$group:$contact"

/** Only roster groups can be reordered; the built-in ones always come last. */
private fun canDrag(group: ContactGroup) = group.kind == ContactGroup.Kind.Roster

/**
 * Whether [item] may be dropped on [target]: contacts onto any other group but "Offline", roster groups onto any
 * other group but themselves.
 */
private fun canDrop(item: DragItem?, target: ContactGroup?): Boolean = when {
    item == null || target == null -> false
    item is DragItem.ContactItem -> target.kind != ContactGroup.Kind.Offline && target.name != item.from.name
    item is DragItem.GroupItem -> target.name != item.group.name
    else -> false
}

/** The roster group name of [group], or `null` for a built-in group. */
private fun rosterName(group: ContactGroup): String? = group.name.takeIf { group.kind == ContactGroup.Kind.Roster }

/**
 * Applies a drop of [item] on [target]: moves a contact on the server, or saves the new group order with the
 * dragged group placed before [target] (at the end when dropped on a built-in group).
 */
private fun onDrop(bubbles: Bubbles, item: DragItem, target: ContactGroup) {
    if (!canDrop(item, target)) return
    when (item) {
        is DragItem.ContactItem -> {
            val from = rosterName(item.from)
            val to = rosterName(target)
            if (from != null || to != null) bubbles.moveContact(item.contact.jid, from, to)
        }

        is DragItem.GroupItem -> {
            val order = rosterGroupOrder(bubbles).filter { it != item.group.name }.toMutableList()
            val index = order.indexOf(target.name).takeIf { it >= 0 } ?: order.size
            order.add(index, item.group.name)
            bubbles.preferences.update { it.copy(groupOrder = order) }
        }
    }
}

/**
 * Every roster group in its current display order, including groups whose members are all offline (and so
 * shown under "Offline"), so their place is kept.
 */
private fun rosterGroupOrder(bubbles: Bubbles): List<String> {
    val all = bubbles.contacts.flatMap { it.groups }.toSet()
    val known = bubbles.preferences.settings.groupOrder.filter { it in all }
    return known + (all - known.toSet()).sortedWith(String.CASE_INSENSITIVE_ORDER)
}

/** A group's heading with its member count; [highlighted] while something can be dropped on it. */
@Composable
private fun groupHeader(group: ContactGroup, collapsed: Boolean, highlighted: Boolean, modifier: Modifier = Modifier) {
    Text(
        "${if (collapsed) "▸" else "▾"} ${group.name} (${group.contacts.size})",
        modifier
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Display picture, name and personal message of one contact; [unread] messages are counted after the name. */
@Composable
private fun contactRow(
    contact: Contact,
    picture: ImageBitmap?,
    unread: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayPicture(contact.displayName, contact.status, 32.dp, picture = picture)
        Spacer(Modifier.width(10.dp))
        Text(
            if (unread > 0) "${contact.displayName} ($unread)" else contact.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (unread > 0) FontWeight.Bold else null,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        contact.personalMessage?.let {
            Text(
                " - $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
