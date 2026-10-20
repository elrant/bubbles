package team.elrant.bubbles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.RosterContact

@Composable
fun ChatScreen(bubbles: Bubbles, contact: RosterContact) {
  val messages = bubbles.history(contact.jid)
  val listState = rememberLazyListState()
  var draft by remember(contact) { mutableStateOf("") }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
  }

  fun send() {
    val text = draft.trim()
    if (text.isEmpty()) return
    bubbles.sendMessage(text)
    draft = ""
  }

  Surface(Modifier.fillMaxSize()) {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
        items(messages) { Text("[${it.sender}]: ${it.body}") }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          draft, { draft = it }, Modifier.weight(1f), singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { send() }),
        )
        Button(onClick = ::send) { Text("Send") }
      }
    }
  }
}
