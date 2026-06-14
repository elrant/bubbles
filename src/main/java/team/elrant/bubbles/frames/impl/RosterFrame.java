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
    super(bubbles);
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var panel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));

    bubbles.events().register(LoggedInEvent.class, event -> {
      for (var entry : event.user().getRoster().getEntries()) {
        var jid = entry.getJid();
        panel.add(new SwingItem.Builder<>(() -> {
          var btn = new JButton(label(entry));
          btn.addActionListener(e -> {
            var chatFrame = bubbles.frames().chat();
            chatFrame.openChat(event.user(), jid);
            chatFrame.window().show();
          });
          return btn;
        }).build());
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
      item.component().dispose();
      bubbles.shutdown();
    });
  }
}
