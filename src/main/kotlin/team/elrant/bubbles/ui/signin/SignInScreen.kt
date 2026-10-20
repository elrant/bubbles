package team.elrant.bubbles.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import team.elrant.bubbles.Bubbles
import team.elrant.bubbles.model.Status
import team.elrant.bubbles.ui.components.DisplayPicture
import team.elrant.bubbles.ui.components.StatusDot
import team.elrant.bubbles.ui.components.StatusSelector

/**
 * Sign-in form, prefilled with the remembered address. While signing in it shows progress with a
 * Cancel button; errors are shown in a dialog. On success [Bubbles.user] is set, which swaps this
 * screen for the contact list.
 */
@Composable
fun SignInScreen(bubbles: Bubbles) {
  var address by remember { mutableStateOf(bubbles.savedAddress.orEmpty()) }
  var password by remember { mutableStateOf("") }
  var status by remember { mutableStateOf(Status.Available) }
  var rememberMe by remember { mutableStateOf(bubbles.savedAddress != null) }
  var busy by remember { mutableStateOf(false) }
  var job by remember { mutableStateOf<Job?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  fun signIn() {
    if (busy) return
    if (address.isBlank() || password.isEmpty()) {
      error = "Please enter your address and password."
      return
    }
    busy = true
    job = scope.launch {
      try {
        bubbles.signIn(address.trim(), password, status, rememberMe)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        error = "Could not sign in: ${e.message ?: e::class.simpleName}"
      } finally {
        busy = false
      }
    }
  }

  Surface(Modifier.fillMaxSize()) {
    Column(
      Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("Bubbles", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(20.dp))
      DisplayPicture(address.ifBlank { "?" }, status, 112.dp)
      Spacer(Modifier.height(24.dp))

      if (busy) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Signing in…")
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { job?.cancel() }) { Text("Cancel") }
      } else {
        SignInForm(
          address = address, onAddress = { address = it },
          password = password, onPassword = { password = it },
          status = status, onStatus = { status = it },
          rememberMe = rememberMe, onRememberMe = { rememberMe = it },
          onSubmit = ::signIn,
        )
      }
    }
  }

  error?.let { message ->
    AlertDialog(
      onDismissRequest = { error = null },
      title = { Text("Bubbles - Sign-in error") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
    )
  }
}

/** Stateless address/password/status/"Remember me" fields; Enter in either text field submits. */
@Composable
private fun SignInForm(
  address: String, onAddress: (String) -> Unit,
  password: String, onPassword: (String) -> Unit,
  status: Status, onStatus: (Status) -> Unit,
  rememberMe: Boolean, onRememberMe: (Boolean) -> Unit,
  onSubmit: () -> Unit,
) {
  val submit = KeyboardActions(onDone = { onSubmit() })
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(
      address, onAddress, Modifier.fillMaxWidth(),
      label = { Text("Address") }, placeholder = { Text("name@example.org") }, singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = submit,
    )
    OutlinedTextField(
      password, onPassword, Modifier.fillMaxWidth(),
      label = { Text("Password") }, singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = submit,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Sign in as:", color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.width(4.dp))
      StatusSelector(onSelect = onStatus) {
        StatusDot(status)
        Spacer(Modifier.width(6.dp))
        Text(status.label)
      }
    }
    // Pull the checkbox's built-in touch padding back so it lines up with the fields.
    Row(Modifier.offset(x = (-12).dp), verticalAlignment = Alignment.CenterVertically) {
      Checkbox(rememberMe, onRememberMe)
      Text("Remember me")
    }
    Button(onClick = onSubmit, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Sign in") }
  }
}
