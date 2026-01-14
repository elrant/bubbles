package team.elrant.bubbles.frames;

import cc.lunary.swingify.SwingItem;
import net.miginfocom.swing.MigLayout;
import org.jivesoftware.smack.roster.RosterEntry;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.event.LoggedInEvent;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class RosterFrame {
  public static SwingItem<JFrame> ITEM;


  private static String rosterLabel(RosterEntry entry) {
    return Objects.requireNonNullElse(
        entry.getName(),
        entry.getJid().toString()
    );
  }

  static {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var rosterPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));

    Bubbles.get().events().register(LoggedInEvent.class, event -> {
      for (var entry : event.user().getRoster().getEntries()) {
        if (entry.getName() != null) {
          rosterPanel.add(new SwingItem.Builder<>(() -> new JButton(rosterLabel(entry))).build()
          );
        }
      }

      ITEM = builder.add(rosterPanel.build()).build();
    });

  }
}