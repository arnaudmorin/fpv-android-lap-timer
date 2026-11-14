package com.racegow.camtimer.ui.components

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.racegow.camtimer.core.Direction
import com.racegow.camtimer.core.FrameMotion
import com.racegow.camtimer.core.NormRect
import com.racegow.camtimer.ui.theme.LapGreen
import com.racegow.camtimer.ui.theme.StopRed
import com.racegow.camtimer.ui.timer.Flash
import com.racegow.camtimer.ui.timer.FlashKind
import com.racegow.camtimer.ui.theme.TriggerAmber
import kotlin.math.abs

private val TestBlue = Color(0xFF60A5FA)

private enum class Handle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, RIGHT, TOP, BOTTOM, DIVIDER, MOVE }

/**
 * Detection zone drawn over the camera preview. When [editable], its corners, edges and gate line
 * can be dragged, and dragging inside moves the whole zone. [onZoneChange] reports the zone live,
 * [onZoneCommit] once the finger is lifted. Tapping the direction badge calls [onDirectionTap].
 */
@Composable
fun ZoneOverlay(
    zone: NormRect,
    divider: Float,
    direction: Direction,
    editable: Boolean,
    flash: Flash?,
    motion: FrameMotion?,
    sensitivity: Float,
    onZoneChange: (NormRect, Float) -> Unit,
    onZoneCommit: (NormRect, Float) -> Unit,
    onDirectionTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftZone by remember(zone) { mutableStateOf(zone) }
    var draftDivider by remember(divider) { mutableFloatStateOf(divider) }
    val onChange by rememberUpdatedState(onZoneChange)
    val onCommit by rememberUpdatedState(onZoneCommit)
    val onDirection by rememberUpdatedState(onDirectionTap)
    val textMeasurer = rememberTextMeasurer()
    val touchRadius = with(LocalDensity.current) { 28.dp.toPx() }

    val flashFill = remember { Animatable(Color.Transparent) }
    LaunchedEffect(flash) {
        val f = flash ?: return@LaunchedEffect
        val color = when (f.kind) {
            FlashKind.LAP -> LapGreen
            FlashKind.CROSSED -> TestBlue
            FlashKind.REJECTED -> StopRed
        }
        flashFill.snapTo(color.copy(alpha = 0.45f))
        flashFill.animateTo(Color.Transparent, tween(450))
    }

    Canvas(
        modifier
            .pointerInput(editable, zone, divider) {
                if (!editable) return@pointerInput
                detectTapGestures { pos ->
                    val z = draftZone
                    val badge = directionBadge(
                        gateX = (z.left + draftDivider * z.width) * size.width,
                        top = z.top * size.height,
                        density = density,
                    )
                    if (pos.x in badge.left..badge.right && pos.y in badge.top..badge.bottom) onDirection()
                }
            }
            .pointerInput(editable, zone, divider) {
            if (!editable) return@pointerInput
            var handle = Handle.NONE
            detectDragGestures(
                onDragStart = { pos ->
                    handle = hitTest(pos, size.width.toFloat(), size.height.toFloat(), draftZone, draftDivider, touchRadius)
                },
                onDragEnd = {
                    if (handle != Handle.NONE) onCommit(draftZone, draftDivider)
                    handle = Handle.NONE
                },
                onDragCancel = {
                    if (handle != Handle.NONE) onCommit(draftZone, draftDivider)
                    handle = Handle.NONE
                },
                onDrag = { change, drag ->
                    if (handle != Handle.NONE) {
                        change.consume()
                        val dx = drag.x / size.width
                        val dy = drag.y / size.height
                        val z = draftZone
                        when (handle) {
                            Handle.TOP_LEFT -> draftZone = z.withLeft(z.left + dx).withTop(z.top + dy)
                            Handle.TOP_RIGHT -> draftZone = z.withRight(z.right + dx).withTop(z.top + dy)
                            Handle.BOTTOM_LEFT -> draftZone = z.withLeft(z.left + dx).withBottom(z.bottom + dy)
                            Handle.BOTTOM_RIGHT -> draftZone = z.withRight(z.right + dx).withBottom(z.bottom + dy)
                            Handle.LEFT -> draftZone = z.withLeft(z.left + dx)
                            Handle.RIGHT -> draftZone = z.withRight(z.right + dx)
                            Handle.TOP -> draftZone = z.withTop(z.top + dy)
                            Handle.BOTTOM -> draftZone = z.withBottom(z.bottom + dy)
                            Handle.DIVIDER -> draftDivider = (draftDivider + dx / z.width).coerceIn(0.1f, 0.9f)
                            Handle.MOVE -> draftZone = z.movedBy(dx, dy)
                            Handle.NONE -> Unit
                        }
                        onChange(draftZone, draftDivider)
                    }
                },
            )
        },
    ) {
        val z = draftZone
        val l = z.left * size.width
        val t = z.top * size.height
        val r = z.right * size.width
        val b = z.bottom * size.height
        val zoneSize = Size(r - l, b - t)

        // Dim everything outside the zone.
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, Offset.Zero, Size(size.width, t))
        drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(dim, Offset(0f, t), Size(l, b - t))
        drawRect(dim, Offset(r, t), Size(size.width - r, b - t))

        if (flashFill.value.alpha > 0f) drawRect(flashFill.value, Offset(l, t), zoneSize)
        drawRect(LapGreen, Offset(l, t), zoneSize, style = Stroke(3.dp.toPx()))

        val gateX = l + draftDivider * (r - l)
        drawLine(
            color = Color.White,
            start = Offset(gateX, t),
            end = Offset(gateX, b),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
        )
        if (editable) {
            // Dark pill behind the arrow: it doubles as the tap target to change direction.
            val badge = directionBadge(gateX, t, density)
            drawRoundRect(
                Color.Black.copy(alpha = 0.6f),
                Offset(badge.left, badge.top),
                Size(badge.right - badge.left, badge.bottom - badge.top),
                CornerRadius(12.dp.toPx()),
            )
        }
        drawDirection(direction, gateX, t + BADGE_CENTER_Y.toPx(), 26.dp.toPx(), textMeasurer)

        if (editable) {
            val handleRadius = 7.dp.toPx()
            listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
                drawCircle(Color.White, handleRadius, it)
            }
            drawCircle(Color.White, handleRadius, Offset(gateX, (t + b) / 2))
        }

        if (motion != null) drawDebug(motion, sensitivity, l, t, r, b, gateX)
    }
}

private const val BADGE_HALF_WIDTH_DP = 44f
private val BADGE_CENTER_Y = 20.dp

private class Badge(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun directionBadge(gateX: Float, top: Float, density: Float): Badge {
    val halfW = BADGE_HALF_WIDTH_DP * density
    val centerY = top + BADGE_CENTER_Y.value * density
    val halfH = 16f * density
    return Badge(gateX - halfW, centerY - halfH, gateX + halfW, centerY + halfH)
}

private fun hitTest(p: Offset, w: Float, h: Float, z: NormRect, divider: Float, radius: Float): Handle {
    val l = z.left * w
    val t = z.top * h
    val r = z.right * w
    val b = z.bottom * h
    fun near(x: Float, y: Float) = (p - Offset(x, y)).getDistance() <= radius
    val withinY = p.y in (t - radius)..(b + radius)
    val withinX = p.x in (l - radius)..(r + radius)
    val gateX = l + divider * (r - l)
    return when {
        near(l, t) -> Handle.TOP_LEFT
        near(r, t) -> Handle.TOP_RIGHT
        near(l, b) -> Handle.BOTTOM_LEFT
        near(r, b) -> Handle.BOTTOM_RIGHT
        withinY && abs(p.x - l) <= radius -> Handle.LEFT
        withinY && abs(p.x - r) <= radius -> Handle.RIGHT
        withinX && abs(p.y - t) <= radius -> Handle.TOP
        withinX && abs(p.y - b) <= radius -> Handle.BOTTOM
        p.y in t..b && abs(p.x - gateX) <= radius -> Handle.DIVIDER
        p.x in l..r && p.y in t..b -> Handle.MOVE
        else -> Handle.NONE
    }
}

private fun DrawScope.drawDirection(direction: Direction, centerX: Float, y: Float, halfLength: Float, textMeasurer: TextMeasurer) {
    when (direction) {
        Direction.LEFT_TO_RIGHT -> arrow(Offset(centerX - halfLength, y), Offset(centerX + halfLength, y))
        Direction.RIGHT_TO_LEFT -> arrow(Offset(centerX + halfLength, y), Offset(centerX - halfLength, y))
        Direction.EITHER -> {
            arrow(Offset(centerX, y), Offset(centerX + halfLength, y))
            arrow(Offset(centerX, y), Offset(centerX - halfLength, y))
        }
        Direction.ANY_MOTION -> {
            val text = textMeasurer.measure("ANY", TextStyle(color = TriggerAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            drawText(text, topLeft = Offset(centerX - text.size.width / 2f, y - text.size.height / 2f))
        }
    }
}

private fun DrawScope.arrow(from: Offset, to: Offset) {
    val width = 3.dp.toPx()
    val head = 9.dp.toPx()
    val sign = if (to.x > from.x) 1f else -1f
    drawLine(TriggerAmber, from, to, width, cap = StrokeCap.Round)
    drawLine(TriggerAmber, to, Offset(to.x - sign * head, to.y - head * 0.7f), width, cap = StrokeCap.Round)
    drawLine(TriggerAmber, to, Offset(to.x - sign * head, to.y + head * 0.7f), width, cap = StrokeCap.Round)
}

/** Activity bars per half (amber once above the sensitivity line) and the motion centroid. */
private fun DrawScope.drawDebug(m: FrameMotion, sensitivity: Float, l: Float, t: Float, r: Float, b: Float, gateX: Float) {
    val maxHeight = (b - t) * 0.5f
    val fullScale = (sensitivity * 2f).coerceAtLeast(1f)
    val barWidth = 12.dp.toPx()
    fun bar(x0: Float, x1: Float, pct: Float) {
        val height = (pct / fullScale).coerceIn(0f, 1f) * maxHeight
        val color = if (pct >= sensitivity) TriggerAmber else Color.White.copy(alpha = 0.6f)
        drawRect(color, Offset((x0 + x1) / 2 - barWidth / 2, b - height), Size(barWidth, height))
    }
    bar(l, gateX, m.leftPct)
    bar(gateX, r, m.rightPct)
    drawLine(TriggerAmber.copy(alpha = 0.6f), Offset(l, b - maxHeight / 2), Offset(r, b - maxHeight / 2), 1.dp.toPx())
    if (!m.centroidX.isNaN()) {
        drawCircle(TriggerAmber, 6.dp.toPx(), Offset(l + m.centroidX * (r - l), (t + b) / 2))
    }
}
