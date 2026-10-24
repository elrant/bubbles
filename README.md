# bubbles : in development

A desktop XMPP client, inspired by Windows Live Messenger 2009. It is written in Kotlin with
Compose Desktop (Material 3) and themed with Catppuccin.

## Requirements

- Currently only works on Linux. Secrets are kept in the keyring through the freedesktop.org Secret Service (GNOME Keyring, KeePassXC,
  KWallet's compatibility layer, ...), so bubbles must have access to the session bus. If the keyring is locked or
  unavailable, history and OMEMO keys are not saved.
- JetBrains Runtime SDK (JBRSDK) 25 that Gradle can detect, for both building and running.

## Build and run

```sh
./gradlew run                       # build and run on X11 / Xwayland (default)
./gradlew run --wayland             # run as a native Wayland window
./gradlew build                     # produces build/libs/bubbles-<version>.jar
./gradlew detekt                    # static analysis: detekt, ktlint and Compose rules
```

The jar is a self-contained shadow jar. Run it with JBR 25:

```sh
java -jar build/libs/bubbles-<version>.jar [--x11 | --wayland]
```

| Option      | Effect                                                                                     |
|-------------|--------------------------------------------------------------------------------------------|
| `--x11`     | X11 / Xwayland with GPU (OpenGL) rendering. Default.                                       |
| `--wayland` | Native Wayland window. Renders on the CPU, as Compose can't use the GPU on Wayland yet.    |
| `--help`    | Print usage.                                                                               |

Without `WAYLAND_DISPLAY`, `--wayland` falls back to X11.

## Files

| Path                                       | Contents                                  |
|--------------------------------------------|-------------------------------------------|
| `$XDG_CONFIG_HOME/bubbles/account.json`    | Remembered address (`~/.config` default)  |
| `$XDG_CONFIG_HOME/bubbles/settings.json`   | Preferences                               |
| `$XDG_CONFIG_HOME/bubbles/history/`        | Encrypted local message history           |
| `$XDG_CONFIG_HOME/bubbles/omemo/`          | OMEMO key material                        |
| `logs/latest.log`                          | Runtime log, also written to the console  |

## Project layout

Package `team.elrant.bubbles`:

- `Main.kt` parses arguments and picks the AWT toolkit before AWT starts.
- `Bubbles.kt` is the single app state holder, as Compose snapshot state owned by the Swing thread. Blocking
  network work runs on `Dispatchers.IO`.
- `xmpp/` wraps the Smack connection. Smack types stay here and in `Bubbles.kt`.
- `store/` and `secrets/` hold persisted data and the keyring access.
- `model/` has the plain types the UI uses.
- `ui/` has the screens (`signin`, `contacts`, `conversation`, `encryption`, `settings`), shared `components/`, and
  the `theme/`.

## Built with

Kotlin, Compose Desktop, [Smack](https://github.com/igniterealtime/Smack) 4.5 (with OMEMO), kotlinx.coroutines,
Jackson, SLF4J with tinylog, ZXing, and dbus-java.

## Contributing

Run `./gradlew detekt` and `./gradlew build` before opening a pull request. Style is four-space indent, 120 columns,
and KDoc on every declaration (see `.editorconfig` and `config/detekt/detekt.yml`).

## License

[GPL-3.0](LICENSE)
