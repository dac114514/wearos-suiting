package com.example.suiting.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WaveProgressIndicator(
    progress: Float,
    isPlaying: Boolean,
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0x28CFBCFF),
    progressColor: Color = Color(0xFFCFBCFF),
    glowColor: Color = Color(0x45CFBCFF),
    strokeWidth: Float = 18f,
    waveAmplitude: Float = 3.5f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val interactionModifier = if (onSeek != null) {
        modifier.fillMaxSize().pointerInput(Unit) {
            detectDragGestures { change, _ ->
                change.consume()
                val cx = size.width / 2f
                val cy = size.height / 2f
                var angle = atan2(change.position.y - cy, change.position.x - cx) +
                        PI.toFloat() / 2f
                if (angle < 0f) angle += (2f * PI).toFloat()
                onSeek((angle / (2f * PI)).toFloat().coerceIn(0f, 1f))
            }
        }
    } else {
        modifier.fillMaxSize()
    }

    Canvas(modifier = interactionModifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f - strokeWidth - 4f

        // 底部轨道
        drawCircle(
            color = trackColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        if (progress > 0.001f) {
            val amplitude = if (isPlaying) waveAmplitude else 0f
            drawWaveArc(
                cx = cx, cy = cy, radius = radius,
                progress = progress,
                progressColor = progressColor,
                glowColor = glowColor,
                strokeWidth = strokeWidth,
                wavePhase = wavePhase,
                waveAmplitude = amplitude
            )
        }
    }
}

private fun DrawScope.drawWaveArc(
    cx: Float, cy: Float, radius: Float,
    progress: Float,
    progressColor: Color,
    glowColor: Color,
    strokeWidth: Float,
    wavePhase: Float,
    waveAmplitude: Float,
) {
    val sweepAngle = 360f * progress
    val startAngle = -90f
    val steps = (sweepAngle * 3).toInt().coerceAtLeast(12)
    val path = Path()

    for (i in 0..steps) {
        val f = i.toFloat() / steps
        val deg = startAngle + sweepAngle * f
        val rad = Math.toRadians(deg.toDouble()).toFloat()
        // 4 波峰的 Material3 式波浪
        val wave = waveAmplitude * sin(4f * f * 2f * PI.toFloat() + wavePhase)
        val r = radius + wave
        val x = cx + r * cos(rad)
        val y = cy + r * sin(rad)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    // 外发光
    drawPath(path = path, color = glowColor,
        style = Stroke(width = strokeWidth + 12f, cap = StrokeCap.Round))

    // 主弧线渐变
    drawPath(
        path = path,
        brush = Brush.sweepGradient(
            colorStops = arrayOf(
                0f to progressColor.copy(alpha = 0.08f),
                (progress * 0.6f).coerceIn(0f, 0.99f) to progressColor.copy(alpha = 0.75f),
                progress.coerceIn(0f, 1f) to progressColor,
                1f to progressColor.copy(alpha = 0f)
            ),
            center = Offset(cx, cy)
        ),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // 进度头部拖动圆点
    val endRad = Math.toRadians((startAngle + sweepAngle).toDouble()).toFloat()
    val dotX = cx + radius * cos(endRad)
    val dotY = cy + radius * sin(endRad)

    drawCircle(color = glowColor.copy(alpha = 0.5f),  radius = strokeWidth * 1.1f, center = Offset(dotX, dotY))
    drawCircle(color = Color.White,                   radius = strokeWidth * 0.72f, center = Offset(dotX, dotY))
    drawCircle(color = progressColor,                 radius = strokeWidth * 0.52f, center = Offset(dotX, dotY))
}
