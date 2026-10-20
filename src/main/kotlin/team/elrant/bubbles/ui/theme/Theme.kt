package team.elrant.bubbles.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import team.elrant.bubbles.model.Status

/** Catppuccin Mocha palette (https://catppuccin.com/palette). */
private object Mocha {
  val Mauve = Color(0xFFCBA6F7)
  val Lavender = Color(0xFFB4BEFE)
  val Pink = Color(0xFFF5C2E7)
  val Red = Color(0xFFF38BA8)
  val Yellow = Color(0xFFF9E2AF)
  val Green = Color(0xFFA6E3A1)
  val Text = Color(0xFFCDD6F4)
  val Subtext0 = Color(0xFFA6ADC8)
  val Overlay0 = Color(0xFF6C7086)
  val Surface1 = Color(0xFF45475A)
  val Surface0 = Color(0xFF313244)
  val Base = Color(0xFF1E1E2E)
  val Mantle = Color(0xFF181825)
  val Crust = Color(0xFF11111B)
}

/** Catppuccin Mocha with Mauve as the accent. */
private val MochaMauve = darkColorScheme(
  primary = Mocha.Mauve,
  onPrimary = Mocha.Crust,
  secondary = Mocha.Lavender,
  onSecondary = Mocha.Crust,
  tertiary = Mocha.Pink,
  onTertiary = Mocha.Crust,
  background = Mocha.Mantle,
  onBackground = Mocha.Text,
  surface = Mocha.Base,
  onSurface = Mocha.Text,
  surfaceVariant = Mocha.Surface0,
  onSurfaceVariant = Mocha.Subtext0,
  surfaceContainerLowest = Mocha.Crust,
  surfaceContainerLow = Mocha.Mantle,
  surfaceContainer = Mocha.Surface0,
  surfaceContainerHigh = Mocha.Surface0,
  surfaceContainerHighest = Mocha.Surface1,
  outline = Mocha.Overlay0,
  outlineVariant = Mocha.Surface1,
  error = Mocha.Red,
  onError = Mocha.Crust,
)

/** Presence colours used for status dots and display-picture frames. */
val Status.color: Color
  get() = when (this) {
    Status.Available -> Mocha.Green
    Status.Busy -> Mocha.Red
    Status.Away -> Mocha.Yellow
    Status.Offline -> Mocha.Overlay0
  }

@Composable
fun BubblesTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = MochaMauve, content = content)
}
