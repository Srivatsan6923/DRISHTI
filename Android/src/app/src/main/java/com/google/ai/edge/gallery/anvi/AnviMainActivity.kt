
package com.google.ai.edge.gallery.anvi

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.compose.material3.Scaffold
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageImage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.textandvoiceinput.HoldToDictateViewModel
import com.google.ai.edge.gallery.ui.llmchat.LlmAskImageViewModel
import com.google.ai.edge.gallery.ui.llmchat.rememberTtsManager
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import dagger.hilt.android.AndroidEntryPoint
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.collections.iterator
import kotlin.text.contains
import kotlin.text.toIntOrNull

@AndroidEntryPoint
class AnviMainActivity : ComponentActivity() {

  companion object {
    // Callback to stop the alarm from HTTP endpoint
    @Volatile
    var alarmStopCallback: (() -> Unit)? = null
  }

  private lateinit var server: SimpleHttpServer
  private val modelManagerViewModel: ModelManagerViewModel by viewModels()
  private var splashScreenAboutToExit: Boolean = false
  private var contentSet: Boolean = false
  private var receivedImagePath = mutableStateOf<String?>(null)
  private var showImageModal = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    fun setContent() {
      if (contentSet) {
        return
      }

      setContent {
        GalleryTheme {
          Surface(modifier = Modifier.fillMaxSize()) {
            AnviApp(
              modelManagerViewModel = modelManagerViewModel,
              receivedImagePath = receivedImagePath,
              showImageModal = showImageModal,
              onImageModalDismiss = { 
                showImageModal.value = false
                receivedImagePath.value = null
              }
            )

            // Fade out a "mask" that has the same color as the background of the splash screen
            // to reveal the actual app content.
            var startMaskFadeout by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { startMaskFadeout = true }
            AnimatedVisibility(
              !startMaskFadeout,
              enter = fadeIn(animationSpec = snap(0)),
              exit =
                fadeOut(animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)),
            ) {
              Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
              )
            }
          }
        }
      }

      @OptIn(ExperimentalApi::class)
      ExperimentalFlags.enableBenchmark = true

      contentSet = true
    }

    // Initialize tasks (simplified - no allowlist loading needed)
    modelManagerViewModel.loadModelAllowlist()

    // Show splash screen.
    val splashScreen = installSplashScreen()

    // Set the content when the system-provided splash screen is not shown.
    //
    // This is necessary on some Android versions where the splash screen is optimized away (e.g.,
    // after a force-quit) to ensure the main content is displayed immediately and correctly.
    lifecycleScope.launch {
      delay(1000)
      if (!splashScreenAboutToExit) {
        setContent()
      }
    }

    // Cross-fade transition from the splash screen to the main content.
    //
    // The logic performs the following key actions:
    // 1. Synchronizes Timing: It calculates the remaining duration of the default icon
    //    animation. It then delays its own animations to ensure the custom fade-out begins just
    //    before the original icon animation would have finished.
    // 2. Initiates a cross-fade:
    //    - Fade out the splash screen.
    //    - Fade in the main content.
    // 3. Cleans up: An `onEnd` listener on the fade-out animator calls
    //    `splashScreenView.remove()` to properly remove the splash screen from the view hierarchy
    //    once it's fully transparent.
    splashScreen.setOnExitAnimationListener { splashScreenView ->
      splashScreenAboutToExit = true

      val now = System.currentTimeMillis()
      val iconAnimationStartMs = splashScreenView.iconAnimationStartMillis
      val duration = splashScreenView.iconAnimationDurationMillis
      val fadeOut = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)
      fadeOut.interpolator = DecelerateInterpolator()
      fadeOut.duration = 300L
      fadeOut.doOnEnd { splashScreenView.remove() }
      lifecycleScope.launch {
        val setContentDelay = duration - (now - iconAnimationStartMs) - 300
        if (setContentDelay > 0) {
          delay(setContentDelay)
        }
        setContent()
        fadeOut.start()
      }
    }

    enableEdgeToEdge()


    server = SimpleHttpServer(5000, this)
    try {
      server.start()
      val ipAddress = getIpAddress()
      Log.d("HttpServer", "Server started at http://$ipAddress:5000")
    } catch (e: Exception) {
      Log.e("HttpServer", "Failed to start server: ${e.message}")
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Fix for three-button nav not properly going edge-to-edge.
      // See: https://issuetracker.google.com/issues/298296168
      window.isNavigationBarContrastEnforced = false
    }
    // Keep the screen on while the app is running for better demo experience.
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  @Composable
  fun Greeting(name: String) {
    Text(text = "Hello $name!")
  }

  fun updateImage(path: String) {
    runOnUiThread {
      receivedImagePath.value = path
      showImageModal.value = true
    }
  }

  private fun getIpAddress(): String {
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      for (networkInterface in interfaces) {
        val addresses = networkInterface.inetAddresses
        for (address in addresses) {
          if (!address.isLoopbackAddress && address is InetAddress) {
            val host = address.hostAddress
            if (host != null && !host.contains(":")) return host
          }
        }
      }
    } catch (e: Exception) {
      Log.e("HttpServer", "Error getting IP: ${e.message}")
    }
    return "localhost"
  }

  @Composable
  fun ImageDisplay(path: String?) {
    if (path == null) {
      Text("No image received yet.")
    } else {
      val file = File(path)
      if (file.exists()) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        bitmap?.let {
          Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Received Image",
            modifier = Modifier.fillMaxWidth().height(300.dp)
          )
          Text("File: ${file.name}\nSize: ${file.length()} bytes")
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
  }
}



class SimpleHttpServer(port: Int, private val context: AnviMainActivity) : NanoHTTPD(port) {

  override fun serve(session: IHTTPSession): Response {
    if (session.method == Method.POST && session.uri == "/upload") {
      return try {
        val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
        Log.d("HttpServer", "Content-Length: $contentLength")

        val body = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
          val read = session.inputStream.read(body, totalRead, contentLength - totalRead)
          if (read == -1) break
          totalRead += read
        }

        Log.d("HttpServer", "Read $totalRead bytes")

        val destFile = File(context.cacheDir, "received_image.jpg")
        destFile.writeBytes(body)

        context.updateImage(destFile.absolutePath)

        newFixedLengthResponse("Received $totalRead bytes successfully")
      } catch (e: Exception) {
        Log.e("HttpServer", "Error processing request", e)
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error")
      }
    }

    if (session.method == Method.POST && session.uri == "/stopalarm") {
      return try {
        Log.d("HttpServer", "Received /stopalarm request")
        
        // Call the alarm stop callback if it's registered
        val callback = AnviMainActivity.alarmStopCallback
        if (callback != null) {
          // Execute on main thread to ensure UI operations work correctly
          context.runOnUiThread {
            try {
              callback()
              Log.d("HttpServer", "Alarm stop callback executed successfully")
            } catch (e: Exception) {
              Log.e("HttpServer", "Error executing alarm stop callback", e)
            }
          }
          newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status":"ok","message":"Alarm stopped"}"""
          )
        } else {
          Log.w("HttpServer", "Alarm stop callback not registered")
          newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"status":"ok","message":"No alarm running"}"""
          )
        }
      } catch (e: Exception) {
        Log.e("HttpServer", "Error processing /stopalarm", e)
        newFixedLengthResponse(
          Response.Status.INTERNAL_ERROR,
          "application/json",
          """{"status":"error","message":"${e.message}"}"""
        )
      }
    }

    return newFixedLengthResponse("Server is running")
  }
}