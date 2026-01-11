package team.elrant.bubbles.frames;

import cc.lunary.swingify.SwingItem;
import net.miginfocom.swing.MigLayout;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.xmpp.ConnectedUser;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;


public final class LoginFrame {
  public static SwingItem<JFrame> ITEM;

  static {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var loginPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));
    AtomicBoolean loggingIn = new AtomicBoolean(false);

    SwingItem<JTextField> loginTxtBoxName = new SwingItem.Builder<>(JTextField::new).size(160, 20).build();
    SwingItem<JTextField> loginTxtBoxServiceName = new SwingItem.Builder<>(JTextField::new).size(160, 20).build();
    SwingItem<JPasswordField> loginTxtBoxPass = new SwingItem.Builder<>(JPasswordField::new).size(160, 20).build();
    SwingItem<JButton> loginBtnProceed = new SwingItem.Builder<>(() -> new JButton("Login"))
        .mouse(SwingItem.Mouse.CLICK, event -> {
          if (loggingIn.get()) {
            return;
          }
          loggingIn.set(true);
          String username = loginTxtBoxName.component().getText();
          String serviceName = loginTxtBoxServiceName.component().getText();
          String password = new String(loginTxtBoxPass.component().getPassword());
          
          if (username.isEmpty() || password.isEmpty() ||  serviceName.isEmpty()) {
            JOptionPane.showMessageDialog(loginPanel.component(), "Fields are empty!", "Bubbles - Login error", JOptionPane.ERROR_MESSAGE);
            loggingIn.set(false);
            return;
          }
          
          Bubbles.get().logger().info(
              "Attempting login for: {}@{} {}",
              username, serviceName, password
          );

          Thread.ofVirtual().start(() -> {
            try {
              Bubbles.activeUser = new ConnectedUser(username, password, serviceName);
              Bubbles.activeUser.initializeConnection();

              Bubbles.roster = Bubbles.activeUser.getRoster();

              Bubbles.get().logger().info("Connection successful! Roster entries: {}", Bubbles.roster.getEntries().toString());

              Bubbles.loginWindow.hide();
              Bubbles.chatWindow.show();
              Bubbles.rosterWindow.show();
              loggingIn.set(false);
            } catch (Exception exception) {
              loggingIn.set(false);
              Bubbles.get().logger().error("Couldn't establish connection: ", exception);
            }
          });
        }).build();

    loginPanel
        .add(loginTxtBoxName)
        .add(loginTxtBoxServiceName)
        .add(loginTxtBoxPass)
        .add(loginBtnProceed);

    ITEM = builder.add(loginPanel.build()).build();
  }

  private LoginFrame() {
  }
}
