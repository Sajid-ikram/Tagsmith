package com.tagsmith.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tagsmith.ui.theme.Tagsmith
import kotlin.math.roundToInt

/**
 * Waiting. Rings radiate out of the phone silhouette on a three-second cycle,
 * one starting each second, so there is always something moving.
 */
@Composable
fun PulsingRings(
    modifier: Modifier = Modifier,
    diameter: Dp = 300.dp,
    color: Color = Tagsmith.colors.accent,
    ringCount: Int = 3,
    periodMillis: Int = 3000,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition(label = "rings")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        repeat(ringCount) { index ->
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(periodMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(periodMillis / ringCount * index),
                ),
                label = "ring$index",
            )
            // Scale .45 → 1.6 easing out, fading to nothing by the time it clears.
            val scale = 0.45f + (1.6f - 0.45f) * EaseOutCubic.transform(progress)
            val alpha = if (progress < 0.7f) {
                0.85f - (0.85f - 0.25f) * (progress / 0.7f)
            } else {
                0.25f * (1f - (progress - 0.7f) / 0.3f)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .border(strokeWidth, color.copy(alpha = alpha.coerceIn(0f, 1f)), CircleShape)
            )
        }
    }
}

/**
 * Detected. The rings stop radiating and snap inward onto the tag — the visual
 * half of the haptic moment.
 */
@Composable
fun SnapRings(
    modifier: Modifier = Modifier,
    diameter: Dp = 300.dp,
    color: Color = Tagsmith.colors.accent,
) {
    val outer = remember { Animatable(1.35f) }
    val inner = remember { Animatable(1.35f) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(Unit) { fade.animateTo(1f, tween(200)) }
    LaunchedEffect(Unit) { outer.animateTo(1f, tween(500, easing = EaseOutCubic)) }
    LaunchedEffect(Unit) { inner.animateTo(1f, tween(400, easing = EaseOutCubic)) }

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(diameter * 0.65f)
                .scale(outer.value)
                .border(2.dp, color.copy(alpha = 0.4f * fade.value), CircleShape)
        )
        Box(
            Modifier
                .size(diameter * 0.5f)
                .scale(inner.value)
                .border(3.dp, color.copy(alpha = fade.value), CircleShape)
        )
    }
}

/**
 * The phone silhouette at the centre of the rings, with the ember square and
 * the NFC mark inside it. [bob] gives it the slow float used in onboarding.
 */
@Composable
fun PhoneMark(
    modifier: Modifier = Modifier,
    width: Dp = 104.dp,
    height: Dp = 160.dp,
    outline: Color = Tagsmith.colors.ink,
    fill: Color = Color.Transparent,
    accent: Color = Tagsmith.colors.accent,
    bob: Boolean = false,
    alignBottom: Boolean = false,
    pop: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "bob")
    val bobOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (bob) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bobOffset",
    )
    val popScale = remember { Animatable(if (pop) 0.6f else 1f) }
    LaunchedEffect(pop) { if (pop) popScale.animateTo(1f, tween(350, easing = EaseOutCubic)) }

    Box(
        modifier = modifier
            .offset { IntOffset(0, (-6.dp.toPx() * bobOffset).roundToInt()) }
            .size(width, height)
            .background(fill)
            .border(3.dp, outline, RoundedCornerShape(16.dp)),
        contentAlignment = if (alignBottom) Alignment.BottomCenter else Alignment.Center,
    ) {
        Box(
            Modifier
                .offset(y = if (alignBottom) (-12).dp else 0.dp)
                .size(width * 0.54f)
                .scale(popScale.value)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TagsmithIcons.Nfc,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(width * 0.27f),
            )
        }
    }
}

/** The indeterminate sweep under "Reading tag…" — a 30% block crossing the track. */
@Composable
fun SweepBar(
    modifier: Modifier = Modifier,
    trackColor: Color = Tagsmith.colors.hairline,
    barColor: Color = Tagsmith.colors.accent,
    height: Dp = 4.dp,
) {
    val transition = rememberInfiniteTransition(label = "sweep")
    val progress by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "sweepOffset",
    )
    Box(
        modifier
            .height(height)
            .background(trackColor)
            .clipToBounds()
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .offsetByFraction(progress)
                .background(barColor)
        )
    }
}

/** Offsets a child horizontally by a fraction of the space it was given. */
private fun Modifier.offsetByFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative((constraints.maxWidth * fraction).toInt(), 0)
    }
}

/**
 * Success. The check draws itself in rather than appearing — the motion is what
 * tells you the write landed.
 */
@Composable
fun DrawnCheck(
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    color: Color = Tagsmith.colors.success,
    strokeWidth: Dp = 6.dp,
) {
    val ringScale = remember { Animatable(0.6f) }
    val ringAlpha = remember { Animatable(0f) }
    val draw = remember { Animatable(0f) }

    LaunchedEffect(Unit) { ringAlpha.animateTo(1f, tween(200)) }
    LaunchedEffect(Unit) { ringScale.animateTo(1f, tween(450, easing = EaseOutCubic)) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(500, delayMillis = 250, easing = EaseOutCubic)) }

    Box(
        modifier
            .size(diameter)
            .scale(ringScale.value)
            .border(3.dp, color.copy(alpha = ringAlpha.value), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.5f)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.08f, h * 0.52f)
                lineTo(w * 0.38f, h * 0.82f)
                lineTo(w * 0.92f, h * 0.18f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val segment = Path()
            measure.getSegment(0f, measure.length * draw.value, segment, true)
            drawPath(
                path = segment,
                color = color,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** A dashed circle for the error states — the ring that failed to close. */
@Composable
fun BrokenRing(
    modifier: Modifier = Modifier,
    diameter: Dp = 150.dp,
    color: Color,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = color,
                radius = size.minDimension / 2 - 2.dp.toPx(),
                center = Offset(size.width / 2, size.height / 2),
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                ),
            )
        }
        content()
    }
}

/** The blinking dot beside "NFC READY". */
@Composable
fun BlinkingDot(modifier: Modifier = Modifier, color: Color, size: Dp = 7.dp) {
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blinkAlpha",
    )
    Box(modifier.size(size).background(color.copy(alpha = alpha), CircleShape))
}

