package team.elrant.bubbles.ui.contacts

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.model.ContactGroup

/** Something the user is dragging in the contact list. */
sealed interface DragItem {
    /** Text shown next to the pointer while dragging. */
    val label: String

    /**
     * A contact row.
     *
     * @property contact the dragged contact.
     * @property from the group it was dragged out of.
     */
    data class ContactItem(val contact: Contact, val from: ContactGroup) : DragItem {
        override val label: String get() = contact.displayName
    }

    /**
     * A roster group's header.
     *
     * @property group the dragged group.
     */
    data class GroupItem(val group: ContactGroup) : DragItem {
        override val label: String get() = group.name
    }
}

/**
 * Drag-and-drop state of the contact list. Rows report their bounds with [dragRow], so the group under the
 * pointer can be found; lazy rows that scroll away are dropped with [forget]. Positions are in root coordinates.
 */
@Stable
class ContactDragState {
    /** What is being dragged, or `null` if nothing is. */
    var dragged by mutableStateOf<DragItem?>(null)
        private set

    /** The group under the pointer while dragging, or `null` if none. */
    var target by mutableStateOf<ContactGroup?>(null)
        private set

    /** The pointer while dragging. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** Where the drag overlay starts; see [dragGhost]. */
    internal var overlayOrigin = Offset.Zero

    /** Bounds of each visible row, by row key, with the group it belongs to. */
    private val rows = mutableMapOf<Any, Pair<ContactGroup, Rect>>()

    /** Records that the row [key] of [group] is at [bounds]. */
    internal fun place(key: Any, group: ContactGroup, bounds: Rect) {
        rows[key] = group to bounds
    }

    /** Forgets the row [key], once it has left the composition. */
    fun forget(key: Any) {
        rows -= key
    }

    /** Starts dragging [item] from row [key], [at] a position relative to that row. */
    internal fun start(key: Any, item: DragItem, at: Offset) {
        dragged = item
        pointer = (rows[key]?.second?.topLeft ?: Offset.Zero) + at
        target = groupAt(pointer)
    }

    /** Moves the pointer by [delta]. */
    internal fun move(delta: Offset) {
        pointer += delta
        target = groupAt(pointer)
    }

    /** Ends the drag; returns what was dropped on which group, or `null` if not dropped on a group. */
    internal fun end(): Pair<DragItem, ContactGroup>? {
        val dropped = dragged?.let { item -> target?.let { item to it } }
        cancel()
        return dropped
    }

    /** Abandons the drag. */
    internal fun cancel() {
        dragged = null
        target = null
    }

    /** The group of the row containing [point], if any. */
    private fun groupAt(point: Offset): ContactGroup? = rows.values.firstOrNull { it.second.contains(point) }?.first
}

/**
 * Makes this row of [group] a drop target and, if [item] isn't `null`, a drag source for it. Pair it with
 * `DisposableEffect(key) { onDispose { state.forget(key) } }`.
 *
 * @param state the list's drag state.
 * @param key the row's unique key.
 * @param group the group the row belongs to.
 * @param item what dragging this row drags, or `null` if it can't be dragged.
 * @param onDrop called with what was dropped on which group when a drag from this row ends over a group.
 */
fun Modifier.dragRow(
    state: ContactDragState,
    key: Any,
    group: ContactGroup,
    item: DragItem?,
    onDrop: (DragItem, ContactGroup) -> Unit,
): Modifier {
    val positioned = onGloballyPositioned { state.place(key, group, it.boundsInRoot()) }
    if (item == null) return positioned
    return positioned.pointerInput(state, key, item) {
        detectDragGestures(
            onDragStart = { state.start(key, item, it) },
            onDragEnd = { state.end()?.let { (dropped, target) -> onDrop(dropped, target) } },
            onDragCancel = state::cancel,
            onDrag = { change, delta ->
                change.consume()
                state.move(delta)
            },
        )
    }
}

/** Marks where the drag overlay starts; apply to the box that holds both the list and its [dragGhost]. */
fun Modifier.dragOverlay(state: ContactDragState): Modifier =
    onGloballyPositioned { state.overlayOrigin = it.positionInRoot() }

/** The label of the dragged item next to the pointer; nothing while not dragging. */
@Composable
fun dragGhost(state: ContactDragState, modifier: Modifier = Modifier) {
    val item = state.dragged ?: return
    Surface(
        modifier.offset { (state.pointer - state.overlayOrigin + GHOST_OFFSET).round() },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
    ) {
        Text(
            item.label,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Where the ghost sits relative to the pointer, so it doesn't hide the drop target. */
private val GHOST_OFFSET = Offset(12f, 8f)
