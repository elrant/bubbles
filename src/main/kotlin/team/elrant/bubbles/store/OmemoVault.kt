package team.elrant.bubbles.store

import org.slf4j.LoggerFactory
import team.elrant.bubbles.secrets.Keyring
import team.elrant.bubbles.secrets.KeyringException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keeps the OMEMO key material of one account encrypted on disk.
 *
 * Smack's key store works on plain files, so while the app runs they live in a private directory under
 * `$XDG_RUNTIME_DIR` (a tmpfs, so they never reach the disk), and [flush] seals them into one AES-256-GCM file,
 * `$XDG_CONFIG_HOME/bubbles/omemo/<account>.vault`. The AES key is random and kept only in the keyring. [close]
 * seals once more and removes the working files. After a crash they stay in the runtime directory until it is
 * cleared at logout, and are replaced by the vault's contents on the next [open].
 *
 * Key stores from versions that kept the files in `omemo/<account>/` are moved into the vault on first [open].
 *
 * @param account the account's address, `name@server`.
 * @param vaultFile where the sealed copy is kept.
 * @param legacyDirectory where older versions kept the plain files.
 * @param runtimeDirectory the directory holding the working files; defaults to one under `$XDG_RUNTIME_DIR`.
 */
class OmemoVault(
    private val account: String,
    private val vaultFile: Path = omemoConfig().resolve("$account.vault"),
    private val legacyDirectory: Path = omemoConfig().resolve(account),
    private val runtimeDirectory: Path = runtimeBase().resolve(account),
) {
    private var key: ByteArray? = null

    /** The directory the key store works in; valid after [open]. */
    val directory: Path get() = runtimeDirectory

    /**
     * Unseals the vault into [directory] (migrating a legacy store, or starting empty) and returns it. Blocking; may
     * wait for the user to unlock the keyring. Throws [KeyringException] if the keyring can't provide the key, or
     * [IOException] if the files can't be read or written.
     */
    @Synchronized
    fun open(): Path {
        val sealing = loadKey()
        key = sealing
        resetDirectory()
        when {
            Files.exists(vaultFile) -> {
                unseal(sealing)
            }

            Files.isDirectory(legacyDirectory) -> {
                logger.info("Moving the OMEMO keys of {} into the encrypted vault", account)
                copyTree(legacyDirectory, runtimeDirectory)
                flush()
                deleteTree(legacyDirectory)
            }
        }
        return runtimeDirectory
    }

    /** Seals the current contents of [directory] into the vault. Blocking. Throws [IOException] if that fails. */
    @Synchronized
    fun flush() {
        val sealing = key ?: return
        val nonce = ByteArray(NONCE_BYTES).also(RANDOM::nextBytes)
        val sealed = try {
            cipher(Cipher.ENCRYPT_MODE, sealing, nonce).doFinal(zip(runtimeDirectory))
        } catch (e: GeneralSecurityException) {
            throw IOException("Could not encrypt the OMEMO keys: ${e.message}", e)
        }
        Files.createDirectories(vaultFile.parent)
        val temporary = vaultFile.resolveSibling("${vaultFile.fileName}.tmp")
        Files.write(temporary, MAGIC + nonce + sealed)
        restrict(temporary, "rw-------")
        Files.move(temporary, vaultFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Seals one last time, then removes the working files and forgets the key. */
    @Synchronized
    fun close() {
        try {
            flush()
        } catch (e: IOException) {
            logger.error("Could not save the OMEMO keys of {}: {}", account, e.message)
        }
        deleteTree(runtimeDirectory)
        key = null
    }

    /** The vault key from the keyring; a new random one, saved there, if there is none yet. */
    private fun loadKey(): ByteArray = Keyring.open().use { keyring ->
        keyring.get(account, KEY_KIND)?.takeIf { it.size == KEY_BYTES } ?: ByteArray(KEY_BYTES).also { fresh ->
            RANDOM.nextBytes(fresh)
            keyring.put(account, KEY_KIND, "Bubbles OMEMO vault key ($account)", fresh)
        }
    }

    /** Unseals the vault into [runtimeDirectory]; a vault that can't be decrypted is set aside and replaced. */
    private fun unseal(sealing: ByteArray) {
        val data = Files.readAllBytes(vaultFile)
        val plain = try {
            require(data.size > MAGIC.size + NONCE_BYTES && data.copyOf(MAGIC.size).contentEquals(MAGIC)) {
                "bad header"
            }
            val nonce = data.copyOfRange(MAGIC.size, MAGIC.size + NONCE_BYTES)
            val offset = MAGIC.size + NONCE_BYTES
            cipher(Cipher.DECRYPT_MODE, sealing, nonce).doFinal(data, offset, data.size - offset)
        } catch (e: GeneralSecurityException) {
            return setAside(e)
        } catch (e: IllegalArgumentException) {
            return setAside(e)
        }
        unzip(plain, runtimeDirectory)
    }

    /** Keeps an unreadable vault under another name and starts without keys, which creates a new OMEMO device. */
    private fun setAside(cause: Exception) {
        val aside = vaultFile.resolveSibling("${vaultFile.fileName}.unreadable-${System.currentTimeMillis()}")
        logger.error(
            "Cannot decrypt the vault of {} ({}); keeping it as {}, starting fresh",
            account,
            cause.message,
            aside,
        )
        Files.move(vaultFile, aside)
    }

    /** An empty, private [runtimeDirectory]. */
    private fun resetDirectory() {
        deleteTree(runtimeDirectory)
        Files.createDirectories(runtimeDirectory.parent)
        restrict(runtimeDirectory.parent, PRIVATE_DIRECTORY)
        Files.createDirectory(runtimeDirectory)
        restrict(runtimeDirectory, PRIVATE_DIRECTORY)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OmemoVault::class.java)
        private val RANDOM = SecureRandom()
        private val MAGIC = "BUBVLT1".toByteArray()
        private const val KEY_KIND = "omemo-vault-key"
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val PRIVATE_DIRECTORY = "rwx------"

        private fun omemoConfig() = configFile("omemo")

        /**
         * `$XDG_RUNTIME_DIR/bubbles/omemo`; throws [IOException] where there is no such tmpfs, so keys never hit
         * disk.
         */
        private fun runtimeBase(): Path {
            val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
                ?: throw IOException("XDG_RUNTIME_DIR is not set, so there is nowhere safe to keep the OMEMO keys")
            return Path.of(runtime, "bubbles", "omemo")
        }

        private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(MAGIC)
            }

        /** Best-effort `chmod`; file systems without POSIX permissions are skipped. */
        private fun restrict(path: Path, permissions: String) {
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
            } catch (e: UnsupportedOperationException) {
                logger.warn("Cannot restrict the permissions of {}: {}", path, e.message)
            }
        }

        private fun zip(directory: Path): ByteArray {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                Files.walk(directory).use { paths ->
                    for (file in paths.filter(Files::isRegularFile)) {
                        zip.putNextEntry(ZipEntry(directory.relativize(file).joinToString("/")))
                        Files.copy(file, zip)
                        zip.closeEntry()
                    }
                }
            }
            return bytes.toByteArray()
        }

        private fun unzip(data: ByteArray, target: Path) {
            ZipInputStream(ByteArrayInputStream(data)).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val file = target.resolve(entry.name).normalize()
                    if (!file.startsWith(target)) throw IOException("Bad path in the OMEMO vault: ${entry.name}")
                    Files.createDirectories(file.parent)
                    Files.copy(zip, file, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        private fun copyTree(from: Path, to: Path) {
            Files.walk(from).use { paths ->
                for (source in paths) {
                    val destination = to.resolve(from.relativize(source).toString())
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination)
                    } else {
                        Files.copy(source, destination)
                    }
                }
            }
        }

        private fun deleteTree(directory: Path) {
            if (!Files.exists(directory)) return
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
