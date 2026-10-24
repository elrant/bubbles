package team.elrant.bubbles.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("team.elrant.bubbles.ui.components.QrCode")

/** Empty modules around the code; scanners need a light border. */
private const val QUIET_ZONE = 2

/** The default side of a [qrCode]. */
private val DEFAULT_SIZE = 200.dp

/**
 * A QR code for [content]. It is always black on white, whatever the theme, since scanners expect that. Shows a short
 * note instead if [content] can't be encoded.
 *
 * @param content text to encode.
 * @param modifier applied to the code.
 * @param size side length of the square code.
 */
@Composable
fun qrCode(content: String, modifier: Modifier = Modifier, size: Dp = DEFAULT_SIZE) {
    val matrix = remember(content) { encode(content) }
    if (matrix == null) {
        Text("Could not make a QR code.", modifier, style = MaterialTheme.typography.labelMedium)
        return
    }
    Canvas(modifier.size(size)) {
        val modules = matrix.width + 2 * QUIET_ZONE
        val module = this.size.width / modules
        drawRect(Color.White)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) {
                    drawRect(
                        Color.Black,
                        Offset((x + QUIET_ZONE) * module, (y + QUIET_ZONE) * module),
                        Size(module, module),
                    )
                }
            }
        }
    }
}

/** [content] as a QR code matrix without its own border; `null` if it can't be encoded. */
private fun encode(content: String): BitMatrix? = try {
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
} catch (e: WriterException) {
    logger.warn("Could not encode a QR code: {}", e.message)
    null
}
