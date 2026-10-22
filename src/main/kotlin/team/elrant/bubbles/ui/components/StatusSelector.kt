package team.elrant.bubbles.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import team.elrant.bubbles.model.Status

/**
 * Clickable [content] that opens the status menu, like Messenger's "Name (Available) ▾".
 * When [onOpenSettings] or [onSignOut] is given, the menu also offers "Settings…" or "Sign out".
 */
@Composable
fun StatusSelector(
  onSelect: (Status) -> Unit,
  modifier: Modifier = Modifier,
  onOpenSettings: (() -> Unit)? = null,
  onSignOut: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier) {
    Row(
      Modifier
        .clip(RoundedCornerShape(4.dp))
        .clickable { expanded = true }
        .padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      content()
      Text(" ▾")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      StatusMenuItems(
        onSelect = onSelect,
        onClose = { expanded = false },
        onOpenSettings = onOpenSettings,
        onSignOut = onSignOut,
      )
    }
  }
}

/**
 * The status menu's items, for use inside a [DropdownMenu] or [ContextMenuBox]: one per selectable status,
 * then "Settings…" and "Sign out" when given. Every item calls [onClose] before its action.
 */
@Composable
internal fun ColumnScope.StatusMenuItems(
  onSelect: (Status) -> Unit,
  onClose: () -> Unit,
  onOpenSettings: (() -> Unit)? = null,
  onSignOut: (() -> Unit)? = null,
) {
  for (status in Status.selectable) {
    DropdownMenuItem(
      text = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          StatusDot(status)
          Spacer(Modifier.width(8.dp))
          Text(status.label)
        }
      },
      onClick = {
        onClose()
        onSelect(status)
      },
    )
  }
  if (onOpenSettings != null || onSignOut != null) HorizontalDivider()
  if (onOpenSettings != null) MenuItem("Settings…", onClose, onClick = onOpenSettings)
  if (onSignOut != null) MenuItem("Sign out", onClose, onClick = onSignOut)
}

/** A plain-text [DropdownMenuItem] that calls [onClose], then [onClick]. */
@Composable
fun MenuItem(text: String, onClose: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
  DropdownMenuItem(
    text = { Text(text) },
    onClick = {
      onClose()
      onClick()
    },
    modifier = modifier,
  )
}
