package team.elrant.bubbles.xmpp

/**
 * A user in the XMPP system, identified by username and service name.
 */
abstract class AbstractUser(
  val username: String,
  val serviceName: String,
)
