package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AssistantState
import kotlin.math.sin

@Composable
fun GlowingOrbVisualizer(
  state: AssistantState,
  amplitude: Float,
  onOrbClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")

  // Continuous subtle rotation
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(12000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rotation"
  )

  // Breathing pulse animation
  val idlePulse by infiniteTransition.animateFloat(
    initialValue = 0.96f,
    targetValue = 1.04f,
    animationSpec = infiniteRepeatable(
      animation = tween(2200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "idlePulse"
  )

  // Glow halo dynamic sizing
  val dynamicScale = when (state) {
    AssistantState.SPEAKING -> 1f + (amplitude * 0.22f)
    AssistantState.LISTENING -> 1f + (amplitude * 0.18f)
    AssistantState.THINKING -> idlePulse * 1.06f
    else -> idlePulse
  }

  val primaryGlow = when (state) {
    AssistantState.SPEAKING -> Color(0xFFFF2A85) // Sassy hot pink
    AssistantState.LISTENING -> Color(0xFF00E5FF) // Electric cyan
    AssistantState.THINKING -> Color(0xFF9D4EDD) // Royal purple
    AssistantState.INTERRUPTED -> Color(0xFFFFB703) // Gold amber
    else -> Color(0xFFE040FB) // Vivid magenta
  }

  val secondaryGlow = when (state) {
    AssistantState.SPEAKING -> Color(0xFF7928CA)
    AssistantState.LISTENING -> Color(0xFF0070F3)
    AssistantState.THINKING -> Color(0xFFFF0080)
    else -> Color(0xFF4361EE)
  }

  Box(
    modifier = modifier
      .size(240.dp)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false, radius = 130.dp),
        onClick = onOrbClick
      ),
    contentAlignment = Alignment.Center
  ) {
    // Canvas for outer rotating holographic glowing aura
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .scale(dynamicScale)
        .rotate(rotation)
    ) {
      val centerOffset = Offset(size.width / 2f, size.height / 2f)
      val maxRadius = size.minDimension / 2f

      // Outer soft radial aura
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            primaryGlow.copy(alpha = 0.35f),
            secondaryGlow.copy(alpha = 0.18f),
            Color.Transparent
          ),
          center = centerOffset,
          radius = maxRadius
        ),
        radius = maxRadius,
        center = centerOffset
      )

      // Holographic concentric rings
      drawCircle(
        brush = Brush.sweepGradient(
          colors = listOf(
            primaryGlow.copy(alpha = 0.8f),
            secondaryGlow.copy(alpha = 0.4f),
            Color(0xFF00E5FF).copy(alpha = 0.7f),
            primaryGlow.copy(alpha = 0.8f)
          )
        ),
        radius = maxRadius * 0.76f,
        center = centerOffset,
        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
      )

      // Inner dashed resonance ring
      drawCircle(
        color = primaryGlow.copy(alpha = 0.45f),
        radius = maxRadius * 0.88f,
        center = centerOffset,
        style = Stroke(width = 1.5.dp.toPx())
      )
    }

    // Avatar center circle with neon border
    Box(
      modifier = Modifier
        .size(136.dp)
        .scale(dynamicScale)
        .clip(CircleShape)
        .background(Color(0xFF0E071A))
        .border(
          width = 3.dp,
          brush = Brush.linearGradient(
            colors = listOf(primaryGlow, secondaryGlow, Color(0xFF00E5FF))
          ),
          shape = CircleShape
        ),
      contentAlignment = Alignment.Center
    ) {
      Image(
        painter = painterResource(id = R.drawable.zoya_avatar),
        contentDescription = "Zoya AI Avatar",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
      )

      // Soft glass overlay
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color(0x33000000)
              )
            )
          )
      )
    }
  }
}

@Composable
fun AudioWaveformBars(
  state: AssistantState,
  amplitude: Float,
  modifier: Modifier = Modifier
) {
  val barCount = 18
  val infiniteTransition = rememberInfiniteTransition(label = "wave_bars")

  val phase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 6.28f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wave_phase"
  )

  val barColor = when (state) {
    AssistantState.SPEAKING -> Color(0xFFFF2A85)
    AssistantState.LISTENING -> Color(0xFF00E5FF)
    AssistantState.THINKING -> Color(0xFF9D4EDD)
    else -> Color(0xFF8A65D4)
  }

  Row(
    modifier = modifier
      .height(36.dp)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    for (i in 0 until barCount) {
      val barPhase = phase + (i * 0.35f)
      val sineVal = (sin(barPhase.toDouble()) + 1.0) / 2.0 // 0..1

      val minHeight = 6.dp
      val calculatedHeight = when (state) {
        AssistantState.SPEAKING -> {
          val activeAmp = amplitude.coerceIn(0.1f, 1f)
          (minHeight + (26.dp * (sineVal * activeAmp).toFloat()))
        }
        AssistantState.LISTENING -> {
          val activeAmp = amplitude.coerceIn(0.1f, 1f)
          (minHeight + (22.dp * (sineVal * activeAmp).toFloat()))
        }
        AssistantState.THINKING -> {
          (minHeight + (14.dp * sineVal.toFloat()))
        }
        else -> minHeight
      }

      Box(
        modifier = Modifier
          .width(4.dp)
          .height(calculatedHeight)
          .clip(RoundedCornerShape(2.dp))
          .background(
            Brush.verticalGradient(
              colors = listOf(
                barColor,
                barColor.copy(alpha = 0.4f)
              )
            )
          )
      )

      if (i < barCount - 1) {
        Spacer(modifier = Modifier.width(3.dp))
      }
    }
  }
}
