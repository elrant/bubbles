package team.elrant.bubbles.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.round

/**
 * [content] with a right-click menu: a secondary-button press opens [menu] at the pointer. The menu is a
 * Material [DropdownMenu], so it follows the app theme, unlike Compose's built-in context menu. Other buttons
 * pass through to [content].
 *
 * @param menu the menu items; call `close` from an item's action to dismiss the menu.
 * @param modifier applied to the box around [content].
 * @param content the area that reacts to right-clicks.
 */
@Composable
fun contextMenuBox(
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var position by remember { mutableStateOf<Offset?>(null) }
    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        position = event.changes.first().position
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        },
    ) {
        content()
        // A zero-size anchor at the pointer, so the menu opens where the user clicked.
        Box(Modifier.offset { position?.round() ?: Offset.Zero.round() }) {
            DropdownMenu(expanded = position != null, onDismissRequest = { position = null }) {
                menu { position = null }
            }
        }
    }
}
