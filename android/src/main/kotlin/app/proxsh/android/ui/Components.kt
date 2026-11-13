package app.proxsh.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(if (primary) Paper.accent else Paper.surface)
            .then(if (primary) Modifier else Modifier.border(1.dp, Paper.lineStrong, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Type.label.copy(color = if (primary) Paper.onAccent else Paper.ink))
    }
}

/** A quiet text-only button for small actions inside rows. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Paper.accent) {
    BasicText(
        text,
        modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        style = Type.label.copy(fontSize = 14.sp, color = color),
    )
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Paper.sunken).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Paper.surface else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, Paper.line, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(value) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = Type.caption.copy(fontSize = 13.sp, color = if (on) Paper.ink else Paper.inkSoft))
            }
        }
    }
}

@Composable
fun CheckRow(checked: Boolean, label: String, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onToggle(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (checked) Paper.accent else Paper.surface)
                .border(1.5.dp, if (checked) Paper.accent else Paper.lineStrong, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) GlyphIcon(Glyph.Check, Paper.onAccent, 16.dp)
        }
        BasicText(label, style = Type.bodyText)
    }
}

@Composable
fun DeviceBadge(glyph: Glyph, size: Dp = 44.dp, tint: Color = Paper.accent, background: Color = Paper.accentSoft) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, tint, size * 0.5f)
    }
}

/** Fades and rises in softly the first time it appears. */
fun Modifier.appearSoftly(): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 14f
    }
}

/** One soft ring that spreads out from a new device, like a ripple on the desk. */
fun Modifier.rippleOnce(color: Color): Modifier = composed {
    val spread = remember { Animatable(0f) }
    LaunchedEffect(Unit) { spread.animateTo(1f, tween(1300, easing = LinearEasing)) }
    this.drawBehind {
        val t = spread.value
        if (t < 1f) {
            drawCircle(color.copy(alpha = (1f - t) * 0.5f), radius = size.minDimension * (0.5f + t * 0.55f), style = Stroke(width = 3f))
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Paper.accent) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Paper.line, size = size, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) drawRoundRect(color, size = Size(width, size.height), cornerRadius = radius)
    }
}

@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.clip(shape).background(Paper.surface).border(1.dp, Paper.line, shape)) { content() }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.clip(shape).background(Paper.canvas).border(1.dp, Paper.lineStrong, shape).padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) BasicText(placeholder, style = Type.bodyText.copy(color = Paper.inkFaint))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = Type.bodyText,
            cursorBrush = SolidColor(Paper.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
