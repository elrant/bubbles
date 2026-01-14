package team.elrant.bubbles.frames.impl;

import cc.lunary.swingify.SwingItem;
import cc.lunary.swingify.Swingify;
import cc.lunary.swingify.Themes;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.event.LoggedInEvent;
import team.elrant.bubbles.frames.AbstractFrame;
import team.elrant.bubbles.xmpp.ConnectedUser;

import javax.swing.*;
import java.awt.*;


public final class LoginFrame extends AbstractFrame {
  private final SwingItem<JFrame> item;

  public LoginFrame(final Bubbles bubbles) {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var loginPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));

    var loginTxtBoxName = new SwingItem.Builder<>(JTextField::new)
        .size(160, 20)
        .build();
    var loginTxtBoxServiceName = new SwingItem.Builder<>(JTextField::new)
        .size(160, 20)
        .build();
    var loginTxtBoxPass = new SwingItem.Builder<>(JPasswordField::new)
        .size(160, 20)
        .build();

    var loginBtnProceed = new SwingItem.Builder<>(() -> new JButton("Login")).mouse(SwingItem.Mouse.CLICK, event -> {
      String username = loginTxtBoxName.component().getText();
      String serviceName = loginTxtBoxServiceName.component().getText();
      String password = new String(loginTxtBoxPass.component().getPassword());

      if (username.isEmpty() || password.isEmpty() || serviceName.isEmpty()) {
        JOptionPane.showMessageDialog(
            loginPanel.component(),
            "Fields are empty!",
            "Bubbles - Login error",
            JOptionPane.ERROR_MESSAGE
        );
        return;
      }

      bubbles.logger().info(
          "Attempting login for: {}@{} {}",
          username, serviceName, password
      );

      Thread.ofVirtual().start(() -> {
        try {
          var user = new ConnectedUser(bubbles, username, password, serviceName);
          user.initializeConnection();

          bubbles.logger().info(
              "Roster entries: {}",
              user.getRoster().getEntries().toString()
          );

          var frames = bubbles.frames();
          frames.login().window().hide();
          frames.chat().window().show();
          frames.roster().window().show();

          bubbles.events().call(new LoggedInEvent(user));
        } catch (Exception exception) {
          bubbles.logger().error("couldn't establish connection", exception);
        }
      });
    }).build();

    loginPanel
        .add(loginTxtBoxName)
        .add(loginTxtBoxServiceName)
        .add(loginTxtBoxPass)
        .add(loginBtnProceed);

    this.item = builder.add(loginPanel.build()).build();
  }

  @Override
  public @NotNull SwingItem<JFrame> get() {
    return this.item;
  }

  @Override
  public Swingify<JFrame> window() {
    return new Swingify<>(this.item, "Bubbles XMPP - Login", Themes.GRADIANTO_DEEP_OCEAN, item -> {
      if (JOptionPane.showConfirmDialog(
          item.component(),
          """
              Are you sure you want to close this window?
              """,
          "Bubbles - Login",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE
      ) != JOptionPane.YES_OPTION) return;
      System.exit(0);
    });
  }
}
