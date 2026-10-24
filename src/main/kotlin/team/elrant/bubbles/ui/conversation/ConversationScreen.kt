package team.elrant.bubbles.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.model.ChatMessage
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.ui.components.Receipt
import team.elrant.bubbles.ui.components.contextMenuBox
import team.elrant.bubbles.ui.components.copyToClipboard
import team.elrant.bubbles.ui.components.displayPicture
import team.elrant.bubbles.ui.components.menuItem
import team.elrant.bubbles.ui.components.receiptIcon
import team.elrant.bubbles.ui.components.statusDot
import team.elrant.bubbles.xmpp.ConnectedUser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME_24H = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_12H = DateTimeFormatter.ofPattern("h:mm a")
private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

/** How often the clock shown for the contact is refreshed. */
private const val CLOCK_TICK_MS = 15_000L

private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** [this] formatted with [time], preceded by its date as `dd/mm/yyyy` unless it is today. */
private fun LocalDateTime.stamp(time: DateTimeFormatter): String =
    if (toLocalDate() == LocalDate.now()) format(time) else "${format(DAY_MONTH_YEAR)} ${format(time)}"

/**
 * Conversation window contents: contact header, history, compose box and both display pictures.
 * The send shortcut, message grouping and time format follow [Bubbles.settings].
 */
@Composable
fun conversationScreen(bubbles: Bubbles, user: ConnectedUser, contact: Contact, modifier: Modifier = Modifier) {
    val messages = bubbles.histories[contact.jid].orEmpty()
    val settings = bubbles.preferences.settings
    val time = if (settings.use24HourClock) TIME_24H else TIME_12H
    var draft by remember(contact.jid) { mutableStateOf("") }
    val now by produceState(Instant.now()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = Instant.now()
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        bubbles.sendMessage(contact.jid, text)
        draft = ""
    }

    Surface(modifier.fillMaxSize()) {
        Row {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val localTime = bubbles.clocks.offset(contact.jid)?.let { "${now.atOffset(it).format(time)} (UTC$it)" }
                contactHeader(contact, localTime)
                HorizontalDivider()
                history(
                    messages,
                    selfName = user.username,
                    contactName = contact.displayName,
                    time = time,
                    runGap = Duration.ofMinutes(settings.runGapMinutes.toLong()),
                    modifier = Modifier.weight(1f),
                )
                composeBox(draft, { draft = it }, enterSends = settings.enterSends, onSend = ::send)
                statusBar(messages, time)
            }
            VerticalDivider()
            Column(
                Modifier.width(128.dp).fillMaxHeight().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                displayPicture(contact.displayName, contact.status, 104.dp, picture = bubbles.avatar(contact.jid))
                Spacer(Modifier.weight(1f))
                displayPicture(user.username, bubbles.status, 104.dp, picture = bubbles.avatar(user.jid))
            }
        }
    }
}

/** The contact's name, status, personal message and JID, and [localTime] on their clock if known. */
@Composable
private fun contactHeader(contact: Contact, localTime: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            contact.displayName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            statusDot(contact.status, size = 8.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                listOfNotNull(contact.status.label, contact.personalMessage).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            listOfNotNull("<${contact.jid}>", localTime?.let { "Their time: $it" }).joinToString("  -  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Messages grouped into "X says:" runs, each with its [time]; a run starts when the sender changes or after
 * [runGap] of silence. Scrolls to the newest message as they arrive. Right-clicking a message offers to copy it.
 */
@Composable
private fun history(
    messages: List<ChatMessage>,
    selfName: String,
    contactName: String,
    time: DateTimeFormatter,
    runGap: Duration,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 12.dp), state = listState) {
        itemsIndexed(messages) { index, message ->
            val previous = messages.getOrNull(index - 1)
            val startsRun = previous == null ||
                previous.outgoing != message.outgoing ||
                Duration.between(previous.time, message.time) > runGap
            val sender = if (message.outgoing) selfName else contactName
            Column {
                if (startsRun) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            "$sender says:",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            message.time.stamp(time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                contextMenuBox(
                    menu = { close ->
                        menuItem("Copy message", onClose = close, onClick = { copyToClipboard(message.body) })
                        menuItem(
                            "Copy with sender and time",
                            onClose = close,
                            onClick = { copyToClipboard("[${message.time.stamp(time)}] $sender: ${message.body}") },
                        )
                    },
                ) {
                    messageText(message)
                }
            }
        }
    }
}

/**
 * [message]'s text. Encryption is meant to be invisible, so a banner appears under it only when something is off:
 * it was sent or received as plain text, or it could not be sent.
 * Outgoing messages also carry a [receiptIcon] showing whether they are sending, sent, read or failed.
 */
@Composable
private fun messageText(message: ChatMessage) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(message.body, Modifier.weight(1f))
            if (message.outgoing) receiptIcon(message.receipt(), Modifier.padding(start = 6.dp, top = 4.dp))
        }
        val banner = when {
            message.failed -> "Not sent"
            message.pending || message.encrypted -> null
            else -> "Not encrypted"
        }
        if (banner != null) banner(banner, isError = message.failed)
    }
}

/** How far this outgoing message has got. */
private fun ChatMessage.receipt(): Receipt = when {
    failed -> Receipt.Failed
    pending -> Receipt.Sending
    read -> Receipt.Read
    else -> Receipt.Sent
}

/** A small label with [text] under a message; red if [isError]. */
@Composable
private fun banner(text: String, isError: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.padding(top = 2.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = if (isError) colors.errorContainer else colors.surfaceVariant,
        contentColor = if (isError) colors.onErrorContainer else colors.onSurfaceVariant,
    ) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Draft editor and Send button. With [enterSends], Enter calls [onSend] and Shift+Enter inserts a newline;
 * otherwise Ctrl+Enter calls [onSend] and Enter inserts a newline.
 */
@Composable
private fun composeBox(draft: String, onDraftChange: (String) -> Unit, enterSends: Boolean, onSend: () -> Unit) {
    Row(
        Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            draft, onDraftChange,
            Modifier.weight(1f).onPreviewKeyEvent {
                val send = it.key == Key.Enter && if (enterSends) !it.isShiftPressed else it.isCtrlPressed
                if (send && it.type == KeyEventType.KeyDown) onSend()
                send
            },
            minLines = 3, maxLines = 5,
        )
        Button(onClick = onSend) { Text("Send") }
    }
}

/** When the last incoming message arrived, formatted with [time]; hidden until there is one. */
@Composable
private fun statusBar(messages: List<ChatMessage>, time: DateTimeFormatter) {
    val last = messages.lastOrNull { !it.outgoing } ?: return
    Text(
        "Last message received at ${last.time.format(time)} on ${last.time.format(DATE)}.",
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
