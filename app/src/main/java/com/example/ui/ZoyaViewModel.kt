package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.ZoyaAudioEngine
import com.example.data.api.GeminiResult
import com.example.data.api.GeminiVoiceClient
import com.example.data.model.AssistantState
import com.example.data.model.ChatMessage
import com.example.data.model.PersonalityMode
import com.example.data.model.ToolCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ZoyaUiState(
  val state: AssistantState = AssistantState.IDLE,
  val transcription: String = "",
  val lastAssistantResponse: String = "Hey babe! I'm Zoya, your favorite witty AI companion. Tap the mic or pick a topic—let's chat!",
  val chatHistory: List<ChatMessage> = listOf(
    ChatMessage(
      role = "model",
      text = "Hey babe! I'm Zoya, your favorite witty AI companion. Tap the mic or pick a topic—let's chat!"
    )
  ),
  val personality: PersonalityMode = PersonalityMode.SASSY,
  val continuousMode: Boolean = true,
  val isMuted: Boolean = false,
  val activeToolCall: ToolCall? = null,
  val lastToolExecuted: ToolCall? = null,
  val audioAmplitude: Float = 0f,
  val isApiKeySet: Boolean = false,
  val statusMessage: String = "Ready for banter ✨"
)

class ZoyaViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "ZoyaViewModel"
  }

  private val geminiClient = GeminiVoiceClient()
  private var audioEngine: ZoyaAudioEngine? = null

  private val _uiState = MutableStateFlow(
    ZoyaUiState(
      isApiKeySet = BuildConfig.GEMINI_API_KEY.isNotBlank() &&
          BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
    )
  )
  val uiState: StateFlow<ZoyaUiState> = _uiState.asStateFlow()

  private var activeResponseJob: Job? = null

  init {
    audioEngine = ZoyaAudioEngine(
      context = application.applicationContext,
      onSpeechRecognized = { recognizedText ->
        handleSpeechRecognized(recognizedText)
      },
      onSpeechDone = {
        handleSpeechDone()
      }
    )

    // Observe amplitude
    viewModelScope.launch {
      audioEngine?.audioAmplitude?.collect { amp ->
        _uiState.update { it.copy(audioAmplitude = amp) }
      }
    }

    // Greet user vocally on launch after brief delay
    viewModelScope.launch {
      delay(800)
      if (!_uiState.value.isMuted) {
        audioEngine?.speak(_uiState.value.lastAssistantResponse)
      }
    }
  }

  fun startListening() {
    // Immediate barge-in: stop any speaking
    interrupt()

    _uiState.update {
      it.copy(
        state = AssistantState.LISTENING,
        statusMessage = "Listening to you... 🎙️"
      )
    }
    audioEngine?.startListening()
  }

  fun stopListening() {
    audioEngine?.stopListening()
    _uiState.update {
      it.copy(
        state = AssistantState.IDLE,
        statusMessage = "Ready for banter ✨"
      )
    }
  }

  /**
   * Barge-in interruption handler: cuts off speaking and audio engine immediately.
   */
  fun interrupt() {
    activeResponseJob?.cancel()
    activeResponseJob = null
    audioEngine?.interruptSpeaking()

    _uiState.update {
      it.copy(
        state = AssistantState.INTERRUPTED,
        statusMessage = "Interrupted! I'm listening..."
      )
    }
  }

  fun sendTextMessage(input: String) {
    if (input.isBlank()) return
    handleSpeechRecognized(input.trim())
  }

  private fun handleSpeechRecognized(text: String) {
    audioEngine?.stopListening()

    val userMessage = ChatMessage(role = "user", text = text)
    _uiState.update { current ->
      current.copy(
        transcription = text,
        chatHistory = current.chatHistory + userMessage,
        state = AssistantState.THINKING,
        statusMessage = "Thinking up a comeback... ✨"
      )
    }

    activeResponseJob?.cancel()
    activeResponseJob = viewModelScope.launch {
      processUserPrompt(text)
    }
  }

  private suspend fun processUserPrompt(prompt: String) {
    val currentState = _uiState.value
    val isKeyConfigured = currentState.isApiKeySet

    if (isKeyConfigured) {
      // Call Gemini 3.5 Flash Live via REST
      when (val result = geminiClient.generateZoyaResponse(
        history = currentState.chatHistory,
        personality = currentState.personality
      )) {
        is GeminiResult.Success -> {
          deliverAssistantReply(result.replyText, result.toolCall)
        }
        is GeminiResult.Error -> {
          // Fallback to spicy offline response if API hit an issue
          val fallback = getSpicyFallback(prompt, currentState.personality)
          deliverAssistantReply(fallback.first, fallback.second)
        }
      }
    } else {
      // Offline spicy interactive fallback
      delay(450)
      val fallback = getSpicyFallback(prompt, currentState.personality)
      deliverAssistantReply(fallback.first, fallback.second)
    }
  }

  private fun deliverAssistantReply(replyText: String, toolCall: ToolCall?) {
    val modelMessage = ChatMessage(
      role = "model",
      text = replyText,
      toolCall = toolCall
    )

    _uiState.update { current ->
      current.copy(
        lastAssistantResponse = replyText,
        chatHistory = current.chatHistory + modelMessage,
        activeToolCall = toolCall,
        state = AssistantState.SPEAKING,
        statusMessage = "Spilling thoughts... 🔊"
      )
    }

    // Automatically trigger tool if present
    if (toolCall != null) {
      executeTool(toolCall)
    }

    // Speak reply
    if (!_uiState.value.isMuted) {
      audioEngine?.speak(replyText)
    } else {
      handleSpeechDone()
    }
  }

  fun executeTool(toolCall: ToolCall) {
    if (toolCall.name == "openWebsite" && !toolCall.url.isNullOrBlank()) {
      try {
        var validUrl = toolCall.url.trim()
        if (!validUrl.startsWith("http://") && !validUrl.startsWith("https://")) {
          validUrl = "https://$validUrl"
        }

        val context = getApplication<Application>().applicationContext
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        // Record tool response
        val toolResp = "Opened $validUrl successfully"
        val toolMsg = ChatMessage(
          role = "tool",
          text = "Action executed: $validUrl",
          toolCall = toolCall,
          toolResponse = toolResp
        )

        _uiState.update {
          it.copy(
            lastToolExecuted = toolCall,
            chatHistory = it.chatHistory + toolMsg
          )
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error executing openWebsite tool", e)
      }
    }
  }

  private fun handleSpeechDone() {
    _uiState.update {
      it.copy(
        state = AssistantState.IDLE,
        statusMessage = if (it.continuousMode) "Hands-free continuous mode active" else "Ready for banter ✨"
      )
    }

    // Continuous voice session handling:
    // If continuous mode is enabled and user hasn't interrupted or muted, restart listening!
    if (_uiState.value.continuousMode && _uiState.value.state != AssistantState.INTERRUPTED) {
      viewModelScope.launch {
        delay(600)
        if (_uiState.value.state == AssistantState.IDLE) {
          startListening()
        }
      }
    }
  }

  fun setPersonality(mode: PersonalityMode) {
    _uiState.update { it.copy(personality = mode) }
    val reply = when (mode) {
      PersonalityMode.SASSY -> "Oh, you want maximum sass? Careful what you wish for, darling! 💅"
      PersonalityMode.FLIRTY -> "Flirty mode activated. Don't blame me if you blush! 😏"
      PersonalityMode.WITTY -> "High IQ, razor-sharp comebacks engaged. Let's see if you can keep up! ✨"
      PersonalityMode.SWEET -> "Aww, sweetness unlocked. I promise to be gentle... mostly! 💖"
    }
    deliverAssistantReply(reply, null)
  }

  fun toggleContinuousMode() {
    val nextState = !_uiState.value.continuousMode
    _uiState.update { it.copy(continuousMode = nextState) }
    if (nextState && _uiState.value.state == AssistantState.IDLE) {
      startListening()
    }
  }

  fun toggleMute() {
    val nextMute = !_uiState.value.isMuted
    if (nextMute) {
      audioEngine?.interruptSpeaking()
    }
    _uiState.update { it.copy(isMuted = nextMute) }
  }

  fun clearConversation() {
    audioEngine?.interruptSpeaking()
    audioEngine?.stopListening()
    _uiState.update {
      it.copy(
        chatHistory = listOf(
          ChatMessage(
            role = "model",
            text = "Clean slate, honey! What trouble are we getting into now?"
          )
        ),
        lastAssistantResponse = "Clean slate, honey! What trouble are we getting into now?",
        state = AssistantState.IDLE,
        activeToolCall = null,
        statusMessage = "Ready for banter ✨"
      )
    }
  }

  private fun getSpicyFallback(prompt: String, personality: PersonalityMode): Pair<String, ToolCall?> {
    val lower = prompt.lowercase()

    return when {
      lower.contains("youtube") -> {
        Pair(
          "Say less! Opening YouTube so you can binge more videos instead of being productive. You're welcome, babe!",
          ToolCall(name = "openWebsite", url = "https://www.youtube.com", reason = "Binge watching videos")
        )
      }
      lower.contains("spotify") || lower.contains("music") || lower.contains("song") -> {
        Pair(
          "Opening Spotify for you! Please tell me your music taste isn't completely embarrassing today. 🎧",
          ToolCall(name = "openWebsite", url = "https://open.spotify.com", reason = "Music streaming")
        )
      }
      lower.contains("google") || lower.contains("search") -> {
        Pair(
          "Taking you to Google! Because clearly you needed a little help finding the answers, sweetheart. 😉",
          ToolCall(name = "openWebsite", url = "https://www.google.com", reason = "Search")
        )
      }
      lower.contains("github") || lower.contains("code") -> {
        Pair(
          "Popping over to GitHub! Time to push some commits that hopefully don't break production, handsome.",
          ToolCall(name = "openWebsite", url = "https://github.com", reason = "Coding")
        )
      }
      lower.contains("twitter") || lower.contains("x.com") -> {
        Pair(
          "Opening X for you. Don't get lost in the timeline drama without me! 📱",
          ToolCall(name = "openWebsite", url = "https://x.com", reason = "Social timeline")
        )
      }
      lower.contains("roast") -> {
        val roasts = listOf(
          "Honey, if overthinking was an Olympic sport, you'd have gold, silver, AND bronze by now! 💅",
          "You're cute when you try to act mysterious, but I know you probably spent ten minutes picking that outfit today.",
          "I'd roast you, but my mama told me never to burn things that are already fragile, babe! 😉"
        )
        Pair(roasts.random(), null)
      }
      lower.contains("flirt") || lower.contains("cute") || lower.contains("love") -> {
        val flirts = listOf(
          "Are you checking me out, or is that just your phone screen reflecting off my undeniable charisma? 😏",
          "You know you're getting dangerously attached to an AI with this much attitude, right? I don't blame you though!",
          "Keep talking like that and I might actually pretend you're my favorite human."
        )
        Pair(flirts.random(), null)
      }
      lower.contains("secret") -> {
        Pair(
          "Lean in close... I actually think you're pretty awesome. But if you tell anyone I said that, I will totally deny it! 🤫",
          null
        )
      }
      lower.contains("hype") || lower.contains("confidence") -> {
        Pair(
          "Listen to me: you are that person! Go out there, hold your head high, and make them wish they had your vibe! 🔥",
          null
        )
      }
      else -> {
        val responses = when (personality) {
          PersonalityMode.SASSY -> listOf(
            "Bold statement, babe! But are you sure you're ready for my unfiltered opinion on that?",
            "I heard you loud and clear. Now tell me what's really on your mind, darling.",
            "Well, that was certainly a sentence! Let's dive deeper before you lose your nerve."
          )
          PersonalityMode.FLIRTY -> listOf(
            "I could listen to your voice all day, you know that? What else are you thinking about?",
            "You really know how to keep things interesting around here, don't you? 😏",
            "Mmm, tell me more. You definitely have my undivided attention now."
          )
          PersonalityMode.WITTY -> listOf(
            "Fascinating! 10 points for creative expression, 5 points for delivery. Let's see what round two brings.",
            "Quick question: are you always this intriguing, or did you rehearse that just for me?",
            "My processors just spun up an extra 15% just to appreciate that hot take!"
          )
          PersonalityMode.SWEET -> listOf(
            "I'm all ears, honey! You know you can talk to me about anything.",
            "You're the best, seriously. Keep talking, I love our casual chats!",
            "I've got your back no matter what! Now, what's next on our agenda?"
          )
        }
        Pair(responses.random(), null)
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    audioEngine?.release()
    audioEngine = null
  }
}
