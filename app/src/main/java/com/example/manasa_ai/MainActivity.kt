package com.example.manasa_ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.manasa_ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var speechRecognizer: SpeechRecognizer? = null
    private val TAG = "ManasaAI_Debug"
    private val client = OkHttpClient()
    private var mediaPlayer: MediaPlayer? = null

    private val VOICE_ID = "yMCzJDgejP3dqCBHGGS4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        } else {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
        }

        setContent {
            ManasaaiTheme {
                ChatbotScreen(
                    onStartListening = { startListening() },
                    onStopListening = { stopListening() }
                )
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "Ready to speak") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "Started speaking") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.d(TAG, "Speech ended") }
            override fun onError(error: Int) { Log.e(TAG, "Recognizer error: $error") }
            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val userSpeech = data?.get(0) ?: ""
                if (userSpeech.isNotEmpty()) {
                    generateResponse(userSpeech)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
    }

    private fun generateResponse(userInput: String) {
        val responseText = "You said, $userInput. I'm here to support you."
        speakWithElevenLabs(responseText)
    }

    private fun speakWithElevenLabs(text: String) {
        val apiKey = BuildConfig.ELEVEN_LABS_API_KEY
        if (apiKey.isEmpty()) {
            Log.e(TAG, "ElevenLabs API Key is missing! Add eleven_labs_api_key=YOUR_KEY to local.properties")
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Missing API Key in local.properties", Toast.LENGTH_LONG).show()
            }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID"
                
                val json = JSONObject().apply {
                    put("text", text)
                    put("model_id", "eleven_monolingual_v1")
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.5)
                        put("similarity_boost", 0.5)
                    })
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("xi-api-key", apiKey)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Unexpected code $response")

                    val audioBytes = response.body?.bytes()
                    if (audioBytes != null) {
                        val tempFile = File.createTempFile("elevenlabs", "mp3", cacheDir)
                        FileOutputStream(tempFile).use { it.write(audioBytes) }
                        
                        withContext(Dispatchers.Main) {
                            playAudio(tempFile)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ElevenLabs Error", e)
            }
        }
    }

    private fun playAudio(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        mediaPlayer?.release()
    }
}

data class WelcomeMessage(
    val quote: String,
    val subText: String,
    val callToAction: String
)

val messages = listOf(
    WelcomeMessage("“Some nights feel heavier than others.”", "If today was hard… I’m here with you.", "Say anything. I’ll stay."),
    WelcomeMessage("“Konni raathrulu chaala bhaaranga anipisthaayi.”", "Eeroju kashtanga unte... nenu neetho unnaanu.", "Edhainaa cheppu. Nenu ikkade untaanu."),
    WelcomeMessage("“It’s okay to feel overwhelmed.”", "Take your time. There's no rush.", "I'm here to listen."),
    WelcomeMessage("“Otthidigaa anipinchinaa parvaledhu.”", "Neeku kaavalasinantha samayam theesuko. Ye thondhara ledhu.", "Nenu vinadaaniki siddhangaa unnaanu."),
    WelcomeMessage("“Small steps are still progress.”", "You're doing better than you think.", "Tell me about your day."),
    WelcomeMessage("“Prathi raathriki oka udayam untundhi.”", "Cheekati tharvatha velugu thappakunda vasthundhi.", "Nee baadhanu naatho panchuko."),
    WelcomeMessage("“Breath in, breath out.”", "You are safe here.", "What's on your mind?"),
    WelcomeMessage("“Okkasaari gaali peelchuko.”", "Ikkada nuvvu surakshithangaa unnaavu.", "Nee manasulo emundhi?")
)

@Composable
fun VoiceVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        repeat(5) { index ->
            val heightScale by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400 + (index * 150), easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            Box(modifier = Modifier.width(4.dp).fillMaxHeight(heightScale).background(Color(0xFF2DD4BF), CircleShape))
        }
    }
}

@Composable
fun ChatbotScreen(onStartListening: () -> Unit = {}, onStopListening: () -> Unit = {}) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var currentMessageIndex by remember { mutableIntStateOf(Random.nextInt(messages.size)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
            onStartListening()
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            brush = Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF1E1B4B)))
        )
    ) {
        Box(modifier = Modifier.size(400.dp).align(Alignment.TopCenter).offset(y = (-100).dp).blur(100.dp).background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape))
        Box(modifier = Modifier.size(300.dp).align(Alignment.Center).blur(80.dp).background(Color(0xFFD8B4FE).copy(alpha = 0.15f), CircleShape))

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                Text(text = "Talk with manasa", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.weight(0.5f))
            Box(modifier = Modifier.size(240.dp).clip(CircleShape).background(brush = Brush.radialGradient(listOf(Color(0xFF67E8F9).copy(alpha = 0.8f), Color(0xFF0E7490).copy(alpha = 0.4f), Color.Transparent))), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(180.dp).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).background(brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)), shape = CircleShape))
                Text(text = "✧", color = Color.White, fontSize = 40.sp, modifier = Modifier.blur(1.dp))
            }
            Spacer(modifier = Modifier.weight(0.4f))
            Crossfade(targetState = messages[currentMessageIndex], label = "message_fade") { message ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color(0xFF2DD4BF))) {
                            append(message.quote + "\n")
                            append(message.subText + "\n")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                            append(message.callToAction)
                        }
                    },
                    textAlign = TextAlign.Center, fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isListening) VoiceVisualizer() else Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.weight(0.6f))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.Center) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    Box(modifier = Modifier.fillMaxSize().blur(20.dp).background(if (isListening) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF2DD4BF).copy(alpha = 0.2f), CircleShape))
                    Surface(
                        onClick = { 
                            if (!isListening) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    isListening = true
                                    onStartListening()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                isListening = false
                                onStopListening()
                                currentMessageIndex = Random.nextInt(messages.size)
                            }
                        },
                        shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(72.dp).border(2.dp, if (isListening) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFF2DD4BF).copy(alpha = 0.5f), CircleShape)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(if (isListening) listOf(Color(0xFFFCA5A5), Color(0xFFEF4444)) else listOf(Color(0xFF5EEAD4), Color(0xFF0D9488)))), contentAlignment = Alignment.Center) {
                            Icon(imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}
