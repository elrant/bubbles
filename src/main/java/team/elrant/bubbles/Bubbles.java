package team.elrant.bubbles;

import cc.lunary.tinyevents.TinyEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.elrant.bubbles.frames.FrameRegistry;

import java.util.concurrent.ConcurrentHashMap;

public final class Bubbles {
  private final Logger logger = LoggerFactory.getLogger(Bubbles.class);
  private final TinyEvents events = new TinyEvents(ConcurrentHashMap::new);
  private final FrameRegistry frames = new FrameRegistry(this);

  public void run() {
    this.frames.login().window().show();
  }

  public void shutdown() {
    logger.info("Shutting down Bubbles...");
    System.exit(0);
  }

  public Logger logger() {
    return this.logger;
  }

  public TinyEvents events() {
    return this.events;
  }

  public FrameRegistry frames() {
    return this.frames;
  }
}
