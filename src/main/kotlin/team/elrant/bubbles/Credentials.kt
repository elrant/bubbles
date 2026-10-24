package team.elrant.bubbles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import team.elrant.bubbles.secrets.Keyring
import team.elrant.bubbles.secrets.KeyringException

/**
 * Account passwords remembered in the system keyring. Calls run on [Dispatchers.IO] and may wait for the user to
 * unlock the keyring. A keyring that can't be used is logged and treated as if nothing were stored.
 */
class Credentials {
    private var autoSignInClaimed = false

    /** The password saved for [address], or `null` if there is none or the keyring is unavailable. */
    suspend fun saved(address: String): String? = try {
        runInterruptible(Dispatchers.IO) { Keyring.open().use { it.get(address, KIND) } }?.decodeToString()
    } catch (e: KeyringException) {
        logger.warn("Could not read the saved password of {}: {}", address, e.message)
        null
    }

    /** Saves [password] for [address]; returns whether that worked. */
    suspend fun save(address: String, password: String): Boolean = try {
        runInterruptible(Dispatchers.IO) {
            Keyring.open().use { it.put(address, KIND, "Bubbles password ($address)", password.encodeToByteArray()) }
        }
        true
    } catch (e: KeyringException) {
        logger.warn("Could not save the password of {}: {}", address, e.message)
        false
    }

    /** Deletes the password saved for [address], if any. */
    suspend fun forget(address: String) {
        try {
            runInterruptible(Dispatchers.IO) { Keyring.open().use { it.delete(address, KIND) } }
        } catch (e: KeyringException) {
            logger.warn("Could not delete the saved password of {}: {}", address, e.message)
        }
    }

    /** `true` the first time it is called in a run, `false` after: only the first sign-in screen signs in by itself. */
    fun claimAutoSignIn(): Boolean = !autoSignInClaimed.also { autoSignInClaimed = true }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(Credentials::class.java)
        const val KIND = "password"
    }
}
