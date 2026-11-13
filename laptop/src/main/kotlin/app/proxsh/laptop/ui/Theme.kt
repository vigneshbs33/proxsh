package app.proxsh.laptop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle as ComposeFontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * The look: a tidy desk seen from above. Warm paper, graphite ink, and one deep teal that means
 * "go" (accept, send, connected). Everything else stays quiet.
 */
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

    val ok = Color(0xFF4F7A55)
    val warn = Color(0xFFA86A12)
    val warnSoft = Color(0xFFF6E8CF)
    val danger = Color(0xFFA8432F)
    val dangerSoft = Color(0xFFF4E1DB)
}

private class Face(val file: String, val weight: FontWeight, val style: ComposeFontStyle = ComposeFontStyle.Normal)

/** Loads font files that Windows already has. Falls back to the generic family if they are missing. */
private fun systemFamily(faces: List<Face>, fallback: FontFamily): FontFamily {
    val dir = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
    val fonts = faces.mapNotNull { face ->
        File(dir, face.file).takeIf { it.exists() }?.let { Font(it, face.weight, face.style) }
    }
    return if (fonts.isEmpty()) fallback else FontFamily(*fonts.toTypedArray())
}

/** Fonts that ship with Windows, so nothing is downloaded and it works offline. */
object Fonts {
    val display: FontFamily by lazy {
        systemFamily(
            listOf(
                Face("georgia.ttf", FontWeight.Normal),
                Face("georgiai.ttf", FontWeight.Normal, ComposeFontStyle.Italic),
                Face("georgiab.ttf", FontWeight.Bold),
                Face("georgiaz.ttf", FontWeight.Bold, ComposeFontStyle.Italic),
            ),
            FontFamily.Serif,
        )
    }
    val body: FontFamily by lazy {
        systemFamily(
            listOf(
                Face("segoeui.ttf", FontWeight.Normal),
                Face("seguisb.ttf", FontWeight.SemiBold),
                Face("segoeuib.ttf", FontWeight.Bold),
                Face("segoeuii.ttf", FontWeight.Normal, ComposeFontStyle.Italic),
            ),
            FontFamily.SansSerif,
        )
    }
}

object Type {
    val display get() = TextStyle(fontFamily = Fonts.display, fontSize = 36.sp, lineHeight = 40.sp, color = Paper.ink)
    val displayTurn get() = display.copy(fontStyle = ComposeFontStyle.Italic)
    val title get() = TextStyle(fontFamily = Fonts.display, fontSize = 20.sp, lineHeight = 26.sp, color = Paper.ink)
    val label get() = TextStyle(fontFamily = Fonts.body, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Paper.ink)
    val body get() = TextStyle(fontFamily = Fonts.body, fontSize = 14.sp, lineHeight = 20.sp, color = Paper.ink)
    val soft get() = TextStyle(fontFamily = Fonts.body, fontSize = 13.sp, lineHeight = 18.sp, color = Paper.inkSoft)
    val caption get() = TextStyle(fontFamily = Fonts.body, fontSize = 12.sp, lineHeight = 16.sp, color = Paper.inkSoft)
    val code get() = TextStyle(fontFamily = Fonts.display, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = 6.sp, color = Paper.ink)
}
