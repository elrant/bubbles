package team.elrant.bubbles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jxmpp.jid.BareJid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.OmemoIdentity
import team.elrant.bubbles.xmpp.ConnectedUser
import java.io.IOException

/**
 * Looks up and decides the trust of OMEMO devices through the signed-in account. Network and key work runs on
 * [Dispatchers.IO]; failures are logged and reported as `null`.
 *
 * @param user the signed-in account, if any; read on each call.
 */
class OmemoKeys(private val user: () -> ConnectedUser?) {
  /** Our own OMEMO device and fingerprint, or `null` if signed out, OMEMO isn't running or the keys are unreadable. */
  suspend fun own(): OmemoIdentity? {
    val current = user() ?: return null
    return guarded("Could not read our OMEMO fingerprint", "this device") { current.ownIdentity() }
  }

  /**
   * The OMEMO devices of [jid] with fingerprints and trust, fetched from the server; `null` if that fails or nobody
   * is signed in.
   */
  suspend fun devicesOf(jid: BareJid): List<OmemoIdentity>? {
    val current = user() ?: return null
    return guarded("Could not list the OMEMO devices", jid) { current.identities(jid) }
  }

  /** Trusts or distrusts [identity] from now on, so messages are or are no longer encrypted to that device. */
  suspend fun setTrusted(identity: OmemoIdentity, trusted: Boolean) {
    val current = user() ?: return
    runInterruptible(Dispatchers.IO) { current.setTrusted(identity, trusted) }
  }

  /** Runs [block] on [Dispatchers.IO]; if it fails, logs "[what] for [subject]: reason" and returns `null`. */
  private suspend fun <T> guarded(what: String, subject: Any, block: () -> T): T? = try {
    runInterruptible(Dispatchers.IO, block = block)
  } catch (e: CorruptedOmemoKeyException) {
    failed(what, subject, e)
  } catch (e: CannotEstablishOmemoSessionException) {
    failed(what, subject, e)
  } catch (e: SmackException) {
    failed(what, subject, e)
  } catch (e: IOException) {
    failed(what, subject, e)
  }

  private fun failed(what: String, subject: Any, cause: Exception): Nothing? {
    logger.warn("{} for {}: {}", what, subject, cause.message)
    return null
  }

  private companion object {
    val logger: Logger = LoggerFactory.getLogger(OmemoKeys::class.java)
  }
}
