package team.elrant.bubbles.store

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Remembers the last signed-in address ("Remember me"). Passwords are never stored.
 */
class AccountStore(private val file: Path = defaultFile()) {

  /** The remembered address, or `null` if none or the file is unreadable. */
  fun load(): String? = try {
    if (Files.exists(file)) MAPPER.readValue(file.toFile(), SavedAccount::class.java).address else null
  } catch (e: Exception) {
    logger.warn("Could not read {}: {}", file, e.message)
    null
  }

  fun save(address: String) = try {
    Files.createDirectories(file.parent)
    MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), SavedAccount(address))
  } catch (e: Exception) {
    logger.warn("Could not write {}: {}", file, e.message)
  }

  fun clear() = try {
    Files.deleteIfExists(file)
    Unit
  } catch (e: Exception) {
    logger.warn("Could not delete {}: {}", file, e.message)
  }

  private data class SavedAccount(@param:JsonProperty("address") val address: String?)

  companion object {
    private val logger = LoggerFactory.getLogger(AccountStore::class.java)
    private val MAPPER = ObjectMapper()

    private fun defaultFile(): Path {
      val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".config")
      return configHome.resolve("bubbles").resolve("account.json")
    }
  }
}
