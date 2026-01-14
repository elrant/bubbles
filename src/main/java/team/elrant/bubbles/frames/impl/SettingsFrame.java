package team.elrant.bubbles.frames.impl;

import cc.lunary.swingify.SwingItem;
import cc.lunary.swingify.Swingify;
import cc.lunary.swingify.Themes;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.frames.AbstractFrame;

import javax.swing.*;
import java.awt.*;

public final class SettingsFrame extends AbstractFrame {
  private final SwingItem<JFrame> item;

  public SettingsFrame(final Bubbles bubbles) {
    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var chatPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));
    // TODO: ... | add stuffz here ~L

    this.item = builder.add(chatPanel.build()).build();
  }

  @Override
  public @NotNull SwingItem<JFrame> get() {
    return this.item;
  }

  @Override
  public @NotNull Swingify<JFrame> window() {
    return new Swingify<>(this.item, "Bubbles - Settings", Themes.GRADIANTO_DEEP_OCEAN, item -> {
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
