package team.elrant.bubbles.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import team.elrant.bubbles.model.Accent
import team.elrant.bubbles.model.Flavor
import team.elrant.bubbles.model.Status

/** The colours of one Catppuccin flavor (https://catppuccin.com/palette) that the app uses. */
private data class Palette(
  val rosewater: Color,
  val flamingo: Color,
  val pink: Color,
  val mauve: Color,
  val red: Color,
  val maroon: Color,
  val peach: Color,
  val yellow: Color,
  val green: Color,
  val teal: Color,
  val sky: Color,
  val sapphire: Color,
  val blue: Color,
  val lavender: Color,
  val text: Color,
  val subtext0: Color,
  val overlay0: Color,
  val surface1: Color,
  val surface0: Color,
  val base: Color,
  val mantle: Color,
  val crust: Color,
) {
  /** This flavor's shade of each [Accent]. */
  val accents: Map<Accent, Color> = mapOf(
    Accent.Rosewater to rosewater,
    Accent.Flamingo to flamingo,
    Accent.Pink to pink,
    Accent.Mauve to mauve,
    Accent.Red to red,
    Accent.Maroon to maroon,
    Accent.Peach to peach,
    Accent.Yellow to yellow,
    Accent.Green to green,
    Accent.Teal to teal,
    Accent.Sky to sky,
    Accent.Sapphire to sapphire,
    Accent.Blue to blue,
    Accent.Lavender to lavender,
  )
}

private val Latte = Palette(
  rosewater = Color(0xFFDC8A78), flamingo = Color(0xFFDD7878), pink = Color(0xFFEA76CB),
  mauve = Color(0xFF8839EF), red = Color(0xFFD20F39), maroon = Color(0xFFE64553),
  peach = Color(0xFFFE640B), yellow = Color(0xFFDF8E1D), green = Color(0xFF40A02B),
  teal = Color(0xFF179299), sky = Color(0xFF04A5E5), sapphire = Color(0xFF209FB5),
  blue = Color(0xFF1E66F5), lavender = Color(0xFF7287FD),
  text = Color(0xFF4C4F69), subtext0 = Color(0xFF6C6F85), overlay0 = Color(0xFF9CA0B0),
  surface1 = Color(0xFFBCC0CC), surface0 = Color(0xFFCCD0DA),
  base = Color(0xFFEFF1F5), mantle = Color(0xFFE6E9EF), crust = Color(0xFFDCE0E8),
)

private val Frappe = Palette(
  rosewater = Color(0xFFF2D5CF), flamingo = Color(0xFFEEBEBE), pink = Color(0xFFF4B8E4),
  mauve = Color(0xFFCA9EE6), red = Color(0xFFE78284), maroon = Color(0xFFEA999C),
  peach = Color(0xFFEF9F76), yellow = Color(0xFFE5C890), green = Color(0xFFA6D189),
  teal = Color(0xFF81C8BE), sky = Color(0xFF99D1DB), sapphire = Color(0xFF85C1DC),
  blue = Color(0xFF8CAAEE), lavender = Color(0xFFBABBF1),
  text = Color(0xFFC6D0F5), subtext0 = Color(0xFFA5ADCE), overlay0 = Color(0xFF737994),
  surface1 = Color(0xFF51576D), surface0 = Color(0xFF414559),
  base = Color(0xFF303446), mantle = Color(0xFF292C3C), crust = Color(0xFF232634),
)

private val Macchiato = Palette(
  rosewater = Color(0xFFF4DBD6), flamingo = Color(0xFFF0C6C6), pink = Color(0xFFF5BDE6),
  mauve = Color(0xFFC6A0F6), red = Color(0xFFED8796), maroon = Color(0xFFEE99A0),
  peach = Color(0xFFF5A97F), yellow = Color(0xFFEED49F), green = Color(0xFFA6DA95),
  teal = Color(0xFF8BD5CA), sky = Color(0xFF91D7E3), sapphire = Color(0xFF7DC4E4),
  blue = Color(0xFF8AADF4), lavender = Color(0xFFB7BDF8),
  text = Color(0xFFCAD3F5), subtext0 = Color(0xFFA5ADCB), overlay0 = Color(0xFF6E738D),
  surface1 = Color(0xFF494D64), surface0 = Color(0xFF363A4F),
  base = Color(0xFF24273A), mantle = Color(0xFF1E2030), crust = Color(0xFF181926),
)

private val Mocha = Palette(
  rosewater = Color(0xFFF5E0DC), flamingo = Color(0xFFF2CDCD), pink = Color(0xFFF5C2E7),
  mauve = Color(0xFFCBA6F7), red = Color(0xFFF38BA8), maroon = Color(0xFFEBA0AC),
  peach = Color(0xFFFAB387), yellow = Color(0xFFF9E2AF), green = Color(0xFFA6E3A1),
  teal = Color(0xFF94E2D5), sky = Color(0xFF89DCEB), sapphire = Color(0xFF74C7EC),
  blue = Color(0xFF89B4FA), lavender = Color(0xFFB4BEFE),
  text = Color(0xFFCDD6F4), subtext0 = Color(0xFFA6ADC8), overlay0 = Color(0xFF6C7086),
  surface1 = Color(0xFF45475A), surface0 = Color(0xFF313244),
  base = Color(0xFF1E1E2E), mantle = Color(0xFF181825), crust = Color(0xFF11111B),
)

/** The palette of [flavor]. */
private fun palette(flavor: Flavor): Palette = when (flavor) {
  Flavor.Latte -> Latte
  Flavor.Frappe -> Frappe
  Flavor.Macchiato -> Macchiato
  Flavor.Mocha -> Mocha
}

/** The palette whose base colour is the [ColorScheme.surface] of [scheme]; Mocha if none matches. */
private fun palette(scheme: ColorScheme): Palette =
  Flavor.entries.map(::palette).firstOrNull { it.base == scheme.surface } ?: Mocha

/** [flavor] with [accent] as the primary colour; text on accents uses the flavor's base or crust for contrast. */
private fun colorScheme(flavor: Flavor, accent: Accent): ColorScheme {
  val p = palette(flavor)
  val onAccent = if (flavor.dark) p.crust else p.base
  val scheme = if (flavor.dark) darkColorScheme() else lightColorScheme()
  return scheme.copy(
    primary = p.accents.getValue(accent),
    onPrimary = onAccent,
    primaryContainer = p.surface1,
    onPrimaryContainer = p.text,
    secondary = p.lavender,
    onSecondary = onAccent,
    tertiary = p.pink,
    onTertiary = onAccent,
    background = p.mantle,
    onBackground = p.text,
    surface = p.base,
    onSurface = p.text,
    surfaceVariant = p.surface0,
    onSurfaceVariant = p.subtext0,
    surfaceContainerLowest = p.crust,
    surfaceContainerLow = p.mantle,
    surfaceContainer = p.surface0,
    surfaceContainerHigh = p.surface0,
    surfaceContainerHighest = p.surface1,
    outline = p.overlay0,
    outlineVariant = p.surface1,
    error = p.red,
    onError = onAccent,
  )
}

/** The swatch colour of [accent] in [flavor], for previews in the settings. */
fun accentColor(flavor: Flavor, accent: Accent): Color = palette(flavor).accents.getValue(accent)

/**
 * Presence colours used for status dots and display-picture frames, in the current theme's flavor.
 *
 * The palette is found from the theme's surface colour rather than a composition local, which the Compose
 * rules discourage.
 */
val Status.color: Color
  @Composable
  @ReadOnlyComposable
  get() {
    val p = palette(MaterialTheme.colorScheme)
    return when (this) {
      Status.Available -> p.green
      Status.Busy -> p.red
      Status.Away -> p.yellow
      Status.Offline -> p.overlay0
    }
  }

/**
 * The app's Material theme: Catppuccin [flavor] with [accent] as the primary colour. Wrap every composition
 * root in it.
 */
@Composable
fun BubblesTheme(flavor: Flavor, accent: Accent, content: @Composable () -> Unit) {
  val scheme = remember(flavor, accent) { colorScheme(flavor, accent) }
  MaterialTheme(colorScheme = scheme, content = content)
}
