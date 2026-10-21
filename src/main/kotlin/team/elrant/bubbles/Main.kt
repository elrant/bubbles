package team.elrant.bubbles

import org.slf4j.LoggerFactory
import sun.misc.Signal
import team.elrant.bubbles.ui.exitApplication
import team.elrant.bubbles.ui.showBubbles
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("team.elrant.bubbles.Main")

/** `--help` text, also printed after a usage error. */
private const val USAGE = """Usage: bubbles [--x11 | --wayland]

  --x11      Run through X11 / Xwayland with GPU (OpenGL) rendering. Default.
  --wayland  Run as a native Wayland window. Renders on the CPU, since Compose can't use
             the GPU on Wayland yet.
  --help     Show this help."""

/** Windowing system to run on; see [USAGE]. */
private enum class Backend { X11, Wayland }

/** Entry point: picks the windowing backend from [args], then opens the UI. */
fun main(args: Array<String>) {
  val backend = configureBackend(parseBackend(args))
  val bubbles = Bubbles()
  if (backend == Backend.Wayland) {
    // Ctrl+C / `kill` must take the same orderly exit as closing the window, or the Wayland
    // toolkit's shutdown hook deadlocks (see exitApplication).
    for (signal in listOf("INT", "TERM")) Signal.handle(Signal(signal)) { exitApplication(bubbles) }
  }
  SwingUtilities.invokeLater { showBubbles(bubbles) }
}

/**
 * Parses the command line into the requested [Backend], [Backend.X11] by default. Exits the
 * process for `--help` and for invalid arguments.
 */
private fun parseBackend(args: Array<String>): Backend {
  var backend: Backend? = null
  for (arg in args) {
    val choice = when (arg) {
      "--x11" -> Backend.X11
      "--wayland" -> Backend.Wayland
      "--help", "-h" -> printHelpAndExit()
      else -> usageError("unknown option '$arg'")
    }
    if (backend != null && backend != choice) usageError("--x11 and --wayland are mutually exclusive")
    backend = choice
  }
  return backend ?: Backend.X11
}

/** Prints the usage to stdout, then exits with status 0. */
private fun printHelpAndExit(): Nothing {
  println(USAGE)
  exitProcess(0)
}

/** Prints [message] and the usage to stderr, then exits with status 2. */
private fun usageError(message: String): Nothing {
  System.err.println("bubbles: $message\n\n$USAGE")
  exitProcess(2)
}

/**
 * Picks the AWT toolkit. Must run before AWT initializes. Assumes JetBrains Runtime, which the
 * build requires; stock OpenJDK has no `WLToolkit`.
 *
 * Wayland uses JBR's native `WLToolkit`. Skiko can't attach to a Wayland surface, so Compose
 * renders through Swing graphics (on the CPU) instead. JBR's launcher always sets
 * `awt.toolkit.name` itself, so it is overridden here rather than left to the command line.
 *
 * @return the backend actually used: Wayland falls back to X11 when `WAYLAND_DISPLAY` is unset.
 */
private fun configureBackend(requested: Backend): Backend {
  val backend = if (requested == Backend.Wayland && System.getenv("WAYLAND_DISPLAY") == null) {
    logger.warn("--wayland requested but WAYLAND_DISPLAY is not set; falling back to X11")
    Backend.X11
  } else {
    requested
  }

  when (backend) {
    Backend.X11 -> {
      System.setProperty("awt.toolkit.name", "XToolkit")
      logger.info("Using X11 backend")
    }

    Backend.Wayland -> {
      System.setProperty("awt.toolkit.name", "WLToolkit")
      System.setProperty("compose.swing.render.on.graphics", "true")
      logger.info("Using native Wayland backend (CPU rendering)")
    }
  }
  return backend
}
