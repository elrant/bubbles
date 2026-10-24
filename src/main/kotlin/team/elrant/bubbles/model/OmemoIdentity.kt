package team.elrant.bubbles.model

import org.jxmpp.jid.BareJid

/**
 * One OMEMO device of an account and the fingerprint of its identity key.
 *
 * @property jid account the device belongs to.
 * @property deviceId the device's OMEMO id.
 * @property fingerprint hex fingerprint of the identity key.
 * @property trust what has been decided about it.
 */
data class OmemoIdentity(val jid: BareJid, val deviceId: Int, val fingerprint: String, val trust: Trust) {
  /**
   * Whether messages are encrypted to a device.
   *
   * @property label user-facing name.
   */
  enum class Trust(val label: String) {
    Undecided("Not yet decided"),
    Trusted("Trusted"),
    Untrusted("Not trusted"),
  }
}
