package team.elrant.bubbles.frames;

import cc.lunary.swingify.SwingItem;
import cc.lunary.swingify.Swingify;
import team.elrant.bubbles.Bubbles;

import javax.swing.*;

public abstract class AbstractFrame {
  protected final Bubbles bubbles;

  protected AbstractFrame(Bubbles bubbles) {
    this.bubbles = bubbles;
  }

  public abstract SwingItem<JFrame> get();
  public abstract Swingify<JFrame> window();
}
