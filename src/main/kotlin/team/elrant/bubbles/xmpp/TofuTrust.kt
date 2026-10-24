package team.elrant.bubbles.xmpp

import com.fasterxml.jackson.annotation.JsonProperty
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback
import org.jivesoftware.smackx.omemo.trust.TrustState
import org.slf4j.LoggerFactory
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Trust on first use: the first fingerprint seen for a device is trusted and remembered, and a device that later
 * shows a different fingerprint is [TrustState.undecided], so nothing is encrypted to it until a user decides.
 * Decisions are kept in [file]; I/O errors are logged and the decisions then only last for this session.
 *
 * @param file JSON file holding the decisions.
 */
internal class TofuTrust(private val file: Path) : OmemoTrustCallback {
  /** Device (`jid:id`) to its pinned fingerprint and state. */
  private val entries: MutableMap<String, Pinned> = load()

  @Synchronized
  override fun getTrust(device: OmemoDevice, fingerprint: OmemoFingerprint): TrustState {
    val key = key(device)
    val pinned = entries[key]
    return when {
      pinned == null -> {
        logger.info("Trusting new OMEMO device {} on first use, fingerprint {}", device, fingerprint)
        setTrust(device, fingerprint, TrustState.trusted)
        TrustState.trusted
      }

      pinned.fingerprint != fingerprint.toString() -> {
        logger.warn("OMEMO device {} changed its fingerprint to {}", device, fingerprint)
        TrustState.undecided
      }

      else -> {
        TrustState.valueOf(pinned.state)
      }
    }
  }

  @Synchronized
  override fun setTrust(device: OmemoDevice, fingerprint: OmemoFingerprint, state: TrustState) {
    entries[key(device)] = Pinned(fingerprint.toString(), state.name)
    save()
  }

  private fun key(device: OmemoDevice) = "${device.jid}:${device.deviceId}"

  /** The saved decisions, or none if there are no usable ones. */
  private fun load(): MutableMap<String, Pinned> = try {
    if (Files.exists(file)) {
      MAPPER.readValue(file.toFile(), Saved::class.java).entries.toMutableMap()
    } else {
      mutableMapOf()
    }
  } catch (e: JacksonException) {
    logger.warn("Could not read {}: {}", file, e.message)
    mutableMapOf()
  }

  private fun save() {
    try {
      Files.createDirectories(file.parent)
      MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), Saved(entries))
    } catch (e: IOException) {
      logger.warn("Could not write {}: {}", file, e.message)
    } catch (e: JacksonException) {
      logger.warn("Could not serialize the trust decisions to {}: {}", file, e.message)
    }
  }

  /** On-disk format of [file]. */
  private data class Saved(@param:JsonProperty("entries") val entries: Map<String, Pinned>)

  /** A device's pinned fingerprint and its [TrustState] by name. */
  private data class Pinned(
    @param:JsonProperty("fingerprint") val fingerprint: String,
    @param:JsonProperty("state") val state: String,
  )

  companion object {
    private val logger = LoggerFactory.getLogger(TofuTrust::class.java)
    private val MAPPER = JsonMapper.builder().build()
  }
}
