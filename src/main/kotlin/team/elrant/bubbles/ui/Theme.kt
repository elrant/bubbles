package team.elrant.bubbles.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Approximation of the Gradianto Deep Ocean look used by the old Swing UI. */
private val DeepOcean = darkColorScheme(
  primary = Color(0xFF5CB8E6),
  onPrimary = Color(0xFF00212F),
  background = Color(0xFF1B2433),
  onBackground = Color(0xFFD8DEE9),
  surface = Color(0xFF222C3E),
  onSurface = Color(0xFFD8DEE9),
  surfaceVariant = Color(0xFF2B374D),
  onSurfaceVariant = Color(0xFFB4BDCC),
  error = Color(0xFFFF6B6B),
)

@Composable
fun BubblesTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = DeepOcean, content = content)
}
