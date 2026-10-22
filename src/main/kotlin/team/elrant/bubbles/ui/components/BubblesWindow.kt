package team.elrant.bubbles.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposePanel
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.Settings
import team.elrant.bubbles.ui.theme.BubblesTheme
import java.awt.Dimension
import java.awt.Taskbar
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.WindowConstants

private val logger = LoggerFactory.getLogger("team.elrant.bubbles.ui.components.BubblesWindow")

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
 * @param settings current settings, read during composition so theme changes apply live.
 * @param onCloseRequest called when the user tries to close the window; the window stays open
 *   unless the caller disposes it.
 * @param content the window's contents.
 */
fun createWindow(
  title: String,
  size: Dimension,
  minimumSize: Dimension,
  settings: () -> Settings,
  onCloseRequest: () -> Unit,
  content: @Composable () -> Unit,
): JFrame = JFrame(title).apply {
  defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
  addWindowListener(object : WindowAdapter() {
    override fun windowClosing(e: WindowEvent) = onCloseRequest()
  })
  contentPane.add(
    ComposePanel().apply {
      setContent {
        val current = settings()
        BubblesTheme(current.flavor, current.accent, content)
      }
    },
  )
  this.size = size
  this.minimumSize = minimumSize
  setLocationRelativeTo(null)
}

/**
 * A [createWindow] window that is shown while this composable is in the composition.
 *
 * [size] and [minimumSize] only apply when the window is first created; [title], [settings],
 * [content] and [onCloseRequest] follow recomposition.
 *
 * @param title window title.
 * @param size initial size, in pixels.
 * @param minimumSize smallest size the user can resize it to.
 * @param settings current settings, for the theme and [attention].
 * @param onCloseRequest called when the user tries to close the window.
 * @param attention whenever this changes after the window is created, the window is raised and/or
 *   flagged to the desktop, as [Settings.raiseOnMessage] and [Settings.requestAttention] say.
 * @param content the window's contents.
 */
@Composable
fun BubblesWindow(
  title: String,
  size: Dimension,
  minimumSize: Dimension,
  settings: Settings,
  onCloseRequest: () -> Unit,
  attention: Int = 0,
  content: @Composable () -> Unit,
) {
  val currentContent by rememberUpdatedState(content)
  val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
  val currentSettings by rememberUpdatedState(settings)
  val frame = remember {
    createWindow(title, size, minimumSize, { currentSettings }, { currentOnCloseRequest() }) { currentContent() }
  }
  val initialAttention = remember { attention }

  SideEffect { frame.title = title }
  DisposableEffect(frame) {
    frame.isVisible = true
    onDispose { frame.dispose() }
  }
  LaunchedEffect(attention) {
    if (attention == initialAttention) return@LaunchedEffect
    if (settings.raiseOnMessage) frame.toFront()
    if (settings.requestAttention) requestUserAttention(frame)
  }
}

/**
 * Asks the desktop to flag [window], typically by flashing its taskbar entry. Best-effort: does nothing where
 * the toolkit doesn't support it.
 */
private fun requestUserAttention(window: Window) {
  val supported = Taskbar.isTaskbarSupported() &&
    Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)
  if (supported) {
    Taskbar.getTaskbar().requestWindowUserAttention(window)
  } else {
    logger.debug("Requesting window attention is not supported here")
  }
}
