package team.elrant.bubbles.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.model.Accent
import team.elrant.bubbles.model.Flavor
import team.elrant.bubbles.model.Settings
import team.elrant.bubbles.ui.theme.accentColor

/**
 * The settings, shown in the main window in place of the sign-in screen or contact list. Every change is
 * applied and saved immediately.
 *
 * @param bubbles app state whose settings are edited.
 * @param onBack called by the Back button.
 * @param modifier applied to the screen.
 */
@Composable
fun SettingsScreen(bubbles: Bubbles, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val settings = bubbles.preferences.settings
  val update = bubbles.preferences::update
  Surface(modifier.fillMaxSize()) {
    Column {
      TopBar(onBack)
      Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AppearanceSection(settings, update)
        HorizontalDivider()
        AccountSection(
          savedAddress = bubbles.preferences.savedAddress,
          signedInAs = bubbles.user?.address,
          onForget = bubbles.preferences::forgetAddress,
        )
        HorizontalDivider()
        NotificationSection(settings, update)
        HorizontalDivider()
        ChatSection(settings, update)
      }
    }
  }
}

/** "← Back" and the screen title. */
@Composable
private fun TopBar(onBack: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack) { Text("← Back") }
      Text("Settings", style = MaterialTheme.typography.titleMedium)
    }
  }
}

/** A settings section: [title] above its [content]. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      title,
      Modifier.padding(top = 8.dp),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    content()
  }
}

/** Flavor choice and accent swatches. */
@Composable
private fun AppearanceSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
  Section("Appearance") {
    Text("Flavor", style = MaterialTheme.typography.labelLarge)
    for (flavor in Flavor.entries) {
      Choice(flavor.label, selected = settings.flavor == flavor) { update { it.copy(flavor = flavor) } }
    }
    Text("Accent: ${settings.accent.label}", style = MaterialTheme.typography.labelLarge)
    AccentSwatches(settings.flavor, settings.accent) { accent -> update { it.copy(accent = accent) } }
  }
}

/** One clickable circle per accent in [flavor]'s colours; [selected] is outlined. */
@Composable
private fun AccentSwatches(flavor: Flavor, selected: Accent, onSelect: (Accent) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for (accent in Accent.entries) {
      val outline = if (accent == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
      Box(
        Modifier
          .size(28.dp)
          .border(BorderStroke(2.dp, outline), CircleShape)
          .padding(3.dp)
          .clip(CircleShape)
          .background(accentColor(flavor, accent))
          .selectable(accent == selected, role = Role.RadioButton) { onSelect(accent) },
      )
    }
  }
}

/** The remembered address with a button to forget it, and the signed-in account if any. */
@Composable
private fun AccountSection(savedAddress: String?, signedInAs: String?, onForget: () -> Unit) {
  Section("Account") {
    signedInAs?.let { Text("Signed in as $it") }
    Text("Remembered address: ${savedAddress ?: "none"}")
    OutlinedButton(onClick = onForget, enabled = savedAddress != null) { Text("Forget remembered address") }
  }
}

/** What happens when a message arrives. */
@Composable
private fun NotificationSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
  Section("Notifications") {
    Toggle("Open the conversation when a message arrives", settings.openOnMessage) { on ->
      update { it.copy(openOnMessage = on) }
    }
    Toggle("Bring the conversation to the front", settings.raiseOnMessage) { on ->
      update { it.copy(raiseOnMessage = on) }
    }
    Toggle("Flash the window in the taskbar", settings.requestAttention) { on ->
      update { it.copy(requestAttention = on) }
    }
  }
}

/** Sending shortcut, message grouping and time format. */
@Composable
private fun ChatSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
  Section("Chat") {
    Choice("Enter sends, Shift+Enter starts a new line", selected = settings.enterSends) {
      update { it.copy(enterSends = true) }
    }
    Choice("Ctrl+Enter sends, Enter starts a new line", selected = !settings.enterSends) {
      update { it.copy(enterSends = false) }
    }
    Toggle("24-hour clock", settings.use24HourClock) { on -> update { it.copy(use24HourClock = on) } }
    RunGapField(settings.runGapMinutes) { minutes -> update { it.copy(runGapMinutes = minutes) } }
  }
}

/**
 * Number field for [Settings.runGapMinutes]. The value is committed through [onCommit] when the field loses
 * focus, clamped to [Settings.RUN_GAP_RANGE]; anything that isn't a number reverts to [current].
 */
@Composable
private fun RunGapField(current: Int, onCommit: (Int) -> Unit) {
  var draft by remember(current) { mutableStateOf(current.toString()) }
  OutlinedTextField(
    draft, { text -> draft = text.filter(Char::isDigit) },
    Modifier.fillMaxWidth().onFocusChanged { focus ->
      if (!focus.isFocused) {
        val minutes = draft.toIntOrNull()?.coerceIn(Settings.RUN_GAP_RANGE) ?: current
        draft = minutes.toString()
        if (minutes != current) onCommit(minutes)
      }
    },
    label = { Text("Start a new \"says:\" block after (minutes)") },
    singleLine = true,
  )
}

/** A radio button row; the whole row is clickable. */
@Composable
private fun Choice(text: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected, onClick = null)
    Spacer(Modifier.width(8.dp))
    Text(text)
  }
}

/** A labelled switch; the whole row is clickable. */
@Composable
private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text, Modifier.weight(1f))
    Switch(checked, onCheckedChange = null)
  }
}
