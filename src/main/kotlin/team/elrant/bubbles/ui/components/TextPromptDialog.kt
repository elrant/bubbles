package team.elrant.bubbles.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Asks for one line of text. OK (or Enter) calls [onConfirm] with the trimmed text, and is disabled while it is
 * blank.
 *
 * @param title dialog title.
 * @param label label of the text field.
 * @param confirmText text of the OK button.
 * @param onConfirm called with the entered text.
 * @param onDismiss called on Cancel or when the dialog is dismissed.
 */
@Composable
fun TextPromptDialog(
  title: String,
  label: String,
  confirmText: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf("") }
  val valid = text.isNotBlank()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        text, { text = it },
        Modifier.onPreviewKeyEvent {
          val enter = it.key == Key.Enter
          if (enter && valid && it.type == KeyEventType.KeyDown) onConfirm(text.trim())
          enter
        },
        label = { Text(label) }, singleLine = true,
      )
    },
    confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = valid) { Text(confirmText) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
