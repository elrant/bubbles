import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.ui.BubblesTheme
import team.elrant.bubbles.ui.ChatScreen
import team.elrant.bubbles.ui.ConfirmExitDialog
import team.elrant.bubbles.ui.LoginScreen
import team.elrant.bubbles.ui.RosterScreen

fun main() = application {
  val bubbles = remember { Bubbles() }
  var confirmExit by remember { mutableStateOf(false) }

  BubblesTheme {
    if (bubbles.user == null) {
      Window(
        onCloseRequest = { confirmExit = true },
        title = "Bubbles XMPP - Login",
        state = rememberWindowState(size = DpSize(420.dp, 340.dp)),
      ) {
        LoginScreen(bubbles)
        if (confirmExit) ConfirmExitDialog(onConfirm = bubbles::shutdown, onDismiss = { confirmExit = false })
      }
    } else {
      Window(
        onCloseRequest = { confirmExit = true },
        title = "Bubbles - Roster",
        state = rememberWindowState(size = DpSize(320.dp, 520.dp)),
      ) {
        RosterScreen(bubbles)
        if (confirmExit) ConfirmExitDialog(onConfirm = bubbles::shutdown, onDismiss = { confirmExit = false })
      }

      bubbles.activeContact?.let { contact ->
        Window(
          onCloseRequest = bubbles::closeChat,
          title = "Bubbles - ${contact.jid}",
          state = rememberWindowState(size = DpSize(640.dp, 480.dp)),
        ) {
          ChatScreen(bubbles, contact)
        }
      }
    }
  }
}
