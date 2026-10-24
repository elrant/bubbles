package team.elrant.bubbles.ui.encryption

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jxmpp.jid.BareJid
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.model.OmemoIdentity

/** Characters per group when a fingerprint is shown, as other OMEMO clients do. */
private const val FINGERPRINT_BLOCK = 8

/**
 * OMEMO key management, shown in the main window like the settings: this device's fingerprint, and for each contact
 * (and our own other devices) the devices that can read our messages, each of which can be trusted or distrusted.
 * Compare fingerprints with the contact over another channel before trusting.
 *
 * @param bubbles app state to read identities from and to record decisions in.
 * @param onBack called by the Back button.
 * @param modifier applied to the screen.
 */
@Composable
fun encryptionScreen(bubbles: Bubbles, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val own = produceState<OmemoIdentity?>(null, bubbles.user) { value = bubbles.omemoKeys.own() }.value
  var expanded by remember { mutableStateOf<BareJid?>(null) }
  val accounts = listOfNotNull(bubbles.user?.jid) + bubbles.contacts.map { it.jid }
  Surface(modifier.fillMaxSize()) {
    Column {
      topBar(onBack)
      Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        thisDevice(own)
        HorizontalDivider()
        Text("Accounts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        for (jid in accounts) {
          accountEntry(bubbles, jid, own?.deviceId, expanded == jid) { expanded = jid.takeIf { it != expanded } }
        }
      }
    }
  }
}

/** This device's id and fingerprint, or a note that OMEMO isn't running if [own] is `null`. */
@Composable
private fun thisDevice(own: OmemoIdentity?) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("This device", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    if (own == null) {
      Text("OMEMO isn't running. It needs a keyring (Secret Service), such as GNOME Keyring, to protect its keys.")
    } else {
      Text("Device ${own.deviceId}", style = MaterialTheme.typography.labelLarge)
      fingerprintText(own.fingerprint)
    }
  }
}

/** The name of [jid], which toggles its device list (shown when [expanded]) when clicked. */
@Composable
private fun accountEntry(bubbles: Bubbles, jid: BareJid, ownDeviceId: Int?, expanded: Boolean, onToggle: () -> Unit) {
  val name = if (jid == bubbles.user?.jid) "$jid (your other devices)" else bubbles.contact(jid).displayName
  Column {
    Text(
      name,
      Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
      style = MaterialTheme.typography.bodyLarge,
    )
    if (expanded) deviceList(bubbles, jid, ownDeviceId)
  }
}

/** "← Back" and the screen title. */
@Composable
private fun topBar(onBack: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack) { Text("← Back") }
      Text("Encryption keys", style = MaterialTheme.typography.titleMedium)
    }
  }
}

/**
 * The devices of [jid], looked up when first shown and again after each decision. Our own device is left out of
 * our own list, as it has its own section.
 */
@Composable
private fun deviceList(bubbles: Bubbles, jid: BareJid, ownDeviceId: Int?, modifier: Modifier = Modifier) {
  var refresh by remember { mutableIntStateOf(0) }
  val scope = rememberCoroutineScope()
  val identities = produceState<List<OmemoIdentity>?>(null, jid, refresh) {
    value = bubbles.omemoKeys.devicesOf(jid)
  }.value
  Column(modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val shown = identities?.filter { it.jid != bubbles.user?.jid || it.deviceId != ownDeviceId }
    when {
      identities == null -> Text("Looking up devices…", style = MaterialTheme.typography.labelMedium)

      shown.isNullOrEmpty() -> Text("No OMEMO devices found.", style = MaterialTheme.typography.labelMedium)

      else -> for (identity in shown) {
        identityRow(identity, onDecide = { trusted ->
          scope.launch {
            bubbles.omemoKeys.setTrusted(identity, trusted)
            refresh++
          }
        })
      }
    }
  }
}

/** One device: id, trust, fingerprint, and the buttons for the decisions that would change something. */
@Composable
private fun identityRow(identity: OmemoIdentity, onDecide: (trusted: Boolean) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text("Device ${identity.deviceId} - ${identity.trust.label}", style = MaterialTheme.typography.labelLarge)
    fingerprintText(identity.fingerprint)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { onDecide(true) }, enabled = identity.trust != OmemoIdentity.Trust.Trusted) {
        Text("Trust")
      }
      OutlinedButton(onClick = { onDecide(false) }, enabled = identity.trust != OmemoIdentity.Trust.Untrusted) {
        Text("Don't trust")
      }
    }
  }
}

/** [fingerprint] in monospace, in groups of [FINGERPRINT_BLOCK] characters. */
@Composable
private fun fingerprintText(fingerprint: String) {
  Text(fingerprint.chunked(FINGERPRINT_BLOCK).joinToString(" "), fontFamily = FontFamily.Monospace)
}
