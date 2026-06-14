package team.elrant.bubbles.xmpp;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.elrant.bubbles.Bubbles;

/**
 * The AbstractUser class represents a user in the XMPP system.
 * It stores the username and service name of the user.
 */
public abstract class AbstractUser {
  private static final Logger logger = LoggerFactory.getLogger(AbstractUser.class);
  final @NotNull Bubbles bubbles;
  private @NotNull String username;
  private @NotNull String serviceName;

  /**
   * Constructs a User object with the specified username and service name.
   *
   * @param username    The username of the user.
   * @param serviceName The service name of the XMPP server.
   */
  public AbstractUser(
      @NotNull Bubbles bubbles,
      @NotNull String username,
      @NotNull String serviceName
  ) {
    this.bubbles = bubbles;
    this.username = username;
    this.serviceName = serviceName;
  }

  /**
   * Retrieves the username of the user.
   *
   * @return The username of the user.
   */
  public @NotNull String getUsername() {
    return this.username;
  }

  /**
   * Retrieves the service name of the XMPP server.
   *
   * @return The service name of the XMPP server.
   */
  public @NotNull String getServiceName() {
    return this.serviceName;
  }
}
