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
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.model.ChatMessage
import team.elrant.bubbles.model.Contact
import team.elrant.bubbles.ui.components.DisplayPicture
import team.elrant.bubbles.ui.components.StatusDot
import team.elrant.bubbles.xmpp.ConnectedUser
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

/** A new "X says:" run starts when the sender changes or after this much silence. */
private val RUN_GAP = Duration.ofMinutes(5)

@Composable
fun ConversationScreen(bubbles: Bubbles, user: ConnectedUser, contact: Contact) {
  val messages = bubbles.history(contact.jid)
  var draft by remember(contact.jid) { mutableStateOf("") }

  fun send() {
    val text = draft.trim()
    if (text.isEmpty()) return
    bubbles.sendMessage(contact.jid, text)
    draft = ""
  }

  Surface(Modifier.fillMaxSize()) {
    Row {
      Column(Modifier.weight(1f).fillMaxHeight()) {
        ContactHeader(contact)
        HorizontalDivider()
        History(messages, selfName = user.username, contactName = contact.displayName, Modifier.weight(1f))
        Row(
          Modifier.padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            draft, { draft = it },
            Modifier.weight(1f).onPreviewKeyEvent {
              // Enter sends, Shift+Enter inserts a newline.
              if (it.key == Key.Enter && !it.isShiftPressed) {
                if (it.type == KeyEventType.KeyDown) send()
                true
              } else false
            },
            minLines = 3, maxLines = 5,
          )
          Button(onClick = ::send) { Text("Send") }
        }
        StatusBar(messages)
      }
      VerticalDivider()
      Column(Modifier.width(128.dp).fillMaxHeight().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DisplayPicture(contact.displayName, contact.status, 104.dp)
        Spacer(Modifier.weight(1f))
        DisplayPicture(user.username, bubbles.status, 104.dp)
      }
    }
  }
}

@Composable
private fun ContactHeader(contact: Contact) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
    Text(contact.displayName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(verticalAlignment = Alignment.CenterVertically) {
      StatusDot(contact.status, size = 8.dp)
      Spacer(Modifier.width(6.dp))
      Text(
        listOfNotNull(contact.status.label, contact.personalMessage).joinToString(" - "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
    }
    Text("<${contact.jid}>", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun History(messages: List<ChatMessage>, selfName: String, contactName: String, modifier: Modifier) {
  val listState = rememberLazyListState()
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }

  LazyColumn(modifier.fillMaxWidth().padding(horizontal = 12.dp), state = listState) {
    itemsIndexed(messages) { index, message ->
      val previous = messages.getOrNull(index - 1)
      val startsRun = previous == null || previous.outgoing != message.outgoing ||
        Duration.between(previous.time, message.time) > RUN_GAP
      Column {
        if (startsRun) {
          Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
              "${if (message.outgoing) selfName else contactName} says:",
              Modifier.weight(1f),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              message.time.format(TIME),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Text(message.body, Modifier.padding(start = 16.dp, top = 2.dp))
      }
    }
  }
}

@Composable
private fun StatusBar(messages: List<ChatMessage>) {
  val last = messages.lastOrNull { !it.outgoing } ?: return
  Text(
    "Last message received at ${last.time.format(TIME)} on ${last.time.format(DATE)}.",
    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
