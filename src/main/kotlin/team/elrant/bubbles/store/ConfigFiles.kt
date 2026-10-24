package team.elrant.bubbles.store

import java.nio.file.Path

/** [name] in the app's directory under the XDG config directory, `~/.config` if `XDG_CONFIG_HOME` is unset. */
internal fun configFile(name: String): Path {
    val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".config")
    return configHome.resolve("bubbles").resolve(name)
}
