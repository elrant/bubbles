package team.elrant.bubbles.frames.impl;


import cc.lunary.swingify.SwingItem;
import cc.lunary.swingify.Swingify;
import cc.lunary.swingify.Themes;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jxmpp.jid.BareJid;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.frames.AbstractFrame;
import team.elrant.bubbles.xmpp.ConnectedUser;

import javax.swing.*;
import java.awt.*;

public final class ChatFrame extends AbstractFrame {
  private final SwingItem<JFrame> item;
  private final JTextArea historyArea;
  private final JTextField messageBox;

  private @Nullable ConnectedUser activeUser;
  private @Nullable BareJid activeContact;

  public ChatFrame(final Bubbles bubbles) {
    super(bubbles);
    historyArea = new JTextArea();
    historyArea.setEditable(false);
    historyArea.setLineWrap(true);
    historyArea.setWrapStyleWord(true);

    messageBox = new JTextField();

    var builder = new SwingItem.Builder<>(JFrame::new)
        .layout(new BorderLayout())
        .sizeDefault()
        .locationDefault()
        .resizable(true);

    var chatPanel = new SwingItem.Builder<>(() -> new JPanel(new MigLayout("fill, insets 8", "[grow,fill]", "[grow,fill][]")));

    var scrollPane = new SwingItem.Builder<>(() -> new JScrollPane(historyArea)).build();
    var msgBoxItem = new SwingItem.Builder<>(() -> messageBox).build();

    var sendBtn = new SwingItem.Builder<>(() -> {
      var btn = new JButton("Send");
      btn.addActionListener(e -> sendCurrentMessage());
      return btn;
    }).build();

    messageBox.addActionListener(e -> sendCurrentMessage());

    chatPanel
        .add(scrollPane, "span, grow, wrap")
        .add(msgBoxItem, "growx")
        .add(sendBtn);

    this.item = builder.add(chatPanel.build()).build();
  }

  /**
   * Opens a chat with the given contact for the given user.
   * Registers an incoming message listener and updates the window title.
   */
  public void openChat(@NotNull ConnectedUser user, @NotNull BareJid contact) {
    this.activeUser = user;
    this.activeContact = contact;
    SwingUtilities.invokeLater(() -> {
      historyArea.setText("");
      item.component().setTitle("Bubbles - " + contact);
    });
    user.addIncomingMessageListener(contact, msg ->
        SwingUtilities.invokeLater(() -> appendMessage(contact.toString(), msg))
    );
  }

  private void sendCurrentMessage() {
    if (activeUser == null || activeContact == null) return;
    String text = messageBox.getText().trim();
    if (text.isEmpty()) return;
    activeUser.sendMessage(activeContact, text);
    appendMessage(activeUser.getUsername(), text);
    messageBox.setText("");
  }

  private void appendMessage(@NotNull String sender, @NotNull String body) {
    historyArea.append("[" + sender + "]: " + body + "\n");
    historyArea.setCaretPosition(historyArea.getDocument().getLength());
  }

  @Override
  public @NotNull SwingItem<JFrame> get() {
    return this.item;
  }

  @Override
  public @NotNull Swingify<JFrame> window() {
    return new Swingify<>(this.item, "Bubbles - Chat", Themes.GRADIANTO_DEEP_OCEAN, item -> {
      if (JOptionPane.showConfirmDialog(
          item.component(),
          "Are you sure you want to close this window?",
          "Bubbles",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
        return;
      item.component().dispose();
      bubbles.shutdown();
    });
  }
}
