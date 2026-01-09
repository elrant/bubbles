package team.elrant.bubbles.frames;


import cc.lunary.swingify.SwingItem;
import net.miginfocom.swing.MigLayout;
import team.elrant.bubbles.xmpp.ConnectedUser;

import javax.swing.*;
import java.awt.*;

public final class ChatFrame {
  public static SwingItem<JFrame> ITEM;
  public static ConnectedUser ActiveUser = null;
  static {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var chatPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));
    var chatMsgBox = new SwingItem.Builder<>(JTextField::new).size(160, 20).build();

    chatPanel.add(chatMsgBox);

    ITEM = builder.add(chatPanel.build()).build();
  }

  private ChatFrame() {
  }
}