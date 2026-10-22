package team.elrant.bubbles.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Yes/No prompt shown before quitting from the main window. */
@Composable
fun confirmExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Bubbles") },
    text = { Text("Are you sure you want to close this window?") },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Yes") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("No") } },
  )
}
