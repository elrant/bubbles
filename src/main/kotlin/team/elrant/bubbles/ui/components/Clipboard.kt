package team.elrant.bubbles.ui.components

import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private val logger = LoggerFactory.getLogger("team.elrant.bubbles.ui.components.Clipboard")

/**
 * Puts [text] on the system clipboard. Uses AWT directly because Compose's desktop `ClipEntry` is still
 * experimental. If the clipboard is unavailable, the failure is logged.
 */
fun copyToClipboard(text: String) {
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (e: IllegalStateException) {
        logger.warn("Could not copy to the clipboard: {}", e.message)
    }
}
