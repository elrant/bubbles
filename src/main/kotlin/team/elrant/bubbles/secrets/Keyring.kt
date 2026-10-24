package team.elrant.bubbles.secrets

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.types.Variant
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The keyring can't be used: no Secret Service on the session bus, the user dismissed a prompt, or a call failed. */
class KeyringException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * The user's keyring through the freedesktop.org Secret Service (GNOME Keyring, KeePassXC, KWallet's compatibility
 * layer...), storing secrets in the default collection. Items are found by the attributes `application=bubbles`,
 * `account` and `kind`. Secrets cross the local session bus in clear (`plain` session), as with `secret-tool`.
 *
 * One instance is one bus connection and session, so use it briefly, via [use], and from one thread at a time.
 * Calls block, and may wait for the user to unlock the keyring. Failures are [KeyringException]s.
 */
class Keyring private constructor(
    private val connection: DBusConnection,
    private val service: ServiceApi,
    private val session: DBusPath,
) : AutoCloseable {
    /** The secret saved for [account] and [kind], or `null` if there is none. */
    fun get(account: String, kind: String): ByteArray? = call {
        val item = find(account, kind) ?: return@call null
        val secret = remote(item.path, ItemApi::class.java).getSecret(session)
        // With the plain algorithm the secret comes back as is: same session, no encryption parameters. Paths are
        // compared as strings because DBusPath.equals also compares where the path was received from.
        if (secret.session.path != session.path || secret.parameters.isNotEmpty()) {
            throw KeyringException("Unexpected secret format")
        }
        logger.debug("Read a {} secret", secret.contentType)
        secret.value
    }

    /** Saves [secret] for [account] and [kind], replacing any previous one; [label] is what the keyring shows. */
    fun put(account: String, kind: String, label: String, secret: ByteArray) = call {
        val collection = service.readAlias(DEFAULT_ALIAS)
        if (collection.path == NO_OBJECT) throw KeyringException("The keyring has no default collection")
        unlock(collection)
        val properties = mapOf(
            "org.freedesktop.Secret.Item.Label" to Variant(label),
            "org.freedesktop.Secret.Item.Attributes" to Variant(attributes(account, kind), "a{ss}"),
        )
        val created = remote(collection.path, CollectionApi::class.java)
            .createItem(properties, Secret(session, ByteArray(0), secret, CONTENT_TYPE), true)
        logger.debug("Stored {} as {}", kind, created.item.path)
        awaitPrompt(created.prompt)
    }

    /** Removes the secret saved for [account] and [kind], if any. */
    fun delete(account: String, kind: String) = call {
        val item = find(account, kind) ?: return@call
        awaitPrompt(remote(item.path, ItemApi::class.java).delete())
    }

    override fun close() {
        try {
            remote(session.path, SessionApi::class.java).close()
        } catch (e: DBusException) {
            closeFailed(e)
        } catch (e: DBusExecutionException) {
            closeFailed(e)
        }
        connection.disconnect()
    }

    private fun closeFailed(cause: Exception) {
        logger.debug("Could not close the Secret Service session: {}", cause.message)
    }

    /** The item for [account] and [kind], unlocked; `null` if there is none. */
    private fun find(account: String, kind: String): DBusPath? {
        val found = service.searchItems(attributes(account, kind))
        return found.unlocked.firstOrNull() ?: found.locked.firstOrNull()?.also(::unlock)
    }

    /** Unlocks [path], waiting for the user to answer the keyring's prompt if it shows one. */
    private fun unlock(path: DBusPath) {
        val result = service.unlock(listOf(path))
        // Without a prompt the keyring unlocks the object (or reports it already is) right away.
        if (result.prompt.path == NO_OBJECT && result.unlocked.none { it.path == path.path }) {
            throw KeyringException("Could not unlock the keyring")
        }
        awaitPrompt(result.prompt)
    }

    /** Runs [prompt] if it is a real one, and waits for the user to confirm it. */
    private fun awaitPrompt(prompt: DBusPath) {
        if (prompt.path == NO_OBJECT) return
        val done = CountDownLatch(1)
        var dismissed = true
        connection.addSigHandler(PromptApi.Completed::class.java, remote(prompt.path, PromptApi::class.java)) {
            dismissed = it.dismissed
            done.countDown()
        }.use {
            remote(prompt.path, PromptApi::class.java).prompt("")
            if (!done.await(PROMPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw KeyringException("The keyring prompt timed out")
            }
        }
        if (dismissed) throw KeyringException("The keyring prompt was dismissed")
    }

    private fun <I : org.freedesktop.dbus.interfaces.DBusInterface> remote(path: String, type: Class<I>): I =
        connection.getRemoteObject(BUS_NAME, path, type)

    /** Runs [block], turning D-Bus failures into [KeyringException]. */
    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: DBusException) {
        throw KeyringException("Secret Service call failed: ${e.message}", e)
    } catch (e: DBusExecutionException) {
        throw KeyringException("Secret Service call failed: ${e.message}", e)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Keyring::class.java)
        private const val DEFAULT_ALIAS = "default"
        private const val NO_OBJECT = "/"
        private const val CONTENT_TYPE = "text/plain; charset=utf8"
        private const val PROMPT_TIMEOUT_SECONDS = 120L

        private fun attributes(account: String, kind: String) =
            mapOf("application" to "bubbles", "account" to account, "kind" to kind)

        /** Connects to the Secret Service. Throws [KeyringException] if the session bus or the service is missing. */
        fun open(): Keyring {
            val connection = try {
                DBusConnectionBuilder.forSessionBus().build()
            } catch (e: DBusException) {
                throw KeyringException("No D-Bus session bus: ${e.message}", e)
            }
            return try {
                val service = connection.getRemoteObject(BUS_NAME, SERVICE_PATH, ServiceApi::class.java)
                val opened = service.openSession("plain", Variant(""))
                if (opened.output.value != "") throw KeyringException("The keyring didn't accept a plain session")
                Keyring(connection, service, opened.session)
            } catch (e: DBusException) {
                unavailable(connection, e)
            } catch (e: DBusExecutionException) {
                unavailable(connection, e)
            }
        }

        /** Closes [connection] and fails: the Secret Service didn't answer. */
        private fun unavailable(connection: DBusConnection, cause: Exception): Nothing {
            connection.disconnect()
            throw KeyringException(
                "No Secret Service (is a keyring such as GNOME Keyring running?): ${cause.message}",
                cause,
            )
        }
    }
}
