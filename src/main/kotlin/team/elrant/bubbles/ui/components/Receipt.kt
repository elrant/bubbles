package team.elrant.bubbles.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** How far an outgoing message has got, as shown by [receiptIcon]. */
enum class Receipt {
  /** Still being sent: a hollow circle. */
  Sending,

  /** Handed to the server: one check. */
  Sent,

  /** The contact has read it: two checks in the accent colour. */
  Read,

  /** Could not be sent: a cross. */
  Failed,
}

private val ICON_HEIGHT = 12.dp
private val SINGLE_WIDTH = 12.dp
private val DOUBLE_WIDTH = 18.dp
private val SECOND_CHECK_SHIFT = 6.dp
private val STROKE_WIDTH = 1.6.dp

/** Delivery state of an outgoing message as a small drawn icon (circle, check, double check or cross). */
@Composable
fun receiptIcon(state: Receipt, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val color = when (state) {
    Receipt.Read -> colors.primary
    Receipt.Failed -> colors.error
    Receipt.Sending, Receipt.Sent -> colors.onSurfaceVariant
  }
  Canvas(modifier.width(if (state == Receipt.Read) DOUBLE_WIDTH else SINGLE_WIDTH).height(ICON_HEIGHT)) {
    val stroke = STROKE_WIDTH.toPx()
    when (state) {
      Receipt.Sending -> {
        drawCircle(color, radius = size.height / 2 - stroke, style = Stroke(stroke))
      }

      Receipt.Sent -> {
        check(color, stroke, 0f)
      }

      Receipt.Read -> {
        check(color, stroke, 0f)
        check(color, stroke, SECOND_CHECK_SHIFT.toPx())
      }

      Receipt.Failed -> {
        cross(color, stroke)
      }
    }
  }
}

/** Draws one check mark, shifted right by [offset] pixels. */
private fun DrawScope.check(color: Color, stroke: Float, offset: Float) {
  val h = size.height
  val tip = Offset(offset + h * CHECK_TIP_X, h * CHECK_TIP_Y)
  drawLine(color, Offset(offset + h * CHECK_START_X, h * CHECK_START_Y), tip, stroke, StrokeCap.Round)
  drawLine(color, tip, Offset(offset + h * CHECK_END_X, h * CHECK_END_Y), stroke, StrokeCap.Round)
}

/** Draws a cross filling the icon. */
private fun DrawScope.cross(color: Color, stroke: Float) {
  val inset = stroke
  val far = size.height - inset
  drawLine(color, Offset(inset, inset), Offset(far, far), stroke, StrokeCap.Round)
  drawLine(color, Offset(far, inset), Offset(inset, far), stroke, StrokeCap.Round)
}

// Points of a check mark, as fractions of the icon's height.
private const val CHECK_START_X = 0.08f
private const val CHECK_START_Y = 0.55f
private const val CHECK_TIP_X = 0.4f
private const val CHECK_TIP_Y = 0.85f
private const val CHECK_END_X = 0.95f
private const val CHECK_END_Y = 0.15f
