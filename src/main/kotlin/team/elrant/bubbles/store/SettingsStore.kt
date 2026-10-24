package team.elrant.bubbles.store

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import team.elrant.bubbles.model.Accent
import team.elrant.bubbles.model.Flavor
import team.elrant.bubbles.model.Settings
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Persists the user's [Settings].
 *
 * Missing or unrecognised values fall back to their defaults, so files from older or newer versions still load.
 * I/O errors are logged and otherwise ignored.
 *
 * @param file JSON file to use; defaults to `$XDG_CONFIG_HOME/bubbles/settings.json`.
 */
class SettingsStore(private val file: Path = configFile("settings.json")) {
  /** The saved settings, or the defaults if none are saved or the file is unreadable. */
  fun load(): Settings = try {
    if (Files.exists(file)) MAPPER.readValue(file.toFile(), SavedSettings::class.java).toSettings() else Settings()
  } catch (e: JacksonException) {
    logger.warn("Could not read {}: {}", file, e.message)
    Settings()
  }

  /** Saves [settings], replacing the previous ones. Blocking. */
  fun save(settings: Settings) = try {
    Files.createDirectories(file.parent)
    MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), SavedSettings.from(settings))
  } catch (e: IOException) {
    logger.warn("Could not write {}: {}", file, e.message)
  } catch (e: JacksonException) {
    logger.warn("Could not serialize the settings to {}: {}", file, e.message)
  }

  /**
   * On-disk format of [file]. Every field is nullable because Jackson doesn't know Kotlin's default arguments;
   * see [toSettings]. Enums are stored by name.
   */
  private data class SavedSettings(
    @param:JsonProperty("flavor") val flavor: String?,
    @param:JsonProperty("accent") val accent: String?,
    @param:JsonProperty("enterSends") val enterSends: Boolean?,
    @param:JsonProperty("runGapMinutes") val runGapMinutes: Int?,
    @param:JsonProperty("use24HourClock") val use24HourClock: Boolean?,
    @param:JsonProperty("openOnMessage") val openOnMessage: Boolean?,
    @param:JsonProperty("raiseOnMessage") val raiseOnMessage: Boolean?,
    @param:JsonProperty("requestAttention") val requestAttention: Boolean?,
    @param:JsonProperty("encryptMessages") val encryptMessages: Boolean?,
    @param:JsonProperty("sendReadReceipts") val sendReadReceipts: Boolean?,
    @param:JsonProperty("requireDeviceApproval") val requireDeviceApproval: Boolean?,
    @param:JsonProperty("groupOrder") val groupOrder: List<String>?,
  ) {
    /** These values, with defaults for missing or invalid ones. */
    fun toSettings(): Settings {
      val defaults = Settings()
      return Settings(
        flavor = Flavor.entries.firstOrNull { it.name == flavor } ?: defaults.flavor,
        accent = Accent.entries.firstOrNull { it.name == accent } ?: defaults.accent,
        enterSends = enterSends ?: defaults.enterSends,
        runGapMinutes = runGapMinutes?.coerceIn(Settings.RUN_GAP_RANGE) ?: defaults.runGapMinutes,
        use24HourClock = use24HourClock ?: defaults.use24HourClock,
        openOnMessage = openOnMessage ?: defaults.openOnMessage,
        raiseOnMessage = raiseOnMessage ?: defaults.raiseOnMessage,
        requestAttention = requestAttention ?: defaults.requestAttention,
        encryptMessages = encryptMessages ?: defaults.encryptMessages,
        sendReadReceipts = sendReadReceipts ?: defaults.sendReadReceipts,
        requireDeviceApproval = requireDeviceApproval ?: defaults.requireDeviceApproval,
        groupOrder = groupOrder ?: defaults.groupOrder,
      )
    }

    companion object {
      /** The on-disk form of [settings]. */
      fun from(settings: Settings) = SavedSettings(
        flavor = settings.flavor.name,
        accent = settings.accent.name,
        enterSends = settings.enterSends,
        runGapMinutes = settings.runGapMinutes,
        use24HourClock = settings.use24HourClock,
        openOnMessage = settings.openOnMessage,
        raiseOnMessage = settings.raiseOnMessage,
        requestAttention = settings.requestAttention,
        encryptMessages = settings.encryptMessages,
        sendReadReceipts = settings.sendReadReceipts,
        requireDeviceApproval = settings.requireDeviceApproval,
        groupOrder = settings.groupOrder,
      )
    }
  }

  companion object {
    private val logger = LoggerFactory.getLogger(SettingsStore::class.java)
    private val MAPPER = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
  }
}
