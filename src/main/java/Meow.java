import cc.lunary.lib_swingify.SwingItem;
import cc.lunary.lib_swingify.Swingify;
import cc.lunary.lib_swingify.Themes;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public final class Meow {
  public static void main(String[] args) {
    Swingify.init();

    var settingsFrame = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault(0.3D)
        .locationDefault()
        .resizable(true)
        .add(new SwingItem.List.Builder<>(() -> new JList<String>())
            .items(Arrays.stream(Themes.values()).map(Themes::getFriendlyName).toArray(String[]::new))
            .select((list, event) -> {
              var theme = Themes.byName(list.getSelectedValue(), false);
              if (theme == null) return;
              Themes.select(theme, true);
            })
            .scroll(SwingItem.List.Scroll.VERTICAL)
            .build())
        .build();

    var mainFrame = new SwingItem.Builder<>(JFrame::new)
        .layout(new GridLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true)
        .bar(new SwingItem.Builder<>(JMenuBar::new)
            .add(new SwingItem.Builder<>(() -> new JButton("Settings"))
                .background(new Color(0, 0, 0, 0))
                .paintBorder(false)
                .mouse(SwingItem.Mouse.RELEASE, settingsFrame::toggle)
                .build())
            .add(new SwingItem.Builder<>(() -> new JLabel("~ Chatty")).build())
            .add(new SwingItem.Builder<>(() -> new JButton("Connect")).build())
            .build())
        .add(new SwingItem.Builder<>(JPanel::new).build())
        .build();

    new Swingify<>(mainFrame, "Chatty", Themes.VUESION, item -> {
      if (JOptionPane.showConfirmDialog(
          item.component(),
          """
              Are you sure you want to close this window?
              ALL connections will be lost!
              """,
          "~ Chatty",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE
      ) != JOptionPane.YES_OPTION) return;
      System.exit(0);
    }).show();
  }
}
