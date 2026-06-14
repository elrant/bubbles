package team.elrant.bubbles.xmpp;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.stringencoder.Base64;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.elrant.bubbles.Bubbles;

import javax.net.ssl.SSLSession;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The ConnectedUser class extends the AbstractUser class.
 * It describes how the app's current user should connect, behave, send messages, etc.
 */
public class ConnectedUser extends AbstractUser {
  private static final Logger logger = LoggerFactory.getLogger(ConnectedUser.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    Base64.setEncoder(new Base64.Encoder() {
      private static final java.util.Base64.Encoder ENCODER = java.util.Base64.getEncoder();
      private static final java.util.Base64.Decoder DECODER = java.util.Base64.getDecoder();

      @Override
      public byte[] decode(String string) {
        return DECODER.decode(string);
      }

      @Override
      public String encodeToString(byte[] input) {
        return ENCODER.encodeToString(input);
      }

      @Override
      public String encodeToStringWithoutPadding(byte[] input) {
        return ENCODER.encodeToString(input);
      }

      @Override
      public byte[] encode(byte[] input) {
        return ENCODER.encode(input);
      }
    });
  }

  /** Null means password was not persisted. */
  private final @Nullable String password;
  private @Nullable Roster roster;
  private @Nullable XMPPTCPConnection connection;
  private @Nullable ChatManager chat;

  /**
   * Constructs a ConnectedUser object with the specified username, password, and service name.
   *
   * @param username    The username of the user.
   * @param password    The password of the user, or {@code null} if not stored.
   * @param serviceName The service name of the XMPP server.
   */
  public ConnectedUser(
      @NotNull Bubbles bubbles,
      @NotNull String username,
      @Nullable String password,
      @NotNull String serviceName
  ) {
    super(bubbles, username, serviceName);
    this.password = password;
  }

  /**
   * Loads a ConnectedUser from a JSON file previously written by {@link #saveUserToFile}.
   *
   * @param bubbles the application instance
   * @param file    path to the JSON file
   * @return the loaded ConnectedUser
   * @throws IOException if the file cannot be read or parsed
   */
  public static @NotNull ConnectedUser fromFile(@NotNull Bubbles bubbles, @NotNull Path file) throws IOException {
    var data = MAPPER.readValue(file.toFile(), UserData.class);
    logger.info("User information loaded from {}", file);
    return new ConnectedUser(bubbles, data.username(), data.serviceName(), data.password());
  }

  /**
   * Retrieves the roster of the connected user.
   *
   * @return The roster of the connected user.
   * @throws IllegalStateException if the roster is not initialized.
   */
  public @NotNull Roster getRoster() {
    if (this.roster == null) {
      throw new IllegalStateException("Roster is not initialized.");
    }
    return this.roster;
  }

  /**
   * Initializes the XMPP connection, logs in, sets up the chat manager, and populates the roster.
   *
   * @throws SmackException       If there is an issue with the XMPP protocol.
   * @throws InterruptedException If the operation is interrupted.
   * @throws XMPPException        If there is an XMPP related error.
   * @throws IOException          If an I/O error occurs.
   * @throws IllegalStateException if no password is available (loaded without password).
   */
  public void initializeConnection() throws SmackException, InterruptedException, XMPPException, IOException {
    if (password == null) {
      throw new IllegalStateException("Cannot connect: no password available.");
    }
    super.bubbles.logger().info("logging in {}...", super.getServiceName());
    final String serviceName = getServiceName();
    (connection = new XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
        .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
        .setXmppDomain(JidCreate.domainBareFrom(serviceName))
        .setHostAddress(InetAddress.getByName(serviceName))
        .setUsernameAndPassword(super.getUsername(), password)
        .setHostnameVerifier((host, session) -> verifyCertHostname(serviceName, session))
        .setResource("meow")
        .build())).connect().login();
    super.bubbles.logger().info("...welcome, {}!", super.getUsername());

    chat = ChatManager.getInstanceFor(connection);
    roster = Roster.getInstanceFor(connection);
    roster.reloadAndWait();
  }

  /**
   * Sends a message to a contact.
   *
   * @param contactJid The JID of the contact to send the message to (user@service.name).
   * @param message    The message to send.
   */
  public void sendMessage(@NotNull BareJid contactJid, @NotNull String message) {
    try {
      if (chat != null) {
        Chat chat = this.chat.chatWith((EntityBareJid) contactJid);
        chat.send(message);
      }
    } catch (Exception e) {
      logger.error("Error sending XMPP message: {}", e.getMessage());
    }
  }

  /**
   * Saves user information to a JSON file.
   *
   * @param filename     The path of the file to write.
   * @param savePassword Whether to include the password in the saved data.
   */
  public void saveUserToFile(@NotNull String filename, boolean savePassword) {
    var data = new UserData(getUsername(), getServiceName(), savePassword ? password : null);
    MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(filename), data);
    logger.info("User information saved to {}", filename);
  }

  /**
   * Checks whether the password was not persisted (i.e. the user must re-enter it).
   *
   * @return {@code true} if no password is stored.
   */
  public boolean passwordUnInit() {
    return this.password == null;
  }

  /**
   * Checks if the user is currently logged in.
   *
   * @return true if the user is logged in, otherwise false.
   */
  public boolean isLoggedIn() {
    return connection != null && connection.isAuthenticated();
  }

  /**
   * Adds an incoming message listener to the chat manager.
   *
   * @param contactJid        The JID of the contact for which to add the listener.
   * @param updateChatDisplay The consumer to handle incoming messages.
   */
  public void addIncomingMessageListener(BareJid contactJid, Consumer<String> updateChatDisplay) {
    if (chat != null) {
      chat.addIncomingListener(new ChatListener(contactJid, updateChatDisplay));
    }
  }

  /** Verifies {@code hostname} against the peer certificate's SAN/CN without relying on SSLSession peer host. */
  private static boolean verifyCertHostname(String hostname, SSLSession session) {
    try {
      var certs = session.getPeerCertificates();
      if (certs.length == 0 || !(certs[0] instanceof java.security.cert.X509Certificate cert)) return false;
      var sans = cert.getSubjectAlternativeNames();
      if (sans != null) {
        for (var san : sans) {
          if (Integer.valueOf(2).equals(san.get(0)) && hostname.equalsIgnoreCase((String) san.get(1))) return true;
        }
      }
      // fall back to CN
      for (String part : cert.getSubjectX500Principal().getName("RFC2253").split(",")) {
        part = part.trim();
        if (part.startsWith("CN=") && hostname.equalsIgnoreCase(part.substring(3))) return true;
      }
      return false;
    } catch (Exception e) {
      logger.error("Hostname verification error: {}", e.getMessage());
      return false;
    }
  }

  private record UserData(
      @JsonProperty("username") String username,
      @JsonProperty("serviceName") String serviceName,
      @JsonProperty("password") @Nullable String password
  ) {}
}

