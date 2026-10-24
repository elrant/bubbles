package team.elrant.bubbles

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jxmpp.jid.BareJid
import org.slf4j.LoggerFactory
import team.elrant.bubbles.xmpp.ConnectedUser
import java.time.ZoneOffset

/**
 * The UTC offsets of contacts' clocks (XEP-0202), so their local time can be shown. Snapshot state, like [Bubbles].
 *
 * @param scope where the requests run; results are applied on its (main) dispatcher.
 * @param current the signed-in account; answers that arrive after it changed are dropped.
 */
class ContactClocks(private val scope: CoroutineScope, private val current: () -> ConnectedUser?) {
  private val offsets = mutableStateMapOf<BareJid, ZoneOffset>()

  /** The UTC offset of [jid]'s clock, if they have told us; `null` otherwise. */
  fun offset(jid: BareJid): ZoneOffset? = offsets[jid]

  /** Asks [jid]'s most available device for its clock's UTC offset in the background. Failures are only logged. */
  fun request(connected: ConnectedUser, jid: BareJid) {
    scope.launch {
      try {
        val offset = runInterruptible(Dispatchers.IO) { connected.localOffset(jid) }
        if (current() === connected && offset != null) offsets[jid] = offset
      } catch (e: SmackException) {
        logger.debug("{} doesn't tell their local time: {}", jid, e.message)
      } catch (e: XMPPException) {
        logger.debug("{} refused to tell their local time: {}", jid, e.message)
      }
    }
  }

  /** Forgets every offset, e.g. on sign-out. */
  fun clear() = offsets.clear()

  private companion object {
    val logger = LoggerFactory.getLogger(ContactClocks::class.java)
  }
}
