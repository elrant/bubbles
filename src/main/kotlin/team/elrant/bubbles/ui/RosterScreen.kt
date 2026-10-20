package team.elrant.bubbles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import team.elrant.bubbles.Bubbles

@Composable
fun RosterScreen(bubbles: Bubbles) {
  Surface(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(bubbles.contacts, key = { it.jid.toString() }) { contact ->
        Button(onClick = { bubbles.openChat(contact) }, modifier = Modifier.fillMaxWidth()) {
          Text(contact.label)
        }
      }
    }
  }
}
