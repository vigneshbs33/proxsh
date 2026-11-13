package app.proxsh.laptop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        primary && (pressed || hovered) -> Paper.accentPressed
        primary -> Paper.accent
        pressed -> Paper.line
        hovered -> Paper.sunken
        else -> Paper.surface
    }
    val textColor = when {
        primary -> Paper.onAccent
        danger -> Paper.danger
        else -> Paper.ink
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(background)
            .then(if (primary) Modifier else Modifier.border(1.dp, Paper.lineStrong, shape))
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Type.label.copy(fontSize = 14.sp, color = textColor))
    }
}

/** A quiet text-only button for small actions inside rows. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Paper.accent) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    BasicText(
        text,
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) Paper.sunken else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = Type.label.copy(fontSize = 13.sp, color = color),
    )
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.clip(shape).background(Paper.sunken).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Paper.surface else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, Paper.line, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(value) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = Type.caption.copy(color = if (on) Paper.ink else Paper.inkSoft, fontSize = 12.sp))
            }
        }
    }
}

@Composable
fun CheckRow(checked: Boolean, label: String, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(!checked) }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) Paper.accent else Paper.surface)
                .border(1.5.dp, if (checked) Paper.accent else Paper.lineStrong, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) GlyphIcon(Glyph.Check, Paper.onAccent, 14.dp)
        }
        BasicText(label, style = Type.body)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Paper.line))
}

/** A round badge holding a device icon. */
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
            drawCircle(
                color.copy(alpha = (1f - t) * 0.5f),
                radius = size.minDimension * (0.5f + t * 0.55f),
                style = Stroke(width = 2f),
            )
        }
    }
}

/** Fills a bar to [fraction], with a rounded end. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Paper.accent) {
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(Paper.line, size = size, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) drawRoundRect(color, size = androidx.compose.ui.geometry.Size(width, size.height), cornerRadius = radius)
    }
}

@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.clip(shape).background(Paper.surface).border(1.dp, Paper.line, shape)) { content() }
}
