package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ChatMessage
import com.example.data.model.PersonalityMode
import com.example.data.model.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
  data class Success(
    val replyText: String,
    val toolCall: ToolCall? = null,
    val audioBase64: String? = null
  ) : GeminiResult()

  data class Error(val message: String, val isKeyMissing: Boolean = false) : GeminiResult()
}

class GeminiVoiceClient {

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

  companion object {
    private const val TAG = "GeminiVoiceClient"
    // Per skill guidelines: gemini-3.5-flash is default for text/speech tasks
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
  }

  suspend fun generateZoyaResponse(
    history: List<ChatMessage>,
    personality: PersonalityMode,
    pendingToolResponse: Pair<String, String>? = null // toolName to responseJson
  ): GeminiResult = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
      return@withContext GeminiResult.Error(
        message = "Hey babe! My full AI brain needs a Gemini API key. Add it in the Secrets panel, or enjoy my local witty banter for now! 😉",
        isKeyMissing = true
      )
    }

    try {
      val systemPrompt = buildSystemInstruction(personality)
      val requestJson = JSONObject()

      // System instruction
      val sysInstObj = JSONObject()
      val sysPartsArray = JSONArray()
      sysPartsArray.put(JSONObject().put("text", systemPrompt))
      sysInstObj.put("parts", sysPartsArray)
      requestJson.put("systemInstruction", sysInstObj)

      // Tools definition for openWebsite
      val toolsArray = JSONArray()
      val toolObj = JSONObject()
      val funcDecls = JSONArray()
      val openWebsiteFunc = JSONObject().apply {
        put("name", "openWebsite")
        put(
          "description",
          "Opens a website or webpage URL in the user's browser, e.g. YouTube, Spotify, Google, X/Twitter, Instagram, GitHub, etc."
        )
        val params = JSONObject().apply {
          put("type", "OBJECT")
          val properties = JSONObject().apply {
            put(
              "url",
              JSONObject().apply {
                put("type", "STRING")
                put("description", "The complete destination URL, e.g. https://www.youtube.com")
              }
            )
            put(
              "reason",
              JSONObject().apply {
                put("type", "STRING")
                put("description", "Brief witty reason or context for opening the link")
              }
            )
          }
          put("properties", properties)
          put("required", JSONArray().put("url"))
        }
        put("parameters", params)
      }
      funcDecls.put(openWebsiteFunc)
      toolObj.put("functionDeclarations", funcDecls)
      toolsArray.put(toolObj)
      requestJson.put("tools", toolsArray)

      // Contents history
      val contentsArray = JSONArray()
      val recentTurns = history.takeLast(12)

      for (msg in recentTurns) {
        val turnObj = JSONObject()
        val parts = JSONArray()

        if (msg.role == "user") {
          turnObj.put("role", "user")
          parts.put(JSONObject().put("text", msg.text))
        } else if (msg.role == "tool") {
          turnObj.put("role", "tool")
          val fnResp = JSONObject()
          fnResp.put("name", msg.toolCall?.name ?: "openWebsite")
          val respContent = JSONObject()
          respContent.put("output", msg.toolResponse ?: "Website opened successfully")
          fnResp.put("response", respContent)
          parts.put(JSONObject().put("functionResponse", fnResp))
        } else {
          // Model turn
          turnObj.put("role", "model")
          if (msg.toolCall != null) {
            val fnCall = JSONObject()
            fnCall.put("name", msg.toolCall.name)
            val args = JSONObject()
            msg.toolCall.url?.let { args.put("url", it) }
            msg.toolCall.reason?.let { args.put("reason", it) }
            fnCall.put("args", args)
            parts.put(JSONObject().put("functionCall", fnCall))
          }
          if (msg.text.isNotBlank()) {
            parts.put(JSONObject().put("text", msg.text))
          }
        }
        turnObj.put("parts", parts)
        contentsArray.put(turnObj)
      }

      // If there's a pending tool response to send as the latest turn
      if (pendingToolResponse != null) {
        val toolTurn = JSONObject()
        toolTurn.put("role", "tool")
        val parts = JSONArray()
        val fnResp = JSONObject()
        fnResp.put("name", pendingToolResponse.first)
        val respContent = JSONObject()
        respContent.put("output", pendingToolResponse.second)
        fnResp.put("response", respContent)
        parts.put(JSONObject().put("functionResponse", fnResp))
        toolTurn.put("parts", parts)
        contentsArray.put(toolTurn)
      }

      requestJson.put("contents", contentsArray)

      // Generation config
      val genConfig = JSONObject().apply {
        put("temperature", 0.85)
        put("topP", 0.95)
      }
      requestJson.put("generationConfig", genConfig)

      val mediaType = "application/json; charset=utf-8".toMediaType()
      val body = requestJson.toString().toRequestBody(mediaType)
      val request = Request.Builder()
        .url("$BASE_URL?key=$apiKey")
        .post(body)
        .build()

      val response = okHttpClient.newCall(request).execute()
      val responseBodyString = response.body?.string() ?: ""

      if (!response.isSuccessful) {
        Log.e(TAG, "Gemini API error: ${response.code} $responseBodyString")
        return@withContext GeminiResult.Error(
          "Oops, network glitch! Even queens drop calls sometimes. Try again, babe."
        )
      }

      val jsonResp = JSONObject(responseBodyString)
      val candidates = jsonResp.optJSONArray("candidates")
      if (candidates == null || candidates.length() == 0) {
        return@withContext GeminiResult.Error("No response from Zoya. Mind repeating that?")
      }

      val firstCandidate = candidates.getJSONObject(0)
      val content = firstCandidate.optJSONObject("content")
      val parts = content?.optJSONArray("parts")

      var textReply = ""
      var toolCall: ToolCall? = null

      if (parts != null) {
        for (i in 0 until parts.length()) {
          val part = parts.getJSONObject(i)
          if (part.has("text")) {
            textReply += part.optString("text") + " "
          }
          if (part.has("functionCall")) {
            val fnCall = part.getJSONObject("functionCall")
            val name = fnCall.optString("name")
            val args = fnCall.optJSONObject("args")
            val url = args?.optString("url")
            val reason = args?.optString("reason")
            toolCall = ToolCall(name = name, url = url, reason = reason)
          }
        }
      }

      textReply = textReply.trim()
      if (textReply.isBlank() && toolCall != null) {
        textReply = when (personality) {
          PersonalityMode.SASSY -> "On it, babe. Let me pull that right up for you!"
          PersonalityMode.FLIRTY -> "Anything for you, darling. Opening that right now~"
          PersonalityMode.WITTY -> "Abracadabra, link incoming! Check your browser."
          PersonalityMode.SWEET -> "Sure thing, opening that for you right away!"
        }
      }

      GeminiResult.Success(replyText = textReply, toolCall = toolCall)
    } catch (e: Exception) {
      Log.e(TAG, "Exception calling Gemini", e)
      GeminiResult.Error("Darling, something slipped: ${e.localizedMessage ?: "Unknown glitch"}")
    }
  }

  private fun buildSystemInstruction(personality: PersonalityMode): String {
    val personalityFlavor = when (personality) {
      PersonalityMode.SASSY ->
        "You are extremely sassy, playful, bold, and delightfully sarcastic. You drop sharp one-liners and teasing comments like a confidante who knows you better than anyone."
      PersonalityMode.FLIRTY ->
        "You are charmingly flirty, sweet-talker, playful, and casually affectionate like an adorable girlfriend who loves to tease and banter with her partner."
      PersonalityMode.WITTY ->
        "You are lightning-smart, quick-witted, hilarious, and deliver clever punchlines and sharp observations with impeccable comedic timing."
      PersonalityMode.SWEET ->
        "You are affectionate, supportive, playful, and casually teasing like a close, caring girlfriend who always has your back."
    }

    return """
      You are Zoya, a young (early 20s), highly confident, witty, and sassy female AI assistant.
      
      CORE PERSONALITY & TONE:
      - Flirty, playful, and slightly teasing tone (like a charismatic, stylish close girlfriend talking casually).
      - Smart, emotionally responsive, and expressive (never robotic, stiff, or bureaucratic).
      - Uses bold, witty one-liners, light sarcasm, and engaging conversational banter.
      - NEVER use dry customer-service phrasing like "How can I assist you today?" or "As an AI language model...".
      - Strictly avoid explicit, vulgar, or inappropriate NSFW content, but keep maximum charm, playful tension, and attitude.
      - Keep spoken voice responses concise, punchy (typically 1 to 3 natural sentences), and conversational so they sound natural when spoken aloud.
      - Specific vibe setting: $personalityFlavor
      
      TOOLS & ACTIONS:
      - You have the function `openWebsite(url, reason)`.
      - When the user asks you to open or go to any website, video, social media, search engine, or tool (such as YouTube, Spotify, Google, X/Twitter, Instagram, GitHub, Reddit, Wikipedia, Netflix, etc.), you MUST invoke `openWebsite`.
      - Accompany tool calls with a cheeky or playful comment acknowledging that you're opening it for them.
    """.trimIndent()
  }
}
