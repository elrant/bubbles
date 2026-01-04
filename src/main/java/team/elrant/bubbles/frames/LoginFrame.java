package team.elrant.bubbles.frames;

import cc.lunary.swingify.SwingItem;
import net.miginfocom.swing.MigLayout;
import team.elrant.bubbles.Bubbles;

import javax.swing.*;
import java.awt.*;

public final class LoginFrame {
  public static SwingItem<JFrame> ITEM;

  static {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var loginPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));
    var loginTxtBoxName = new SwingItem.Builder<>(JTextField::new).size(160, 20).build();
    var loginTxtBoxPass = new SwingItem.Builder<>(JTextField::new).size(160, 20).build();
    var loginBtnProceed = new SwingItem.Builder<>(() -> new JButton("Login"))
        .mouse(SwingItem.Mouse.CLICK, event -> {
          Bubbles.get().logger().info(
              "click! :3 {} {}",
              loginTxtBoxName.component().getText(),
              loginTxtBoxPass.component().getText()
          );
        }).build();
    loginPanel.add(loginTxtBoxName).add(loginTxtBoxPass, "wrap").add(loginBtnProceed);

    ITEM = builder.add(loginPanel.build()).build();
  }

  private LoginFrame() {
  }
}
