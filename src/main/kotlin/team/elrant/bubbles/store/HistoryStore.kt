package team.elrant.bubbles.store

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.ChatMessage
import team.elrant.bubbles.secrets.Keyring
import team.elrant.bubbles.secrets.KeyringException
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.DateTimeException
import java.time.LocalDateTime
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keeps the message history of one account on disk, encrypted, so conversations survive a restart.
 *
 * This is the only copy of messages that travelled OMEMO-encrypted: the server archive holds ciphertext that can't be
 * decrypted a second time. The file, `$XDG_CONFIG_HOME/bubbles/history/<account>.bin`, is AES-256-GCM sealed with a
 * random key that is kept only in the keyring. Messages that failed or are still being sent are not saved, and only
 * the latest [MAX_PER_CONTACT] of each conversation are kept.
 *
 * Problems are logged and otherwise ignored: history is then simply not remembered.
 *
 * @param account the account's address, `name@server`.
 * @param file where the sealed history is kept.
 */
class HistoryStore(
  private val account: String,
  private val file: Path = configFile("history").resolve("$account.bin"),
) {
  private var key: ByteArray? = null

  /**
   * The saved history by contact address, oldest message first; empty if there is none. `null` if it exists but can't
   * be reached right now (keyring locked, I/O error), in which case it must not be overwritten. Blocking; may wait
   * for the user to unlock the keyring.
   */
  @Synchronized
  fun load(): Map<String, List<ChatMessage>>? = try {
    if (Files.exists(file)) read(sealingKey()) else emptyMap()
  } catch (e: KeyringException) {
    logger.warn("Could not unlock the message history of {}: {}", account, e.message)
    null
  } catch (e: IOException) {
    logger.warn("Could not read the message history of {}: {}", account, e.message)
    null
  }

  /**
   * Replaces the saved history with [history], by contact address. Blocking; may wait for the user to unlock the
   * keyring.
   */
  @Synchronized
  fun save(history: Map<String, List<ChatMessage>>) {
    try {
      write(sealingKey(), history)
    } catch (e: KeyringException) {
      logger.warn("Could not unlock the message history of {}: {}", account, e.message)
    } catch (e: IOException) {
      logger.warn("Could not write the message history of {}: {}", account, e.message)
    }
  }

  /** The AES key from the keyring; a new random one, saved there, if there is none yet. */
  private fun sealingKey(): ByteArray = key ?: Keyring.open().use { keyring ->
    keyring.get(account, KEY_KIND)?.takeIf { it.size == KEY_BYTES } ?: ByteArray(KEY_BYTES).also { fresh ->
      RANDOM.nextBytes(fresh)
      keyring.put(account, KEY_KIND, "Bubbles history key ($account)", fresh)
    }
  }.also { key = it }

  /** Decrypts and parses [file]; one that can't be is set aside and treated as empty. */
  private fun read(sealing: ByteArray): Map<String, List<ChatMessage>> {
    val data = Files.readAllBytes(file)
    return try {
      require(data.size > MAGIC.size + NONCE_BYTES && data.copyOf(MAGIC.size).contentEquals(MAGIC)) { "bad header" }
      val nonce = data.copyOfRange(MAGIC.size, MAGIC.size + NONCE_BYTES)
      val offset = MAGIC.size + NONCE_BYTES
      val plain = cipher(Cipher.DECRYPT_MODE, sealing, nonce).doFinal(data, offset, data.size - offset)
      MAPPER.readValue(plain, SavedHistory::class.java).toHistory()
    } catch (e: GeneralSecurityException) {
      setAside(e)
    } catch (e: IllegalArgumentException) {
      setAside(e)
    } catch (e: JacksonException) {
      setAside(e)
    }
  }

  /** Keeps an unreadable [file] under another name and starts empty. */
  private fun setAside(cause: Exception): Map<String, List<ChatMessage>> {
    val aside = file.resolveSibling("${file.fileName}.unreadable-${System.currentTimeMillis()}")
    logger.error("Cannot read the message history of {} ({}); keeping it as {}", account, cause.message, aside)
    Files.move(file, aside)
    return emptyMap()
  }

  /** Seals [history] into [file], atomically. */
  private fun write(sealing: ByteArray, history: Map<String, List<ChatMessage>>) {
    val nonce = ByteArray(NONCE_BYTES).also(RANDOM::nextBytes)
    val sealed = try {
      cipher(Cipher.ENCRYPT_MODE, sealing, nonce).doFinal(MAPPER.writeValueAsBytes(SavedHistory.from(history)))
    } catch (e: GeneralSecurityException) {
      throw IOException("Could not encrypt the message history: ${e.message}", e)
    } catch (e: JacksonException) {
      throw IOException("Could not serialize the message history: ${e.message}", e)
    }
    Files.createDirectories(file.parent)
    val temporary = file.resolveSibling("${file.fileName}.tmp")
    Files.write(temporary, MAGIC + nonce + sealed)
    restrict(temporary)
    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }

  /** Best-effort `chmod 600`; file systems without POSIX permissions are skipped. */
  private fun restrict(path: Path) {
    try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
    } catch (e: UnsupportedOperationException) {
      logger.warn("Cannot restrict the permissions of {}: {}", path, e.message)
    }
  }

  /** On-disk format of the decrypted history. Nullable fields because Jackson doesn't know Kotlin's defaults. */
  private data class SavedHistory(
    @param:JsonProperty("conversations") val conversations: Map<String, List<SavedMessage>>?,
  ) {
    /** These conversations as messages; entries that can't be read are skipped. */
    fun toHistory(): Map<String, List<ChatMessage>> =
      conversations.orEmpty().mapValues { (_, messages) -> messages.mapNotNull(SavedMessage::toMessage) }

    companion object {
      /** The latest messages of each conversation in [history] that were actually sent or received. */
      fun from(history: Map<String, List<ChatMessage>>) = SavedHistory(
        history.mapValues { (_, messages) ->
          messages.filter { !it.failed && !it.pending }.takeLast(MAX_PER_CONTACT).map(SavedMessage::from)
        }.filterValues { it.isNotEmpty() },
      )
    }
  }

  /** One message in [SavedHistory]; the time is stored as ISO-8601 text. */
  private data class SavedMessage(
    @param:JsonProperty("body") val body: String?,
    @param:JsonProperty("outgoing") val outgoing: Boolean?,
    @param:JsonProperty("time") val time: String?,
    @param:JsonProperty("encrypted") val encrypted: Boolean?,
    @param:JsonProperty("read") val read: Boolean?,
    @param:JsonProperty("id") val id: String?,
  ) {
    /** This message, or `null` if its text or time is missing or invalid. */
    fun toMessage(): ChatMessage? {
      val parsed = try {
        time?.let(LocalDateTime::parse)
      } catch (e: DateTimeException) {
        logger.warn("Skipping a saved message with the bad time “{}”: {}", time, e.message)
        null
      }
      return if (body == null || parsed == null) {
        null
      } else {
        ChatMessage(body, outgoing ?: false, parsed, encrypted ?: false, read = read ?: false, id = id)
      }
    }

    companion object {
      /** [message] in its stored form. */
      fun from(message: ChatMessage) = SavedMessage(
        message.body, message.outgoing, message.time.toString(), message.encrypted, message.read, message.id,
      )
    }
  }

  companion object {
    /** How many messages of one conversation are kept. */
    const val MAX_PER_CONTACT = 1000

    private val logger = LoggerFactory.getLogger(HistoryStore::class.java)
    private val MAPPER = JsonMapper.builder().build()
    private val RANDOM = SecureRandom()
    private val MAGIC = "BUBHST1".toByteArray()
    private const val KEY_KIND = "history-key"
    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
      Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        updateAAD(MAGIC)
      }
  }
}
