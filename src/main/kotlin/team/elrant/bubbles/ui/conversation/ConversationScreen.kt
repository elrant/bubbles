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
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.model.ChatMessage
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.ui.components.contextMenuBox
import team.elrant.bubbles.ui.components.copyToClipboard
import team.elrant.bubbles.ui.components.displayPicture
import team.elrant.bubbles.ui.components.menuItem
import team.elrant.bubbles.ui.components.statusDot
import team.elrant.bubbles.xmpp.ConnectedUser
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME_24H = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_12H = DateTimeFormatter.ofPattern("h:mm a")
private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

/**
 * Conversation window contents: contact header, history, compose box and both display pictures.
 * The send shortcut, message grouping and time format follow [Bubbles.settings].
 */
@Composable
fun conversationScreen(bubbles: Bubbles, user: ConnectedUser, contact: Contact, modifier: Modifier = Modifier) {
  val messages = bubbles.history(contact.jid)
  val settings = bubbles.preferences.settings
  val time = if (settings.use24HourClock) TIME_24H else TIME_12H
  var draft by remember(contact.jid) { mutableStateOf("") }

  fun send() {
    val text = draft.trim()
    if (text.isEmpty()) return
    bubbles.sendMessage(contact.jid, text)
    draft = ""
  }

  Surface(modifier.fillMaxSize()) {
    Row {
      Column(Modifier.weight(1f).fillMaxHeight()) {
        contactHeader(contact)
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

/** The contact's name, status, personal message and JID. */
@Composable
private fun contactHeader(contact: Contact) {
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
      "<${contact.jid}>",
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
              message.time.format(time),
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
              onClick = { copyToClipboard("[${message.time.format(time)}] $sender: ${message.body}") },
            )
          },
        ) {
          messageText(message)
        }
      }
    }
  }
}

/** [message]'s text, with a note underneath if it was sent encrypted or could not be sent. */
@Composable
private fun messageText(message: ChatMessage) {
  Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp)) {
    Text(message.body)
    val note = when {
      message.failed -> "Not sent"
      message.encrypted -> "Encrypted with OMEMO"
      else -> return@Column
    }
    Text(
      note,
      style = MaterialTheme.typography.labelSmall,
      color = if (message.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
