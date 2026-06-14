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
import java.awt.event.ActionListener;


public final class LoginFrame extends AbstractFrame {
  private final SwingItem<JFrame> item;

  public LoginFrame(final Bubbles bubbles) {
    super(bubbles);
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var loginPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout("wrap 2", "[right]rel[grow,fill]")));

    var loginTxtBoxName = new SwingItem.Builder<>(JTextField::new)
        .size(160, 20)
        .build();
    var loginTxtBoxServiceName = new SwingItem.Builder<>(JTextField::new)
        .size(160, 20)
        .build();
    var loginTxtBoxPass = new SwingItem.Builder<>(JPasswordField::new)
        .size(160, 20)
        .build();

    ActionListener doLogin = event -> {
      String username = loginTxtBoxName.component().getText();
      String serviceName = loginTxtBoxServiceName.component().getText();
      String password = new String(loginTxtBoxPass.component().getPassword());

      if (username.isEmpty() || password.isEmpty() || serviceName.isEmpty()) {
        JOptionPane.showMessageDialog(
            loginPanel.component(),
            "All fields are required.",
            "Bubbles - Login error",
            JOptionPane.ERROR_MESSAGE
        );
        return;
      }

      bubbles.logger().info("Attempting login for: {}@{}", username, serviceName);

      Thread.ofVirtual().name("Login").start(() -> {
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
          SwingUtilities.invokeLater(() ->
              JOptionPane.showMessageDialog(
                  loginPanel.component(),
                  "Could not connect: " + exception.getMessage(),
                  "Bubbles - Login error",
                  JOptionPane.ERROR_MESSAGE
              )
          );
        }
      });
    };

    loginTxtBoxName.component().addActionListener(doLogin);
    loginTxtBoxServiceName.component().addActionListener(doLogin);
    loginTxtBoxPass.component().addActionListener(doLogin);

    var loginBtnProceed = new SwingItem.Builder<>(() -> {
      var btn = new JButton("Login");
      btn.addActionListener(doLogin);
      return btn;
    }).build();

    loginPanel
        .add(new SwingItem.Builder<>(() -> new JLabel("Username:")).build())
        .add(loginTxtBoxName)
        .add(new SwingItem.Builder<>(() -> new JLabel("Server:")).build())
        .add(loginTxtBoxServiceName)
        .add(new SwingItem.Builder<>(() -> new JLabel("Password:")).build())
        .add(loginTxtBoxPass)
        .add(new SwingItem.Builder<>(() -> new JLabel("")).build())
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
          "Are you sure you want to close this window?",
          "Bubbles - Login",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE
      ) != JOptionPane.YES_OPTION) return;
      item.component().dispose();
      bubbles.shutdown();
    });
  }
}
