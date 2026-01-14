package team.elrant.bubbles.frames;

import cc.lunary.swingify.SwingItem;
import cc.lunary.swingify.Swingify;

import javax.swing.*;

public abstract class AbstractFrame {
  public abstract SwingItem<JFrame> get();
  public abstract Swingify<JFrame> window();
}
