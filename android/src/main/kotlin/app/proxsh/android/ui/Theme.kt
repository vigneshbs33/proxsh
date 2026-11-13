package app.proxsh.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The same look as the laptop: a tidy desk seen from above. Warm paper, graphite ink, one teal. */
object Paper {
    val canvas = Color(0xFFF6F2EA)
    val surface = Color(0xFFFFFDF8)
    val sunken = Color(0xFFEFE9DE)
    val ink = Color(0xFF2B2926)
    val inkSoft = Color(0xFF6B665E)
    val inkFaint = Color(0xFF9A948A)
    val line = Color(0xFFE4DED3)
    val lineStrong = Color(0xFFCFC7B9)

    val accent = Color(0xFF0F6B63)
    val accentPressed = Color(0xFF0B5750)
    val onAccent = Color(0xFFFFFFFF)
    val accentSoft = Color(0xFFDCEBE8)

    val warn = Color(0xFFA86A12)
    val warnSoft = Color(0xFFF6E8CF)
    val danger = Color(0xFFA8432F)
}

object Type {
    private val display = FontFamily.Serif
    private val body = FontFamily.SansSerif

    val headline get() = TextStyle(fontFamily = display, fontSize = 34.sp, lineHeight = 38.sp, color = Paper.ink)
    val headlineTurn get() = headline.copy(fontStyle = FontStyle.Italic)
    val title get() = TextStyle(fontFamily = display, fontSize = 20.sp, lineHeight = 26.sp, color = Paper.ink)
    val label get() = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Paper.ink)
    val bodyText get() = TextStyle(fontFamily = body, fontSize = 15.sp, lineHeight = 21.sp, color = Paper.ink)
    val soft get() = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 19.sp, color = Paper.inkSoft)
    val caption get() = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 16.sp, color = Paper.inkSoft)
    val code get() = TextStyle(fontFamily = display, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = 6.sp, color = Paper.ink)
}
