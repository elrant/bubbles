package team.elrant.bubbles.store

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** The account is signed in elsewhere: in another Bubbles process, or another window of this one. */
class AccountInUseException(account: String) :
    IOException("$account is already signed in in another Bubbles instance. Close it first.")

/**
 * An exclusive, per-account lock held while this process is signed in, so two Bubbles instances can't share the
 * account's OMEMO keys and XMPP resource, which would corrupt the sessions of both. The operating system drops the
 * lock when the process ends, however it ends.
 *
 * @param account the account's address, `name@server`.
 * @param file the lock file; defaults to one under `$XDG_RUNTIME_DIR`, or the temporary directory without it.
 */
class AccountLock(private val account: String, private val file: Path = lockDirectory().resolve("$account.lock")) {
    private var channel: FileChannel? = null

    /**
     * Takes the lock, waiting a few seconds for a previous holder that is just signing out. Blocking. Does nothing if
     * already held. Throws [AccountInUseException] if somebody else holds it, or [IOException] if the file can't be
     * opened.
     */
    @Synchronized
    fun acquire() {
        if (channel != null) return
        Files.createDirectories(file.parent)
        val opened = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        repeat(ATTEMPTS) {
            // The lock is per process, so a holder in this one shows up as an overlapping lock instead of a null
            // result.
            val lock = try {
                opened.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock != null) {
                channel = opened
                return
            }
            Thread.sleep(RETRY_MS)
        }
        opened.close()
        throw AccountInUseException(account)
    }

    /** Drops the lock, if held. */
    @Synchronized
    fun release() {
        channel?.close()
        channel = null
    }

    private companion object {
        const val ATTEMPTS = 12
        const val RETRY_MS = 250L

        fun lockDirectory(): Path {
            val base = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("java.io.tmpdir")
            return Path.of(base, "bubbles", "locks")
        }
    }
}
