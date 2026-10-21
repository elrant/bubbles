package team.elrant.bubbles.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.ui.components.BubblesWindow
import team.elrant.bubbles.ui.components.ConfirmExitDialog
import team.elrant.bubbles.ui.components.createWindow
import team.elrant.bubbles.ui.contacts.ContactListScreen
import team.elrant.bubbles.ui.conversation.ConversationScreen
import team.elrant.bubbles.ui.signin.SignInScreen
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.Window
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val MAIN_WINDOW_SIZE = Dimension(360, 640)
private val MAIN_WINDOW_MIN_SIZE = Dimension(300, 440)
private val CONVERSATION_WINDOW_SIZE = Dimension(680, 520)
private val CONVERSATION_WINDOW_MIN_SIZE = Dimension(480, 360)

/**
 * Opens Messenger-style windows: one main window (sign-in, then the contact list) plus one window
 * per open conversation. The main window's composition owns the conversation windows, so the app
 * lives as long as the main window does. Call on the Swing thread.
 */
fun showBubbles(bubbles: Bubbles) {
  val confirmExit = mutableStateOf(false)
  createWindow(
    title = "Bubbles",
    size = MAIN_WINDOW_SIZE,
    minimumSize = MAIN_WINDOW_MIN_SIZE,
    onCloseRequest = { confirmExit.value = true },
  ) {
    val user = bubbles.user
    if (user == null) SignInScreen(bubbles) else ContactListScreen(bubbles, user)
    if (confirmExit.value) {
      ConfirmExitDialog(onConfirm = { exitApplication(bubbles) }, onDismiss = { confirmExit.value = false })
    }
    ConversationWindows(bubbles)
  }.isVisible = true
}

/**
 * Disconnects and exits from a separate thread. Safe to call from any thread.
 *
 * Under JBR's Wayland toolkit every window is disposed on the Swing thread first: otherwise its
 * shutdown hook tears windows down off the Swing thread while holding the AWT tree lock, and
 * Compose then blocks on the Swing thread, which is waiting for that same lock. Not done on X11,
 * where exiting right after disposing races Skiko's OpenGL teardown and can hang in native code.
 */
fun exitApplication(bubbles: Bubbles) {
  SwingUtilities.invokeLater {
    if (Toolkit.getDefaultToolkit().javaClass.name == "sun.awt.wl.WLToolkit") {
      Window.getWindows().forEach(Window::dispose)
    }
    thread(name = "Bubbles-Exit") {
      bubbles.shutdown()
      exitProcess(0)
    }
  }
}

/** One window per [Bubbles.conversations] entry; closing a window closes its conversation. */
@Composable
private fun ConversationWindows(bubbles: Bubbles) {
  val user = bubbles.user ?: return
  for (jid in bubbles.conversations) {
    key(jid) {
      val contact = bubbles.contact(jid)
      BubblesWindow(
        title = "${contact.displayName} - Conversation",
        size = CONVERSATION_WINDOW_SIZE,
        minimumSize = CONVERSATION_WINDOW_MIN_SIZE,
        onCloseRequest = { bubbles.closeConversation(jid) },
      ) {
        ConversationScreen(bubbles, user, contact)
      }
    }
  }
}
