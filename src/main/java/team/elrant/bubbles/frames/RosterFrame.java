package team.elrant.bubbles.frames;

import cc.lunary.swingify.SwingItem;
import net.miginfocom.swing.MigLayout;
import team.elrant.bubbles.Bubbles;

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
    

    var rosterPanel =  new SwingItem.Builder<>(() -> new JPanel(new MigLayout()));

    if (Bubbles.roster != null) {
      for(var entry : Bubbles.roster.getEntries()) {
        rosterPanel.add(new SwingItem.Builder<>(() -> new JButton(entry.getName())).build());
      }
    } 
    ITEM = builder.add(rosterPanel.build()).build();
    
  }
}
