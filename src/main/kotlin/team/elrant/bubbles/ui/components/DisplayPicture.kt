package team.elrant.bubbles.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.elrant.bubbles.model.Status
import team.elrant.bubbles.ui.theme.color

// Proportions of a display picture, relative to its outer edge length.
private const val FRAME_CORNER = 0.2f
private const val FRAME_WIDTH = 0.07f
private const val PICTURE_CORNER = 0.14f
private const val INITIALS_SIZE = 0.34f

/**
 * Messenger-style display picture: [picture], or initials if there is none, inside a frame
 * coloured by presence.
 *
 * @param name name or address to take the initials from; also the picture's description.
 * @param status presence that colours the frame.
 * @param size outer edge length; the frame and text scale with it.
 * @param modifier applied to the outer frame.
 * @param picture avatar image, cropped to fill the frame.
 */
@Composable
fun displayPicture(
  name: String,
  status: Status,
  size: Dp,
  modifier: Modifier = Modifier,
  picture: ImageBitmap? = null,
) {
  Box(
    modifier
      .size(size)
      .clip(RoundedCornerShape(size * FRAME_CORNER))
      .background(status.color)
      .padding(size * FRAME_WIDTH),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(size * PICTURE_CORNER))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      if (picture != null) {
        Image(picture, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      } else {
        Text(
          initials(name),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = (size.value * INITIALS_SIZE).sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

/** Small circle in the presence colour of [status]. */
@Composable
fun statusDot(status: Status, modifier: Modifier = Modifier, size: Dp = 10.dp) {
  Box(modifier.size(size).clip(CircleShape).background(status.color))
}

/** Up to two uppercase initials from the words of [name] (ignoring any `@domain`), or `?`. */
private fun initials(name: String): String = name.substringBefore('@')
  .split(' ', '.', '_', '-')
  .filter { it.isNotBlank() }
  .take(2)
  .joinToString("") { it.first().uppercase() }
  .ifEmpty { "?" }
