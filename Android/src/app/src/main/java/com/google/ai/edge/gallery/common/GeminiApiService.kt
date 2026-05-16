
package com.google.ai.edge.gallery.common

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "AGGeminiApiService"
// 2.5 flash lite
private const val GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:streamGenerateContent"

data class GeminiRequest(
  val contents: List<ContentPart>,
  val generationConfig: GenerationConfig? = null
)

data class ContentPart(
  val role: String = "user",
  val parts: List<Part>
)

data class Part(
  val text: String? = null,
  val inlineData: InlineData? = null
)

data class InlineData(
  val mimeType: String,
  val data: String
)

data class GenerationConfig(
  val temperature: Double? = null,
  val topK: Int? = null,
  val topP: Double? = null,
  val maxOutputTokens: Int? = null
)

data class GeminiResponse(
  val candidates: List<Candidate>? = null,
  val error: Error? = null
)

data class Candidate(
  val content: Content? = null
)

data class Content(
  val parts: List<PartResponse>? = null
)

data class PartResponse(
  val text: String? = null
)

data class Error(
  val message: String? = null,
  val code: Int? = null
)

/**
 * Service for interacting with the Gemini API.
 */
object GeminiApiService {
  /**
   * Streams a response from the Gemini API with support for text and images.
   * @param apiKey The Gemini API key
   * @param textInput The text input
   * @param images List of images to include
   * @param temperature Temperature for generation
   * @param topK TopK for generation
   * @param topP TopP for generation
   * @param maxTokens Maximum output tokens
   * @param onChunk Callback for each chunk of the response
   * @return true if successful, false otherwise
   */
  suspend fun streamResponse(
      apiKey: String,
      textInput: String,
      images: List<Bitmap> = listOf(),
      temperature: Float = 1.0f,
      topK: Int = 64,
      topP: Float = 0.95f,
      maxTokens: Int = 4096,
      onChunk: (String) -> Unit,
  ): Boolean = withContext(Dispatchers.IO) {
      try {
          if (apiKey.isEmpty()) {
              Log.e(TAG, "API key is empty")
              return@withContext false
          }

          if (textInput.trim().isEmpty() && images.isEmpty()) {
              Log.e(TAG, "No text input or images provided")
              return@withContext false
          }

          // Build request parts - images first, then text
          val parts = mutableListOf<Part>()
          
          // Add images
          for (image in images) {
              val imageData = imageToBase64(image)
              parts.add(
                  Part(
                      inlineData = InlineData(
                          mimeType = "image/png",
                          data = imageData
                      )
                  )
              )
          }
          
          // Add text (if provided)
          if (textInput.trim().isNotEmpty()) {
              parts.add(Part(text = textInput))
          }

          val request = GeminiRequest(
              contents = listOf(ContentPart(parts = parts)),
              generationConfig = GenerationConfig(
                  temperature = temperature.toDouble(),
                  topK = topK,
                  topP = topP.toDouble(),
                  maxOutputTokens = maxTokens
              )
          )

          // FIX 1: Add "&alt=sse" to the URL to force Server-Sent Events format
          val url = URL("$GEMINI_API_BASE_URL?key=$apiKey&alt=sse")

          val connection = url.openConnection() as HttpURLConnection
          connection.requestMethod = "POST"
          connection.setRequestProperty("Content-Type", "application/json")
          connection.doOutput = true

          val gson = Gson()
          val requestJson = gson.toJson(request)
          Log.d(TAG, "Sending request to Gemini API: $url")

          connection.outputStream.use { output ->
              output.write(requestJson.toByteArray(Charsets.UTF_8))
          }

          val responseCode = connection.responseCode
          if (responseCode != HttpURLConnection.HTTP_OK) {
              val errorStream = connection.errorStream
              val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
              Log.e(TAG, "API request failed with code $responseCode: $errorResponse")
              return@withContext false
          }

          var chunkCount = 0
          connection.inputStream.bufferedReader().use { reader ->
              reader.lineSequence().forEach { line ->
                  // FIX 2: Better logging to see what is actually coming back
                  if (!line.startsWith("data: ") && line.isNotBlank()) {
                      Log.d(TAG, "Ignored raw line: $line")
                  }

                  if (line.startsWith("data: ")) {
                      val jsonData = line.removePrefix("data: ").trim()
                      if (jsonData == "[DONE]") return@forEach

                      if (jsonData.isNotEmpty()) {
                          try {
                              val response = gson.fromJson(jsonData, GeminiResponse::class.java)

                              response.candidates?.firstOrNull()?.content?.parts?.forEach { part ->
                                  part.text?.let { text ->
                                      chunkCount++
                                      onChunk(text)
                                  }
                              }
                          } catch (e: Exception) {
                              Log.e(TAG, "Error parsing chunk: ${e.message}")
                          }
                      }
                  }
              }
          }
          Log.d(TAG, "Stream complete. Chunks received: $chunkCount")
          true
      } catch (e: Exception) {
          Log.e(TAG, "Error calling Gemini API", e)
          false
      }
  }

  private fun imageToBase64(bitmap: Bitmap): String {
      val outputStream = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
      val imageBytes = outputStream.toByteArray()
      return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
  }
}
