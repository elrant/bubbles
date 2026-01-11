package team.elrant.bubbles.xmpp;

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
import org.minidns.dnsname.DnsName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.elrant.bubbles.Bubbles;

import javax.net.ssl.HostnameVerifier;
import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The ConnectedUser class extends the User class.
 * It describes how the app's current user should connect, behave, send messages, etc.
 */
public class ConnectedUser extends AbstractUser {
  private static final Logger logger = LoggerFactory.getLogger(ConnectedUser.class);
  private static final HostnameVerifier HOSTNAME_VERIFIER = (hostname, session) -> true;

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

  private final @NotNull String password;
  private @Nullable Roster roster;
  private @Nullable XMPPTCPConnection connection;
  private @Nullable ChatManager chat;

  /**
   * Constructs a ConnectedUser object with the specified username, password, and service name.
   *
   * @param username    The username of the user.
   * @param password    The password of the user.
   * @param serviceName The service name of the XMPP server.
   */
  public ConnectedUser(
      @NotNull String username,
      @NotNull String password,
      @NotNull String serviceName
  ) {
    super(username, serviceName);
    this.password = password;
  }


  /**
   * Load a new Connected user from a file.
   *
   * @param file the file path
   * @throws IOException            the io exception
   * @throws ClassNotFoundException the class not found exception
   */
  public ConnectedUser(@NotNull Path file) throws IOException, ClassNotFoundException {
    super("", ""); //initialize after read file
    try (var obj = new ObjectInputStream(new FileInputStream(file.toFile()))) {
      var user = (ConnectedUser) obj.readObject();
      super.username = user.getUsername();
      super.serviceName = user.getServiceName();
      this.password = user.getPassword();
      logger.info("User information loaded from {}", file);
    } catch (IOException | ClassNotFoundException e) {
      logger.error("Error loading user information from file: {}", e.getMessage());
      throw e;
    }
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
   * Initializes the XMPP connection, logs in, sets up chat manager, and populates the roster.
   * After connecting, it sends a message to the test user.
   * This method assigns the connection and chatManager properties.
   *
   * @throws SmackException       If there is an issue with the XMPP protocol.
   * @throws InterruptedException If the operation is interrupted.
   * @throws XMPPException        If there is an XMPP related error.
   * @throws IOException          If an I/O error occurs.
   */
  public void initializeConnection() throws SmackException, InterruptedException, XMPPException, IOException {
    Bubbles.get().logger().info("logging in {}...", super.getServiceName());
    (connection = new XMPPTCPConnection(XMPPTCPConnectionConfiguration.builder()
        .setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible)
        .setXmppDomain(JidCreate.domainBareFrom(super.getServiceName()))
        .setHostAddress(InetAddress.getByName(super.getServiceName()))
        .setUsernameAndPassword(super.getUsername(), password)
        .setHostnameVerifier(HOSTNAME_VERIFIER)
        .setResource("meow")
        .build())).connect().login();
    Bubbles.get().logger().info("...welcome, {}!", super.getUsername());
    
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
   * Saves the user information (excluding password) to a file.
   *
   * @param filename     The name of the file to save the user information to.
   * @param savePassword Flag indicating whether to save the password in the file.
   */
  public void saveUserToFile(@NotNull String filename, boolean savePassword) {
    File file = new File("user.dat");
    if (file.delete()) {
      logger.info("Old user information file deleted");
    }

    try (@NotNull FileOutputStream fileOut = new FileOutputStream(filename); @NotNull ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {

      if (savePassword)
        objectOut.writeObject(new ConnectedUser(this.getUsername(), this.getPassword(), this.getServiceName()));
      else {
        ConnectedUser user = new ConnectedUser(this.getUsername(), "uninit", this.getServiceName());
        objectOut.writeObject(user);
      }

      logger.info("User information (excluding password) saved to {}", filename);
    } catch (IOException e) {
      logger.error("Error saving user information to file: {}", e.getMessage());
    }
  }

  private @NotNull String getPassword() {
    return password;
  }

  /**
   * Checks if the password is uninitialized
   *
   * @return true if the password in uninitialized, false if it isn't
   */
  public boolean passwordUnInit() {
    return this.getPassword().equals("uninit");
  }

  /**
   * Checks if the user is currently logged in.>>>>>>> main
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
}
