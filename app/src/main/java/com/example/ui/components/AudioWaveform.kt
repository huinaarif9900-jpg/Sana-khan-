package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.audio.AudioState
import kotlin.math.sin

@Composable
fun AudioWaveform(
    audioState: AudioState,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val barCount = 28
        val spacing = 4.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount
        val centerY = size.height / 2f

        val (colorStart, colorEnd) = when (audioState) {
            AudioState.LISTENING -> Pair(Color(0xFF00E5FF), Color(0xFF00B0FF))
            AudioState.SPEAKING -> Pair(Color(0xFFFF4081), Color(0xFF7C4DFF))
            AudioState.THINKING -> Pair(Color(0xFF7C4DFF), Color(0xFF00E5FF))
            AudioState.ERROR -> Pair(Color(0xFFFF5252), Color(0xFFFF8A80))
            else -> Pair(Color(0xFF8A7BFF).copy(alpha = 0.5f), Color(0xFFFF7BB0).copy(alpha = 0.5f))
        }

        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / barCount
            val waveModifier = if (audioState == AudioState.LISTENING || audioState == AudioState.SPEAKING) {
                sin((normalizedIndex * Math.PI * 3 + phase).toDouble()).toFloat() * 0.4f + 0.6f
            } else if (audioState == AudioState.THINKING) {
                sin((normalizedIndex * Math.PI * 4 + phase).toDouble()).toFloat() * 0.3f + 0.4f
            } else {
                0.12f
            }

            val dynamicHeight = (size.height * 0.75f * (audioLevel.coerceAtLeast(0.1f) * waveModifier))
                .coerceIn(4.dp.toPx(), size.height)

            val left = i * (barWidth + spacing)
            val top = centerY - (dynamicHeight / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(colorStart, colorEnd),
                    startY = top,
                    endY = top + dynamicHeight
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, dynamicHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
