package app.proxsh.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { Phone, Laptop, File, Text, Check, Close, Folder, Arrow, Tray, Link }

/** Icons drawn by hand in one line weight, so nothing comes from an icon library. */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        draw(glyph, color, stroke, u)
    }
}

private fun DrawScope.draw(glyph: Glyph, color: Color, stroke: Stroke, u: Float) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)

    when (glyph) {
        Glyph.Phone -> {
            drawRoundRect(color, p(7f, 3f), Size(10f * u, 18f * u), androidx.compose.ui.geometry.CornerRadius(2.6f * u), style = stroke)
            line(10.5f, 18f, 13.5f, 18f)
        }
        Glyph.Laptop -> {
            drawRoundRect(color, p(4.5f, 5f), Size(15f * u, 10f * u), androidx.compose.ui.geometry.CornerRadius(1.8f * u), style = stroke)
            line(2.5f, 19f, 21.5f, 19f)
        }
        Glyph.File -> {
            val path = Path().apply {
                moveTo(6f * u, 3f * u); lineTo(14f * u, 3f * u); lineTo(19f * u, 8f * u)
                lineTo(19f * u, 20f * u); lineTo(6f * u, 20f * u); close()
                moveTo(14f * u, 3f * u); lineTo(14f * u, 8f * u); lineTo(19f * u, 8f * u)
            }
            drawPath(path, color, style = stroke)
        }
        Glyph.Text -> {
            line(5f, 7f, 19f, 7f); line(5f, 12f, 19f, 12f); line(5f, 17f, 13f, 17f)
        }
        Glyph.Check -> {
            val path = Path().apply { moveTo(5f * u, 12.5f * u); lineTo(10f * u, 17.5f * u); lineTo(19f * u, 7f * u) }
            drawPath(path, color, style = Stroke(width = 2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Glyph.Close -> {
            line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f)
        }
        Glyph.Folder -> {
            val path = Path().apply {
                moveTo(3f * u, 7f * u); lineTo(9f * u, 7f * u); lineTo(11f * u, 9.5f * u); lineTo(21f * u, 9.5f * u)
                lineTo(21f * u, 19f * u); lineTo(3f * u, 19f * u); close()
            }
            drawPath(path, color, style = stroke)
        }
        Glyph.Arrow -> {
            line(5f, 12f, 19f, 12f)
            val head = Path().apply { moveTo(13f * u, 6f * u); lineTo(19f * u, 12f * u); lineTo(13f * u, 18f * u) }
            drawPath(head, color, style = stroke)
        }
        Glyph.Tray -> {
            line(12f, 4f, 12f, 14f)
            val head = Path().apply { moveTo(7.5f * u, 10f * u); lineTo(12f * u, 14.5f * u); lineTo(16.5f * u, 10f * u) }
            drawPath(head, color, style = stroke)
            val tray = Path().apply { moveTo(4f * u, 14f * u); lineTo(4f * u, 19.5f * u); lineTo(20f * u, 19.5f * u); lineTo(20f * u, 14f * u) }
            drawPath(tray, color, style = stroke)
        }
        Glyph.Link -> {
            line(9.5f, 14.5f, 14.5f, 9.5f)
            drawRoundRect(color, p(3f, 11f), Size(9f * u, 5f * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = stroke)
            drawRoundRect(color, p(12f, 8f), Size(9f * u, 5f * u), androidx.compose.ui.geometry.CornerRadius(2.5f * u), style = stroke)
        }
    }
}
