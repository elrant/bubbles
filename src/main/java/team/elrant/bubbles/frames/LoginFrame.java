package team.elrant.bubbles.frames;

import cc.lunary.swingify.SwingItem;
import net.miginfocom.swing.MigLayout;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.xmpp.ConnectedUser;
import javax.swing.*;
import java.awt.*;


public final class LoginFrame {
  public static SwingItem<JFrame> ITEM;
  public static ConnectedUser ActiveUser = null;
  static {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var loginPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));
    var loginTxtBoxName = new SwingItem.Builder<>(JTextField::new).size(160, 20).build();
    var loginTxtBoxServiceName = new SwingItem.Builder<>(JTextField::new).size(160, 20).build();
    var loginTxtBoxPass = new SwingItem.Builder<>(JPasswordField::new).size(160, 20).build();
    var loginBtnProceed = new SwingItem.Builder<>(() -> new JButton("Login"))
        .mouse(SwingItem.Mouse.CLICK, event -> {
          String username = loginTxtBoxName.component().getText();
          String serviceName = loginTxtBoxServiceName.component().getText();
          String password = new String(loginTxtBoxPass.component().getPassword());
          Bubbles.get().logger().info(
              "click! :3 {}@{} {}",
              username, serviceName, password
          );
          try {
            ActiveUser = new ConnectedUser(username, password, serviceName);
            ActiveUser.initializeConnection();
            
            Bubbles.get().logger().info(ActiveUser.getRoster().getEntries().toString());
          } catch (Exception exception) {
            Bubbles.get().logger().error("couldn't establish connection", exception);
          }
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
