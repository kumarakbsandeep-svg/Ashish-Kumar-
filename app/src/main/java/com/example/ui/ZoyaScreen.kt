package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.AssistantState
import com.example.data.model.PersonalityMode
import com.example.ui.components.AudioWaveformBars
import com.example.ui.components.ConversationSheet
import com.example.ui.components.GlowingOrbVisualizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoyaScreen(
  viewModel: ZoyaViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  var showTranscriptSheet by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      viewModel.startListening()
    }
  }

  fun requestMicAndListen() {
    if (ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      if (uiState.state == AssistantState.LISTENING) {
        viewModel.stopListening()
      } else {
        viewModel.startListening()
      }
    } else {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF0C0717),
            Color(0xFF140C26),
            Color(0xFF090512)
          )
        )
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // 1. Top Header Bar
      TopHeaderBar(
        personality = uiState.personality,
        continuousMode = uiState.continuousMode,
        isMuted = uiState.isMuted,
        onToggleContinuous = { viewModel.toggleContinuousMode() },
        onToggleMute = { viewModel.toggleMute() },
        onOpenTranscript = { showTranscriptSheet = true }
      )

      // 2. Personality Selector Chips
      PersonalityChipsRow(
        selectedMode = uiState.personality,
        onModeSelected = { viewModel.setPersonality(it) }
      )

      // 3. Centerpiece Visualizer & Dialogue
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f, fill = false)
      ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Glowing Voice Orb
        GlowingOrbVisualizer(
          state = uiState.state,
          amplitude = uiState.audioAmplitude,
          onOrbClick = { requestMicAndListen() }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Live Audio Waveform Bars
        AudioWaveformBars(
          state = uiState.state,
          amplitude = uiState.audioAmplitude
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status Pill
        StatusPill(state = uiState.state, statusMessage = uiState.statusMessage)

        Spacer(modifier = Modifier.height(12.dp))

        // Live Dialogue Card
        DialogueBubble(
          text = uiState.lastAssistantResponse,
          personality = uiState.personality,
          state = uiState.state
        )

        // Tool execution pill if active
        uiState.activeToolCall?.let { tool ->
          Spacer(modifier = Modifier.height(10.dp))
          ToolCallPill(
            tool = tool,
            onOpen = { viewModel.executeTool(tool) }
          )
        }
      }

      // 4. Quick Prompts & Bottom Voice Controls
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        QuickPromptsRow(
          onPromptSelected = { prompt ->
            viewModel.sendTextMessage(prompt)
          }
        )

        Spacer(modifier = Modifier.height(18.dp))

        VoiceControlBar(
          state = uiState.state,
          continuousMode = uiState.continuousMode,
          onMicClick = { requestMicAndListen() },
          onInterruptClick = { viewModel.interrupt() }
        )

        Spacer(modifier = Modifier.height(6.dp))
      }
    }

    // Modal Sheet for Full Transcript & Banter
    if (showTranscriptSheet) {
      ModalBottomSheet(
        onDismissRequest = { showTranscriptSheet = false },
        sheetState = sheetState,
        containerColor = Color(0xFF130B22),
        contentColor = Color(0xFFF3E8FF)
      ) {
        ConversationSheet(
          chatHistory = uiState.chatHistory,
          onSendMessage = {
            viewModel.sendTextMessage(it)
          },
          onToolClick = {
            viewModel.executeTool(it)
          },
          onClearChat = {
            viewModel.clearConversation()
          },
          modifier = Modifier.height(520.dp)
        )
      }
    }
  }
}

@Composable
fun TopHeaderBar(
  personality: PersonalityMode,
  continuousMode: Boolean,
  isMuted: Boolean,
  onToggleContinuous: () -> Unit,
  onToggleMute: () -> Unit,
  onOpenTranscript: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Brand title with live online dot
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(Color(0xFF00E5FF))
      )
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(
          text = "ZOYA",
          fontSize = 18.sp,
          fontWeight = FontWeight.Black,
          letterSpacing = 2.sp,
          color = Color(0xFFF3E8FF)
        )
        Text(
          text = "${personality.emoji} ${personality.title}",
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFFE040FB)
        )
      }
    }

    // Top action controls
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Hands-free continuous session pill
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(20.dp))
          .background(if (continuousMode) Color(0xFF2C164D) else Color(0xFF1B122C))
          .border(
            1.dp,
            if (continuousMode) Color(0xFFE040FB).copy(alpha = 0.7f) else Color(0xFF43325B),
            RoundedCornerShape(20.dp)
          )
          .clickable { onToggleContinuous() }
          .padding(horizontal = 10.dp, vertical = 6.dp)
          .testTag("toggle_continuous_mode"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (continuousMode) "Hands-Free ON" else "Hands-Free OFF",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = if (continuousMode) Color(0xFFE040FB) else Color(0xFF9E8FB2)
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Mute toggle
      IconButton(
        onClick = onToggleMute,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(Color(0xFF1B122C))
          .testTag("toggle_mute_button")
      ) {
        Icon(
          imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
          contentDescription = "Mute audio",
          tint = if (isMuted) Color(0xFFFF5252) else Color(0xFF00E5FF),
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Open transcript sheet
      IconButton(
        onClick = onOpenTranscript,
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(Color(0xFF1B122C))
          .testTag("open_transcript_button")
      ) {
        Icon(
          imageVector = Icons.Default.ChatBubbleOutline,
          contentDescription = "View chat banter",
          tint = Color(0xFFE040FB),
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
fun PersonalityChipsRow(
  selectedMode: PersonalityMode,
  onModeSelected: (PersonalityMode) -> Unit
) {
  val modes = PersonalityMode.entries
  val scrollState = rememberScrollState()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp)
      .horizontalScroll(scrollState),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    modes.forEach { mode ->
      val isSelected = mode == selectedMode
      val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFE040FB) else Color(0xFF1B122C),
        label = "mode_bg"
      )
      val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFFC7B8DF),
        label = "mode_text"
      )

      Surface(
        onClick = { onModeSelected(mode) },
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier
          .border(
            width = 1.dp,
            color = if (isSelected) Color(0xFFFF4081) else Color(0xFF382654),
            shape = RoundedCornerShape(16.dp)
          )
          .testTag("mode_${mode.name.lowercase()}")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = mode.emoji, fontSize = 13.sp)
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = mode.title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
          )
        }
      }
    }
  }
}

@Composable
fun StatusPill(
  state: AssistantState,
  statusMessage: String
) {
  val pillColor = when (state) {
    AssistantState.LISTENING -> Color(0xFF00E5FF)
    AssistantState.SPEAKING -> Color(0xFFFF2A85)
    AssistantState.THINKING -> Color(0xFF9D4EDD)
    AssistantState.INTERRUPTED -> Color(0xFFFFB703)
    else -> Color(0xFF8A65D4)
  }

  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(20.dp))
      .background(pillColor.copy(alpha = 0.15f))
      .border(1.dp, pillColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
      .padding(horizontal = 14.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(pillColor)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = statusMessage,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = pillColor
    )
  }
}

@Composable
fun DialogueBubble(
  text: String,
  personality: PersonalityMode,
  state: AssistantState
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = Color(0xFF1B112E).copy(alpha = 0.85f)
    ),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      Brush.linearGradient(
        colors = listOf(
          Color(0xFFE040FB).copy(alpha = 0.4f),
          Color(0xFF00E5FF).copy(alpha = 0.2f)
        )
      )
    )
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "“ $text ”",
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFFF5EEFF),
        lineHeight = 22.sp,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
fun ToolCallPill(
  tool: com.example.data.model.ToolCall,
  onOpen: () -> Unit
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .background(Color(0xFF1F1138))
      .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
      .clickable { onOpen() }
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = Icons.Default.OpenInBrowser,
      contentDescription = null,
      tint = Color(0xFF00E5FF),
      modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(
        text = "Tool Action: ${tool.name}",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF00E5FF)
      )
      tool.url?.let {
        Text(
          text = it,
          fontSize = 11.sp,
          color = Color(0xFFE040FB),
          maxLines = 1
        )
      }
    }
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = "Launch ↗",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF00E5FF)
    )
  }
}

@Composable
fun QuickPromptsRow(
  onPromptSelected: (String) -> Unit
) {
  val prompts = listOf(
    "Roast my playlist 🎵",
    "Open YouTube 📺",
    "Open Spotify 🎧",
    "Are you flirting with me? 😏",
    "Tell me a secret 🤫",
    "Hype me up! 🔥",
    "Open GitHub 💻"
  )
  val scrollState = rememberScrollState()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(scrollState),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    prompts.forEach { prompt ->
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(18.dp))
          .background(Color(0xFF1A122B))
          .border(1.dp, Color(0xFF4A3469), RoundedCornerShape(18.dp))
          .clickable { onPromptSelected(prompt) }
          .padding(horizontal = 14.dp, vertical = 8.dp)
          .testTag("prompt_${prompt.take(6).trim()}")
      ) {
        Text(
          text = prompt,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFFE2D9F3)
        )
      }
    }
  }
}

@Composable
fun VoiceControlBar(
  state: AssistantState,
  continuousMode: Boolean,
  onMicClick: () -> Unit,
  onInterruptClick: () -> Unit
) {
  val isSpeaking = state == AssistantState.SPEAKING
  val isListening = state == AssistantState.LISTENING

  val infiniteTransition = rememberInfiniteTransition(label = "pulse_mic")
  val micPulse by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "mic_scale"
  )

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left: Interrupt Barge-In Button (Animated presence when Zoya is speaking)
    AnimatedVisibility(
      visible = isSpeaking,
      enter = fadeIn() + slideInVertically { it / 2 },
      exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFFFB703).copy(alpha = 0.2f))
            .border(1.5.dp, Color(0xFFFFB703), RoundedCornerShape(20.dp))
            .clickable { onInterruptClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("interrupt_button"),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Stop,
              contentDescription = "Interrupt",
              tint = Color(0xFFFFB703),
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Interrupt",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFFFB703)
            )
          }
        }
        Spacer(modifier = Modifier.width(18.dp))
      }
    }

    // Main Center Mic Button
    Box(
      contentAlignment = Alignment.Center
    ) {
      // Glow background ripple when listening
      if (isListening) {
        Box(
          modifier = Modifier
            .size(86.dp)
            .scale(micPulse)
            .clip(CircleShape)
            .background(Color(0xFF00E5FF).copy(alpha = 0.25f))
        )
      }

      Box(
        modifier = Modifier
          .size(72.dp)
          .scale(if (isListening) micPulse else 1f)
          .clip(CircleShape)
          .background(
            if (isListening) {
              Brush.linearGradient(
                colors = listOf(Color(0xFF00E5FF), Color(0xFF0070F3))
              )
            } else {
              Brush.linearGradient(
                colors = listOf(Color(0xFFE040FB), Color(0xFFFF4081))
              )
            }
          )
          .border(
            2.dp,
            if (isListening) Color(0xFF80D8FF) else Color(0xFFFF80AB),
            CircleShape
          )
          .clickable { onMicClick() }
          .testTag("main_mic_button"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
          contentDescription = if (isListening) "Stop listening" else "Start talking",
          tint = Color.White,
          modifier = Modifier.size(32.dp)
        )
      }
    }
  }
}
