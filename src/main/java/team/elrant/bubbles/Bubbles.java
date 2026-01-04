package team.elrant.bubbles;

import cc.lunary.swingify.Swingify;
import cc.lunary.swingify.Themes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public final class Bubbles {
  private static final Bubbles INSTANCE = new Bubbles();
  private final Logger logger = LoggerFactory.getLogger(Bubbles.class);

  public static Bubbles get() {
    return INSTANCE;
  }
  
  public void run() {
    Swingify.init();

    new Swingify<>(loginFrame, "Bubbles XMPP", Themes.GRADIANTO_DEEP_OCEAN, item -> {
      if (JOptionPane.showConfirmDialog(
          item.component(),
          """
              Are you sure you want to close this window?
              """,
          "~ Bubbles",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE
      ) != JOptionPane.YES_OPTION) return;
      System.exit(0);
    }).show();
  }

  public Logger logger() {
    return this.logger;
  }
}
