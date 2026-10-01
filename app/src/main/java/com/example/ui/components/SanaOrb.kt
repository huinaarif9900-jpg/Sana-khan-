package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.audio.AudioState

@Composable
fun SanaOrb(
    audioState: AudioState,
    audioLevel: Float,
    emotion: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val eyeBlink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    // Dynamic color theme based on audioState and emotion
    val (corePrimary, coreSecondary, glowColor) = when {
        audioState == AudioState.ERROR -> Triple(
            Color(0xFFFF5252),
            Color(0xFFFF7B7B),
            Color(0x66FF5252)
        )
        audioState == AudioState.LISTENING -> Triple(
            Color(0xFF00E5FF),
            Color(0xFF00B0FF),
            Color(0x6600E5FF)
        )
        audioState == AudioState.SPEAKING -> Triple(
            Color(0xFFFF4081),
            Color(0xFF7C4DFF),
            Color(0x66FF4081)
        )
        audioState == AudioState.THINKING -> Triple(
            Color(0xFF7C4DFF),
            Color(0xFF00E5FF),
            Color(0x667C4DFF)
        )
        emotion == "romantic" -> Triple(
            Color(0xFFFF69B4),
            Color(0xFFFF1493),
            Color(0x66FF69B4)
        )
        emotion == "playful" -> Triple(
            Color(0xFFFFAB00),
            Color(0xFFFF4081),
            Color(0x66FFAB00)
        )
        else -> Triple(
            Color(0xFF7052FF),
            Color(0xFFFF7BB0),
            Color(0x557052FF)
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 3.4f
            val dynamicBoost = if (audioState == AudioState.LISTENING || audioState == AudioState.SPEAKING) {
                audioLevel * 24f
            } else 0f

            val currentRadius = (baseRadius * pulseScale) + dynamicBoost

            // 1. Outer ambient glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = currentRadius * 1.6f
                ),
                radius = currentRadius * 1.6f,
                center = center
            )

            // 2. Audio ripple waves
            if (audioLevel > 0.08f || audioState == AudioState.SPEAKING) {
                drawCircle(
                    color = corePrimary.copy(alpha = 0.35f),
                    radius = currentRadius * (1.25f + audioLevel * 0.3f),
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = coreSecondary.copy(alpha = 0.2f),
                    radius = currentRadius * (1.45f + audioLevel * 0.4f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 3. Main Orb Gradient
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(corePrimary, coreSecondary),
                    start = Offset(center.x - currentRadius, center.y - currentRadius),
                    end = Offset(center.x + currentRadius, center.y + currentRadius)
                ),
                radius = currentRadius,
                center = center
            )

            // 4. Subtle orbital ring
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = currentRadius * 1.12f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 5. Cute digital anime eye expressions
            val eyeSpacing = currentRadius * 0.35f
            val eyeRadiusY = (currentRadius * 0.18f) * eyeBlink.coerceAtLeast(0.15f)
            val eyeRadiusX = currentRadius * 0.12f
            val eyeCenterY = center.y - (currentRadius * 0.08f)

            // Left Eye
            drawOval(
                color = Color.White.copy(alpha = 0.95f),
                topLeft = Offset(center.x - eyeSpacing - eyeRadiusX, eyeCenterY - eyeRadiusY),
                size = androidx.compose.ui.geometry.Size(eyeRadiusX * 2, eyeRadiusY * 2)
            )
            // Left Eye Sparkle
            drawCircle(
                color = corePrimary,
                radius = eyeRadiusX * 0.45f,
                center = Offset(center.x - eyeSpacing, eyeCenterY)
            )

            // Right Eye
            drawOval(
                color = Color.White.copy(alpha = 0.95f),
                topLeft = Offset(center.x + eyeSpacing - eyeRadiusX, eyeCenterY - eyeRadiusY),
                size = androidx.compose.ui.geometry.Size(eyeRadiusX * 2, eyeRadiusY * 2)
            )
            // Right Eye Sparkle
            drawCircle(
                color = corePrimary,
                radius = eyeRadiusX * 0.45f,
                center = Offset(center.x + eyeSpacing, eyeCenterY)
            )

            // Cute digital smile curve
            val smileWidth = currentRadius * 0.28f
            val smileCenterY = center.y + (currentRadius * 0.25f)
            drawArc(
                color = Color.White.copy(alpha = 0.85f),
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(center.x - smileWidth / 2f, smileCenterY - 6.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(smileWidth, 12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
