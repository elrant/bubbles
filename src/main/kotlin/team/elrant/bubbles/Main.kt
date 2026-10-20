package team.elrant.bubbles

import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import team.elrant.bubbles.ui.BubblesApp

fun main() = application {
  BubblesApp(remember { Bubbles() })
}
