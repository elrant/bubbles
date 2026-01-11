package team.elrant.bubbles.frames;

import cc.lunary.swingify.SwingItem;
import net.miginfocom.swing.MigLayout;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.event.LoggedInEvent;

import javax.swing.*;
import java.awt.*;

public class RosterFrame {
  public static SwingItem<JFrame> ITEM;

  static {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var rosterPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));

    Bubbles.get().events().register(LoggedInEvent.class, event -> {
      for (var entry : event.user().getRoster().getEntries()) {
        rosterPanel.add(new SwingItem.Builder<>(() -> new JButton(entry.getName())).build());
      }
    });

    ITEM = builder.add(rosterPanel.build()).build();
  }
}
