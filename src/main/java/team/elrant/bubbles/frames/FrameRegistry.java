package team.elrant.bubbles.frames;

import cc.lunary.swingify.Swingify;
import team.elrant.bubbles.Bubbles;
import team.elrant.bubbles.frames.impl.ChatFrame;
import team.elrant.bubbles.frames.impl.LoginFrame;
import team.elrant.bubbles.frames.impl.RosterFrame;
import team.elrant.bubbles.frames.impl.SettingsFrame;

public final class FrameRegistry {
  static {
    Swingify.init();
  }

  private final ChatFrame frameChat;
  private final LoginFrame frameLogin;
  private final RosterFrame frameRoster;
  private final SettingsFrame frameSettings;
  private final AbstractFrame[] frames;

  public FrameRegistry(final Bubbles bubbles) {
    this.frameChat = new ChatFrame(bubbles);
    this.frameLogin = new LoginFrame(bubbles);
    this.frameRoster = new RosterFrame(bubbles);
    this.frameSettings = new SettingsFrame(bubbles);
    this.frames = new AbstractFrame[]{
        this.frameLogin,
        this.frameRoster,
        this.frameChat,
        this.frameSettings
    };
  }

  public LoginFrame login() {
    return this.frameLogin;
  }

  public RosterFrame roster() {
    return this.frameRoster;
  }

  public ChatFrame chat() {
    return this.frameChat;
  }

  public SettingsFrame settings() {
    return this.frameSettings;
  }

  public AbstractFrame[] all() {
    return this.frames;
  }
}
