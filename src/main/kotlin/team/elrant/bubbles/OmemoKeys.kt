package team.elrant.bubbles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jxmpp.jid.BareJid
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
    return try {
      runInterruptible(Dispatchers.IO) { current.ownIdentity() }
    } catch (e: CorruptedOmemoKeyException) {
      logger.warn(KEY_FAILED, e.message)
      null
    } catch (e: SmackException) {
      logger.warn(KEY_FAILED, e.message)
      null
    } catch (e: IOException) {
      logger.warn(KEY_FAILED, e.message)
      null
    }
  }

  /**
   * The OMEMO devices of [jid] with fingerprints and trust, fetched from the server; `null` if that fails or nobody
   * is signed in.
   */
  suspend fun devicesOf(jid: BareJid): List<OmemoIdentity>? {
    val current = user() ?: return null
    return try {
      runInterruptible(Dispatchers.IO) { current.identities(jid) }
    } catch (e: CorruptedOmemoKeyException) {
      logger.warn(IDENTITIES_FAILED, jid, e.message)
      null
    } catch (e: CannotEstablishOmemoSessionException) {
      logger.warn(IDENTITIES_FAILED, jid, e.message)
      null
    } catch (e: SmackException) {
      logger.warn(IDENTITIES_FAILED, jid, e.message)
      null
    } catch (e: IOException) {
      logger.warn(IDENTITIES_FAILED, jid, e.message)
      null
    }
  }

  /** Trusts or distrusts [identity] from now on, so messages are or are no longer encrypted to that device. */
  suspend fun setTrusted(identity: OmemoIdentity, trusted: Boolean) {
    val current = user() ?: return
    runInterruptible(Dispatchers.IO) { current.setTrusted(identity, trusted) }
  }

  private companion object {
    val logger = LoggerFactory.getLogger(OmemoKeys::class.java)
    const val IDENTITIES_FAILED = "Could not list the OMEMO devices of {}: {}"
    const val KEY_FAILED = "Could not read our OMEMO fingerprint: {}"
  }
}
