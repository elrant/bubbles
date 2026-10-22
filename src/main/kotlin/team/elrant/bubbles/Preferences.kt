package team.elrant.bubbles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.elrant.bubbles.model.Settings
import team.elrant.bubbles.store.AccountStore
import team.elrant.bubbles.store.SettingsStore

/**
 * What the app persists between runs: the user's [settings] and the "Remember me" [savedAddress].
 *
 * Like [Bubbles], this is Compose snapshot state owned by the Swing thread; files are loaded when it is
 * created and written in the background on [Dispatchers.IO].
 *
 * @param accounts where [savedAddress] is kept.
 * @param settingsStore where [settings] are kept.
 * @param scope main-thread scope that runs the background saves.
 */
class Preferences(
  private val accounts: AccountStore,
  private val settingsStore: SettingsStore,
  private val scope: CoroutineScope,
) {
  /** Address remembered from the last "Remember me" sign-in, or `null` if none. */
  var savedAddress by mutableStateOf(accounts.load())
    private set

  /** The user's preferences; change them with [update]. */
  var settings by mutableStateOf(settingsStore.load())
    private set

  /** Replaces [settings] with `transform(settings)` and saves them in the background. */
  fun update(transform: (Settings) -> Settings) {
    val updated = transform(settings)
    if (updated == settings) return
    settings = updated
    scope.launch(Dispatchers.IO) { settingsStore.save(updated) }
  }

  /** Forgets [savedAddress]. */
  fun forgetAddress() {
    savedAddress = null
    scope.launch(Dispatchers.IO) { accounts.clear() }
  }

  /** Remembers [address] as [savedAddress], or forgets the saved one if it is `null`. Waits for the file. */
  internal suspend fun rememberAddress(address: String?) {
    withContext(Dispatchers.IO) { if (address != null) accounts.save(address) else accounts.clear() }
    savedAddress = address
  }
}
