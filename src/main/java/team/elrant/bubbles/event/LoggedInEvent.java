package team.elrant.bubbles.event;

import cc.lunary.tinyevents.AbstractEvent;
import team.elrant.bubbles.xmpp.ConnectedUser;

import java.util.Objects;

public class LoggedInEvent extends AbstractEvent {
  private final ConnectedUser user;

  public LoggedInEvent(final ConnectedUser user) {
    this.user = user;
  }

  public ConnectedUser user() {
    return this.user;
  }

  public @Override int hashCode() {
    return Objects.hash(this.user);
  }

  public @Override boolean equals(Object object) {
    return object instanceof LoggedInEvent event && this.equals(event);
  }

  public <E extends LoggedInEvent> boolean equals(E event) {
    return event.hashCode() == this.hashCode() && event.user() == this.user();
  }

  public @Override String toString() {
    return "LoggedInEvent{" +
        "user=" + user +
        '}';
  }
}
