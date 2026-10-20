package team.elrant.bubbles.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.ui.components.ConfirmExitDialog
import team.elrant.bubbles.ui.contacts.ContactListScreen
import team.elrant.bubbles.ui.conversation.ConversationScreen
import team.elrant.bubbles.ui.signin.SignInScreen
import team.elrant.bubbles.ui.theme.BubblesTheme
import java.awt.Dimension

/**
 * Messenger-style windows: one main window (sign-in, then the contact list) plus one window per
 * open conversation.
 */
@Composable
fun BubblesApp(bubbles: Bubbles) {
  var confirmExit by remember { mutableStateOf(false) }

  BubblesTheme {
    Window(
      onCloseRequest = { confirmExit = true },
      title = "Bubbles",
      state = rememberWindowState(size = DpSize(360.dp, 640.dp)),
    ) {
      LaunchedEffect(Unit) { window.minimumSize = Dimension(300, 440) }
      when (val user = bubbles.user) {
        null -> SignInScreen(bubbles)
        else -> ContactListScreen(bubbles, user)
      }
      if (confirmExit) ConfirmExitDialog(onConfirm = bubbles::shutdown, onDismiss = { confirmExit = false })
    }

    val user = bubbles.user ?: return@BubblesTheme
    for (jid in bubbles.conversations) key(jid) {
      val contact = bubbles.contact(jid)
      Window(
        onCloseRequest = { bubbles.closeConversation(jid) },
        title = "${contact.displayName} - Conversation",
        state = rememberWindowState(size = DpSize(680.dp, 520.dp)),
      ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(480, 360) }
        ConversationScreen(bubbles, user, contact)
      }
    }
  }
}
