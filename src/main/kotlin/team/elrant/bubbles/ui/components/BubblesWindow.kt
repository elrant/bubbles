package team.elrant.bubbles.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposePanel
import team.elrant.bubbles.ui.theme.BubblesTheme
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.WindowConstants

/**
 * Creates a (not yet visible) top-level window hosting Compose [content] in a [ComposePanel].
 *
 * Used instead of Compose's `Window` because only a [ComposePanel] can render through Swing
 * graphics, which is what JBR's native Wayland toolkit needs. Each panel is its own composition,
 * so the theme is applied here.
 *
 * @param title window title.
 * @param size initial size, in pixels.
 * @param minimumSize smallest size the user can resize it to.
 * @param onCloseRequest called when the user tries to close the window; the window stays open
 *   unless the caller disposes it.
 * @param content the window's contents.
 */
fun createWindow(
  title: String,
  size: Dimension,
  minimumSize: Dimension,
  onCloseRequest: () -> Unit,
  content: @Composable () -> Unit,
): JFrame = JFrame(title).apply {
  defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
  addWindowListener(object : WindowAdapter() {
    override fun windowClosing(e: WindowEvent) = onCloseRequest()
  })
  contentPane.add(ComposePanel().apply { setContent { BubblesTheme(content) } })
  this.size = size
  this.minimumSize = minimumSize
  setLocationRelativeTo(null)
}

/**
 * A [createWindow] window that is shown while this composable is in the composition.
 *
 * [size] and [minimumSize] only apply when the window is first created; [title], [content] and
 * [onCloseRequest] follow recomposition.
 */
@Composable
fun BubblesWindow(
  title: String,
  size: Dimension,
  minimumSize: Dimension,
  onCloseRequest: () -> Unit,
  content: @Composable () -> Unit,
) {
  val currentContent by rememberUpdatedState(content)
  val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
  val frame = remember { createWindow(title, size, minimumSize, { currentOnCloseRequest() }) { currentContent() } }

  SideEffect { frame.title = title }
  DisposableEffect(frame) {
    frame.isVisible = true
    onDispose { frame.dispose() }
  }
}
