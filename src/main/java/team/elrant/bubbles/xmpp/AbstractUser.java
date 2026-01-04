package team.elrant.bubbles.xmpp;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * The User class represents a user in the XMPP system.
 * It stores the username and service name of the user.
 */
public class AbstractUser implements Serializable {
  private static final Logger logger = LoggerFactory.getLogger(AbstractUser.class);

  public @NotNull String username;
  public @NotNull String serviceName;

  /**
   * Constructs a User object with the specified username and service name.
   *
   * @param username    The username of the user.
   * @param serviceName The service name of the XMPP server.
   */
  public AbstractUser(
      @NotNull String username,
      @NotNull String serviceName
  ) {
    this.username = username;
    this.serviceName = serviceName;
  }

  /**
   * Loads the user information from a file and initializes a User object.
   *
   * @param filename The name of the file containing the serialized user information.
   * @throws IOException            If an I/O error occurs while reading the file.
   * @throws ClassNotFoundException If the class of a serialized object cannot be found.
   */
  public AbstractUser(@NotNull String filename) throws IOException, ClassNotFoundException {
    try (var obj = new ObjectInputStream(new FileInputStream(filename))) {
      var user = (AbstractUser) obj.readObject();
      this.username = user.getUsername();
      this.serviceName = user.getServiceName();
      logger.info("User information loaded from {}", filename);
    } catch (IOException | ClassNotFoundException e) {
      logger.error("Error loading user information from file: {}", e.getMessage());
      throw e;
    }
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
