package team.elrant.bubbles;

import cc.lunary.swingify.Swingify;
import cc.lunary.swingify.Themes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.elrant.bubbles.frames.ChatFrame;
import team.elrant.bubbles.frames.LoginFrame;

import javax.swing.*;

public final class Bubbles {
  private static final Bubbles INSTANCE = new Bubbles();
  private final Logger logger = LoggerFactory.getLogger(Bubbles.class);

  public static Bubbles get() {
    return INSTANCE;
  }

  public void run() {
    Swingify.init();
    
    var loginWindow = new Swingify<>(LoginFrame.ITEM, "Bubbles XMPP ~ Login", Themes.GRADIANTO_DEEP_OCEAN, item -> {
      if (JOptionPane.showConfirmDialog(
          item.component(),
          """
              Are you sure you want to close this window?
              """,
          "~ Bubbles | Login",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
        return;
      System.exit(0);
    });
    
    var chatWindow = new Swingify<>(ChatFrame.ITEM, "Bubbles XMPP ~ Chat", Themes.GRADIANTO_DEEP_OCEAN, item -> {
      if (JOptionPane.showConfirmDialog(
          item.component(),
          """
              Are you sure you want to close this window?
              """,
          "~ Bubbles | Chat",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
        return;
      System.exit(0);
    }); 

    loginWindow.show();
    chatWindow.show();
  }

  public Logger logger() {
    return this.logger;
  }
}
