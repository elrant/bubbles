package team.elrant.bubbles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.elrant.bubbles.model.Status
import team.elrant.bubbles.ui.theme.color

/** Messenger-style display picture: initials inside a frame coloured by presence. */
@Composable
fun DisplayPicture(name: String, status: Status, size: Dp, modifier: Modifier = Modifier) {
  Box(
    modifier
      .size(size)
      .clip(RoundedCornerShape(size * 0.2f))
      .background(status.color)
      .padding(size * 0.07f),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(size * 0.14f))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        initials(name),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = (size.value * 0.34f).sp,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
fun StatusDot(status: Status, modifier: Modifier = Modifier, size: Dp = 10.dp) {
  Box(modifier.size(size).clip(CircleShape).background(status.color))
}

private fun initials(name: String): String =
  name.substringBefore('@')
    .split(' ', '.', '_', '-')
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { "?" }
