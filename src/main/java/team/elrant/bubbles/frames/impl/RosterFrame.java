package team.elrant.bubbles.frames.impl;

import cc.lunary.swingify.SwingItem;
import cc.lunary.swingify.Swingify;
import cc.lunary.swingify.Themes;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smack.roster.RosterEntry;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.event.LoggedInEvent;
import team.elrant.bubbles.frames.AbstractFrame;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public final class RosterFrame extends AbstractFrame {
  private final SwingItem<JFrame> item;

  public RosterFrame(final Bubbles bubbles) {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var panel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));

    bubbles.events().register(LoggedInEvent.class, event -> {
      event.user().getRoster().getEntries().forEach(entry -> {
      });
      for (var entry : event.user().getRoster().getEntries()) {
        panel.add(new SwingItem.Builder<>(() -> new JButton(label(entry))).build());
      }
    });

    this.item = builder.add(panel.build()).build();
  }

  private static String label(RosterEntry entry) {
    return Objects.requireNonNullElse(
        entry.getName(),
        entry.getJid().toString()
    );
  }

  @Override
  public @NotNull SwingItem<JFrame> get() {
    return this.item;
  }

  @Override
  public Swingify<JFrame> window() {
    return new Swingify<>(this.item, "Bubbles - Roster", Themes.GRADIANTO_DEEP_OCEAN, item -> {
      if (JOptionPane.showConfirmDialog(
          item.component(),
          """
              Are you sure you want to close this window?
              """,
          "Bubbles",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
        return;
      System.exit(0);
    });
  }
}
