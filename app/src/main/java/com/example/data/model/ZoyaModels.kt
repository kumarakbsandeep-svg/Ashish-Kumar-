package com.example.data.model

import java.util.UUID

enum class AssistantState {
  IDLE,
  LISTENING,
  THINKING,
  SPEAKING,
  INTERRUPTED,
  ERROR
}

enum class PersonalityMode(val title: String, val emoji: String, val tagLine: String) {
  SASSY("Sassy & Bold", "💅", "Sharp wit, playful sarcasm, zero hesitation"),
  FLIRTY("Flirty & Playful", "😏", "Teasing charm, casual affection, playful banter"),
  WITTY("Witty & Clever", "✨", "Smart one-liners, fast comebacks, intellectual spark"),
  SWEET("Sweet & Teasing", "💖", "Warm girlfriend energy with a cheekily teasing twist")
}

data class ToolCall(
  val name: String,
  val url: String? = null,
  val reason: String? = null
)

data class ChatMessage(
  val id: String = UUID.randomUUID().toString(),
  val role: String, // "user", "model", "tool"
  val text: String,
  val toolCall: ToolCall? = null,
  val toolResponse: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)
