package team.elrant.bubbles.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
 * When [onSignOut] is given, the menu also offers "Sign out".
 */
@Composable
fun StatusSelector(
  onSelect: (Status) -> Unit,
  modifier: Modifier = Modifier,
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
            expanded = false
            onSelect(status)
          },
        )
      }
      if (onSignOut != null) {
        HorizontalDivider()
        DropdownMenuItem(
          text = { Text("Sign out") },
          onClick = {
            expanded = false
            onSignOut()
          },
        )
      }
    }
  }
}
