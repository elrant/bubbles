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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jxmpp.jid.BareJid
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.model.ContactGroup
import team.elrant.bubbles.model.groupContacts
import team.elrant.bubbles.ui.components.DisplayPicture
import team.elrant.bubbles.ui.components.StatusSelector
import team.elrant.bubbles.xmpp.ConnectedUser

/** Main window once signed in: own profile header, contact search and the grouped contact list. */
@Composable
fun ContactListScreen(bubbles: Bubbles, user: ConnectedUser, modifier: Modifier = Modifier) {
  var query by remember { mutableStateOf("") }
  Surface(modifier.fillMaxSize()) {
    Column {
      SelfHeader(bubbles, user)
      OutlinedTextField(
        query, { query = it },
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("Search contacts…") }, singleLine = true,
      )
      HorizontalDivider()
      ContactList(
        groupContacts(bubbles.contacts, query),
        emptyText = if (query.isBlank()) "No contacts yet" else "No matches",
        avatar = bubbles::avatar,
        onOpen = bubbles::openConversation,
      )
    }
  }
}

/** Own display picture, name, status menu and inline-editable personal message. */
@Composable
private fun SelfHeader(bubbles: Bubbles, user: ConnectedUser) {
  Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      DisplayPicture(user.username, bubbles.status, 64.dp, picture = bubbles.avatar(user.jid))
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        StatusSelector(onSelect = bubbles::changeStatus, onSignOut = bubbles::signOut) {
          Text(
            "${user.username} (${bubbles.status.label})",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
          )
        }
        PersonalMessageField(
          bubbles.personalMessage,
          bubbles::changePersonalMessage,
          Modifier.padding(start = 4.dp, top = 2.dp),
        )
      }
    }
  }
}

/**
 * Borderless single-line editor for the personal message. Edits are committed through [onCommit]
 * when the field loses focus (Enter clears focus), and only if the text changed.
 */
@Composable
private fun PersonalMessageField(current: String, onCommit: (String) -> Unit, modifier: Modifier = Modifier) {
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
        if (draft.isEmpty()) Text("<Share a quick message>", style = style.copy(color = style.color.copy(alpha = 0.6f)))
        field()
      }
    },
  )
}

/**
 * Collapsible [groups] of contacts, with display pictures from [avatar]. A click selects a contact,
 * a double-click calls [onOpen]. Shows [emptyText] when there are no groups.
 */
@Composable
private fun ContactList(
  groups: List<ContactGroup>,
  emptyText: String,
  avatar: (BareJid) -> ImageBitmap?,
  onOpen: (BareJid) -> Unit,
) {
  val collapsed = remember { mutableStateMapOf<String, Boolean>() }
  var selected by remember { mutableStateOf<BareJid?>(null) }

  if (groups.isEmpty()) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    return
  }

  LazyColumn(Modifier.fillMaxSize()) {
    for ((name, members) in groups) {
      val isCollapsed = collapsed[name] == true
      item(key = "group:$name") {
        Text(
          "${if (isCollapsed) "▸" else "▾"} $name (${members.size})",
          Modifier
            .fillMaxWidth()
            .clickable { collapsed[name] = !isCollapsed }
            .padding(horizontal = 12.dp, vertical = 6.dp),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (!isCollapsed) {
        items(members, key = { "contact:$name:${it.jid}" }) { contact ->
          ContactRow(
            contact,
            picture = avatar(contact.jid),
            selected = contact.jid == selected,
            onSelect = { selected = contact.jid },
            onOpen = { onOpen(contact.jid) },
          )
        }
      }
    }
  }
}

/** Display picture, name and personal message of one contact. */
@Composable
private fun ContactRow(
  contact: Contact,
  picture: ImageBitmap?,
  selected: Boolean,
  onSelect: () -> Unit,
  onOpen: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
      .combinedClickable(onClick = onSelect, onDoubleClick = onOpen)
      .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    DisplayPicture(contact.displayName, contact.status, 32.dp, picture = picture)
    Spacer(Modifier.width(10.dp))
    Text(
      contact.displayName,
      style = MaterialTheme.typography.bodyMedium,
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
