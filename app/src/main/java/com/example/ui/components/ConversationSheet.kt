package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.ToolCall

@Composable
fun ConversationSheet(
  chatHistory: List<ChatMessage>,
  onSendMessage: (String) -> Unit,
  onToolClick: (ToolCall) -> Unit,
  onClearChat: () -> Unit,
  modifier: Modifier = Modifier
) {
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(chatHistory.size) {
    if (chatHistory.isNotEmpty()) {
      listState.animateScrollToItem(chatHistory.size - 1)
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Color(0xFF130B22))
      .padding(16.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFF00E5FF))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Voice Transcript & Banter",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color(0xFFF3E8FF)
        )
      }

      IconButton(
        onClick = onClearChat,
        modifier = Modifier.testTag("clear_chat_button")
      ) {
        Icon(
          imageVector = Icons.Default.Clear,
          contentDescription = "Clear conversation",
          tint = Color(0xFF9E8FB2)
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Messages List
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(chatHistory, key = { it.id }) { msg ->
        MessageBubble(message = msg, onToolClick = onToolClick)
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Input bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = inputText,
        onValueChange = { inputText = it },
        placeholder = { Text("Ask Zoya anything...", color = Color(0xFF8C7D9E), fontSize = 14.sp) },
        modifier = Modifier
          .weight(1f)
          .testTag("chat_text_input"),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = Color.White,
          unfocusedTextColor = Color(0xFFE2D9F3),
          focusedContainerColor = Color(0xFF1E1333),
          unfocusedContainerColor = Color(0xFF1E1333),
          focusedBorderColor = Color(0xFFE040FB),
          unfocusedBorderColor = Color(0xFF3B2A56)
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
          onSend = {
            if (inputText.isNotBlank()) {
              onSendMessage(inputText)
              inputText = ""
            }
          }
        ),
        singleLine = true
      )

      Spacer(modifier = Modifier.width(8.dp))

      IconButton(
        onClick = {
          if (inputText.isNotBlank()) {
            onSendMessage(inputText)
            inputText = ""
          }
        },
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(
            Brush.linearGradient(
              colors = listOf(Color(0xFFE040FB), Color(0xFFFF4081))
            )
          )
          .testTag("send_message_button")
      ) {
        Icon(
          imageVector = Icons.Default.Send,
          contentDescription = "Send message",
          tint = Color.White
        )
      }
    }
  }
}

@Composable
fun MessageBubble(
  message: ChatMessage,
  onToolClick: (ToolCall) -> Unit
) {
  val isUser = message.role == "user"
  val isTool = message.role == "tool"

  val alignment = when {
    isUser -> Alignment.End
    else -> Alignment.Start
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = alignment
  ) {
    if (isTool) {
      // Special Tool Execution pill
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF1D2644))
          .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
          .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.OpenInBrowser,
          contentDescription = null,
          tint = Color(0xFF00E5FF),
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = message.toolResponse ?: message.text,
          fontSize = 12.sp,
          color = Color(0xFF80D8FF)
        )
      }
    } else {
      Box(
        modifier = Modifier
          .clip(
            RoundedCornerShape(
              topStart = 16.dp,
              topEnd = 16.dp,
              bottomStart = if (isUser) 16.dp else 4.dp,
              bottomEnd = if (isUser) 4.dp else 16.dp
            )
          )
          .background(
            if (isUser) {
              Brush.linearGradient(
                colors = listOf(Color(0xFF6C2BD9), Color(0xFF9333EA))
              )
            } else {
              Brush.linearGradient(
                colors = listOf(Color(0xFF271B3E), Color(0xFF221636))
              )
            }
          )
          .border(
            width = 1.dp,
            color = if (isUser) Color(0xFF9333EA) else Color(0xFF452F6B),
            shape = RoundedCornerShape(16.dp)
          )
          .padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        Column {
          Text(
            text = if (isUser) "You" else "Zoya",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isUser) Color(0xFFD8B4FE) else Color(0xFFFF60A8)
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = message.text,
            fontSize = 14.sp,
            color = Color(0xFFF3E8FF),
            lineHeight = 20.sp
          )
        }
      }

      // If this model message contained a tool call, display tool action card
      message.toolCall?.let { tool ->
        Spacer(modifier = Modifier.height(6.dp))
        ToolCallBadge(tool = tool, onClick = { onToolClick(tool) })
      }
    }
  }
}

@Composable
fun ToolCallBadge(
  tool: ToolCall,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF1E1033))
      .border(1.dp, Color(0xFFE040FB).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = Icons.Default.OpenInBrowser,
      contentDescription = "Tool Call",
      tint = Color(0xFFE040FB),
      modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(
        text = "Action: ${tool.name}",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFE040FB)
      )
      tool.url?.let { url ->
        Text(
          text = url,
          fontSize = 11.sp,
          color = Color(0xFF00E5FF),
          maxLines = 1
        )
      }
    }
    Spacer(modifier = Modifier.width(8.dp))
    Button(
      onClick = onClick,
      modifier = Modifier
        .height(30.dp)
        .testTag("tool_launch_button"),
      colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFFE040FB)
      ),
      shape = RoundedCornerShape(8.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    ) {
      Text("Open", fontSize = 11.sp, color = Color.White)
    }
  }
}
