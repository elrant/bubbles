package team.elrant.bubbles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.ChatMessage
import team.elrant.bubbles.store.HistoryStore
import team.elrant.bubbles.xmpp.ConnectedUser

/**
 * Restores the message histories from a [HistoryStore] at sign-in and writes them back shortly after they change.
 * Nothing is saved until [restore] has read the saved history, so one that couldn't be read is never overwritten.
 *
 * @param scope where the delayed writes are scheduled (the main dispatcher).
 * @param histories the live histories by contact; read on the main dispatcher, or at shutdown.
 */
class HistoryKeeper(private val scope: CoroutineScope, private val histories: () -> Map<BareJid, List<ChatMessage>>) {
  private var store: HistoryStore? = null
  private var job: Job? = null

  /**
   * Reads the history saved for [connected] in the background and passes each conversation to [merge], then starts
   * saving. Does nothing if the keyring is locked or the file can't be read, or if [current] is no longer [connected].
   */
  fun restore(connected: ConnectedUser, current: () -> ConnectedUser?, merge: (BareJid, List<ChatMessage>) -> Unit) {
    val target = HistoryStore(connected.address)
    scope.launch {
      val saved = withContext(Dispatchers.IO) { target.load() }
      if (current() !== connected || saved == null) return@launch
      store = target
      for ((address, messages) in saved) {
        try {
          merge(JidCreate.bareFrom(address), messages)
        } catch (e: XmppStringprepException) {
          logger.warn("Skipping the saved history of the invalid address {}: {}", address, e.message)
        }
      }
    }
  }

  /** Saves shortly after the last change, in the background, so a burst of changes is written once. */
  fun schedule() {
    val target = store ?: return
    job?.cancel()
    job = scope.launch {
      delay(SAVE_DELAY_MS)
      val snapshot = snapshot()
      withContext(Dispatchers.IO) { target.save(snapshot) }
    }
  }

  /** Saves right away in the background and stops saving, e.g. before the histories are cleared. */
  fun finish() {
    job?.cancel()
    job = null
    val target = store ?: return
    store = null
    val snapshot = snapshot()
    scope.launch(Dispatchers.IO) { target.save(snapshot) }
  }

  /** Saves right away and waits for it; for shutdown. */
  fun finishBlocking() {
    job?.cancel()
    store?.save(snapshot())
  }

  /** A copy of every conversation's history, keyed by the contact's address. */
  private fun snapshot(): Map<String, List<ChatMessage>> =
    histories().entries.associate { (jid, messages) -> jid.toString() to messages.toList() }

  private companion object {
    val logger = LoggerFactory.getLogger(HistoryKeeper::class.java)

    /** How long after a change the history is written, so that a burst of changes costs one write. */
    const val SAVE_DELAY_MS = 2000L
  }
}
