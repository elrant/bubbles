package team.elrant.bubbles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.elrant.bubbles.Bubbles

@Composable
fun LoginScreen(bubbles: Bubbles) {
  var username by remember { mutableStateOf("") }
  var serviceName by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  fun doLogin() {
    if (busy) return
    if (username.isEmpty() || serviceName.isEmpty() || password.isEmpty()) {
      error = "All fields are required."
      return
    }
    busy = true
    scope.launch {
      try {
        bubbles.login(username, password, serviceName)
      } catch (e: Exception) {
        bubbles.logger.error("couldn't establish connection", e)
        error = "Could not connect: ${e.message ?: e::class.simpleName}"
      } finally {
        busy = false
      }
    }
  }

  val actions = KeyboardActions(onDone = { doLogin() })
  Surface(Modifier.fillMaxSize()) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(
        username, { username = it }, Modifier.fillMaxWidth(),
        label = { Text("Username") }, singleLine = true, enabled = !busy,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = actions,
      )
      OutlinedTextField(
        serviceName, { serviceName = it }, Modifier.fillMaxWidth(),
        label = { Text("Server") }, singleLine = true, enabled = !busy,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = actions,
      )
      OutlinedTextField(
        password, { password = it }, Modifier.fillMaxWidth(),
        label = { Text("Password") }, singleLine = true, enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = actions,
      )
      Button(onClick = ::doLogin, enabled = !busy) { Text(if (busy) "Connecting…" else "Login") }
    }
  }

  error?.let { message ->
    AlertDialog(
      onDismissRequest = { error = null },
      title = { Text("Bubbles - Login error") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
    )
  }
}
