package com.faster.suiting.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// 首尾硬阻尼区间：进度落在此范围内禁止继续向该方向拖动
private const val DEAD_LOW  = 0.02f   // < 2%  时禁止继续减小
private const val DEAD_HIGH = 0.98f   // > 98% 时禁止继续增大

private const val LONG_PRESS_MS = 200L

@Composable
fun WaveProgressIndicator(
    progress: Float,
    isPlaying: Boolean,
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    trackColor: Color    = Color(0x28CFBCFF),
    progressColor: Color = Color(0xFFCFBCFF),
    glowColor: Color     = Color(0x45CFBCFF),
    strokeWidth: Float   = 18f,
) {
    val dotScale = remember { Animatable(0.35f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val interactionModifier = if (onSeek != null) {
        modifier.fillMaxSize().pointerInput(Unit) {

            val cx      = size.width  / 2f
            val cy      = size.height / 2f
            val radius  = minOf(size.width, size.height) / 2f - strokeWidth / 2f
            val hitZone = strokeWidth * 2f

            awaitPointerEventScope {
                var trackingId:   Long = -1L
                var longPressJob: Job? = null

                while (true) {
                    val event  = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    val pos    = change.position
                    val dx     = pos.x - cx
                    val dy     = pos.y - cy
                    val dist   = sqrt(dx * dx + dy * dy)
                    val onRing = abs(dist - radius) < hitZone

                    when (event.type) {

                        PointerEventType.Press -> {
                            if (onRing && trackingId == -1L) {
                                trackingId = change.id.value
                                change.consume()
                                longPressJob = scope.launch {
                                    delay(LONG_PRESS_MS)
                                    isDragging = true
                                    dotScale.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
                                }
                            }
                        }

                        PointerEventType.Move -> {
                            if (change.id.value == trackingId && isDragging) {
                                change.consume()

                                // 绝对位置 → 进度值
                                val newProgress = toAngle(pos.x - cx, pos.y - cy) /
                                        (2f * PI.toFloat())

                                // 首尾硬阻尼：
                                // 在 0~2% 区域，只允许进度增大（向前拖），不允许减小
                                // 在 98%~100% 区域，只允许进度减小（向后拖），不允许增大
                                val blocked = (newProgress < DEAD_LOW  && newProgress < progress) ||
                                              (newProgress > DEAD_HIGH && newProgress > progress)

                                if (!blocked) {
                                    onSeek(newProgress.coerceIn(0f, 1f))
                                }
                            }
                        }

                        PointerEventType.Release, PointerEventType.Exit -> {
                            if (change.id.value == trackingId) {
                                longPressJob?.cancel()
                                longPressJob = null
                                trackingId   = -1L
                                if (isDragging) {
                                    isDragging = false
                                    change.consume()
                                    scope.launch {
                                        dotScale.animateTo(0.35f,
                                            spring(stiffness = Spring.StiffnessMedium))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        modifier.fillMaxSize()
    }

    val dotScaleValue = dotScale.value

    Canvas(modifier = interactionModifier) {
        val cx     = size.width  / 2f
        val cy     = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f - strokeWidth / 2f

        drawCircle(
            color  = trackColor,
            radius = radius,
            center = Offset(cx, cy),
            style  = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        if (progress > 0.001f) {
            drawProgressArc(
                cx = cx, cy = cy, radius = radius,
                progress      = progress,
                progressColor = progressColor,
                glowColor     = glowColor,
                strokeWidth   = strokeWidth,
                dotScale      = dotScaleValue
            )
        }
    }
}

private fun toAngle(dx: Float, dy: Float): Float {
    val twoPi = 2f * PI.toFloat()
    var a = atan2(dy.toDouble(), dx.toDouble()).toFloat() + PI.toFloat() / 2f
    if (a < 0f)    a += twoPi
    if (a > twoPi) a -= twoPi
    return a
}

private fun DrawScope.drawProgressArc(
    cx: Float, cy: Float, radius: Float,
    progress: Float,
    progressColor: Color,
    glowColor: Color,
    strokeWidth: Float,
    dotScale: Float,
) {
    val sweepAngle = 360f * progress
    val startAngle = -90f
    val topLeft    = Offset(cx - radius, cy - radius)
    val arcSize    = Size(radius * 2f, radius * 2f)

    drawArc(color = glowColor, startAngle = startAngle, sweepAngle = sweepAngle,
        useCenter = false, topLeft = topLeft, size = arcSize,
        style = Stroke(width = strokeWidth + 12f, cap = StrokeCap.Round))

    drawArc(
        brush = Brush.sweepGradient(
            colorStops = arrayOf(
                0f                                      to progressColor.copy(alpha = 0.05f),
                (progress * 0.55f).coerceIn(0f, 0.99f) to progressColor.copy(alpha = 0.70f),
                progress.coerceIn(0.01f, 1f)            to progressColor,
                1f                                      to progressColor.copy(alpha = 0f)
            ),
            center = Offset(cx, cy)
        ),
        startAngle = startAngle, sweepAngle = sweepAngle,
        useCenter = false, topLeft = topLeft, size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    val endRad = Math.toRadians((startAngle + sweepAngle).toDouble()).toFloat()
    val dotX   = cx + radius * cos(endRad)
    val dotY   = cy + radius * sin(endRad)
    val baseR  = strokeWidth * 0.8f

    drawCircle(color = glowColor.copy(alpha = 0.55f * dotScale),
        radius = baseR * 1.5f * dotScale, center = Offset(dotX, dotY))
    drawCircle(color = Color.White,
        radius = baseR * dotScale, center = Offset(dotX, dotY))
    drawCircle(color = progressColor,
        radius = baseR * 0.72f * dotScale, center = Offset(dotX, dotY))
}
