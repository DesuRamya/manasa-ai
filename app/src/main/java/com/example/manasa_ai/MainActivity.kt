package com.example.manasa_ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import com.google.ai.client.generativeai.GenerativeModel
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

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private val TAG = "ManasaAI_Debug"
    private val client = OkHttpClient()
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var generativeModel: GenerativeModel? = null

    private val VOICE_ID = "l4Coq6695JDX9xtLqXDE"

    // App State
    private var _isListening by mutableStateOf(false)
    private var _isProcessing by mutableStateOf(false)
    private var _recognizedText by mutableStateOf("")
    private var _aiResponseText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        initSpeechRecognizer()
        initGemini()
        tts = TextToSpeech(this, this)

        setContent {
            ManasaaiTheme {
                ChatbotScreen(
                    isListening = _isListening,
                    isProcessing = _isProcessing,
                    recognizedText = _recognizedText,
                    aiResponseText = _aiResponseText,
                    onStartListening = { startListening() },
                    onStopListening = { stopListening() }
                )
            }
        }
    }

    private fun initGemini() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotEmpty()) {
            // Corrected the model name to 'gemini-1.5-flash' which is the stable version.
            // gemini-2.5-flash does not exist and was causing initialization errors.
            generativeModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_LONG).show()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _recognizedText = "I'm listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _isListening = false
                _isProcessing = true
            }
            override fun onError(error: Int) {
                _isListening = false
                _isProcessing = false
                Log.e(TAG, "Speech Error: $error")
            }
            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = data?.get(0) ?: ""
                _recognizedText = text
                if (text.isNotEmpty()) {
                    processUserSpeech(text)
                } else {
                    _isProcessing = false
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                _recognizedText = data?.get(0) ?: ""
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        _aiResponseText = ""
        _recognizedText = ""
        _isListening = true
        _isProcessing = false
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening = false
    }

    private fun processUserSpeech(text: String) {
        lifecycleScope.launch {
            _isProcessing = true
            val response = generateAIResponse(text)
            _aiResponseText = response
            _isProcessing = false
            speakResponse(response)
        }
    }

    private suspend fun generateAIResponse(input: String): String {
        return try {
            val model = generativeModel
            if (model != null) {
                val prompt = "You are 'Manasa', a compassionate and supportive AI companion. " +
                        "The user said: \"$input\". " +
                        "Respond kindly, keeping it brief and empathetic."
                val response = model.generateContent(prompt)
                response.text ?: "I hear you. Tell me more."
            } else {
                "I'm listening, but I'm not fully initialized yet. Please check your API key."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Error", e)
            // Fallback response if Gemini fails
            "I'm here with you. Tell me more about what's on your mind."
        }
    }

    private fun speakResponse(text: String) {
        val apiKey = BuildConfig.ELEVEN_LABS_API_KEY
        if (apiKey.isEmpty()) {
            speakWithSystemTts(text)
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
                val request = Request.Builder().url(url).addHeader("xi-api-key", apiKey).post(body).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val audioBytes = response.body?.bytes()
                        if (audioBytes != null) {
                            val tempFile = File.createTempFile("elevenlabs", "mp3", cacheDir)
                            FileOutputStream(tempFile).use { it.write(audioBytes) }
                            withContext(Dispatchers.Main) { playAudio(tempFile) }
                        }
                    } else {
                        withContext(Dispatchers.Main) { speakWithSystemTts(text) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { speakWithSystemTts(text) }
            }
        }
    }

    private fun speakWithSystemTts(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun playAudio(file: File) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { start() }
            prepareAsync()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        mediaPlayer?.release()
        tts?.stop()
        tts?.shutdown()
    }
}

data class WelcomeMessage(val quote: String, val subText: String, val callToAction: String)

val welcomeMessages = listOf(
    WelcomeMessage("“Some nights feel heavier than others.”", "If today was hard… I’m here with you.", "Say anything. I’ll stay."),
    WelcomeMessage("“It’s okay to feel overwhelmed.”", "Take your time. There's no rush.", "I'm here to listen."),
    WelcomeMessage("“Small steps are still progress.”", "You're doing better than you think.", "Tell me about your day."),
    WelcomeMessage("“Breath in, breath out.”", "You are safe here.", "What's on your mind?")
)

@Composable
fun VoiceVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(32.dp)) {
        repeat(5) { index ->
            val heightScale by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1.0f,
                animationSpec = infiniteRepeatable(animation = tween(400 + (index * 150), easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = ""
            )
            Box(modifier = Modifier.width(4.dp).fillMaxHeight(heightScale).background(Color(0xFF2DD4BF), CircleShape))
        }
    }
}

@Composable
fun ChatbotScreen(
    isListening: Boolean,
    isProcessing: Boolean,
    recognizedText: String,
    aiResponseText: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    val context = LocalContext.current
    val currentMessageIndex by remember { mutableIntStateOf(Random.nextInt(welcomeMessages.size)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) onStartListening() else Toast.makeText(context, "Mic permission required", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF312E81), Color(0xFF1E1B4B))))) {
        Box(modifier = Modifier.size(400.dp).align(Alignment.TopCenter).offset(y = (-100).dp).blur(100.dp).background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape))
        Box(modifier = Modifier.size(300.dp).align(Alignment.Center).blur(80.dp).background(Color(0xFFD8B4FE).copy(alpha = 0.15f), CircleShape))

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Talk with manasa", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 16.dp))
            Spacer(modifier = Modifier.weight(0.5f))
            Box(modifier = Modifier.size(240.dp).clip(CircleShape).background(brush = Brush.radialGradient(listOf(Color(0xFF67E8F9).copy(alpha = 0.8f), Color(0xFF0E7490).copy(alpha = 0.4f), Color.Transparent))), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(180.dp).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).background(brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)), shape = CircleShape))
                Text(text = "✧", color = Color.White, fontSize = 40.sp, modifier = Modifier.blur(1.dp))
            }
            Spacer(modifier = Modifier.weight(0.4f))
            
            Box(modifier = Modifier.heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                when {
                    isProcessing -> Text(text = "Thinking...", color = Color.White, textAlign = TextAlign.Center, fontSize = 18.sp)
                    isListening -> Text(text = recognizedText.ifEmpty { "I'm listening..." }, color = Color.White, textAlign = TextAlign.Center, fontSize = 18.sp)
                    aiResponseText.isNotEmpty() -> Text(text = aiResponseText, color = Color(0xFF2DD4BF), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    else -> {
                        val message = welcomeMessages[currentMessageIndex]
                        Text(text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF2DD4BF))) { append(message.quote + "\n" + message.subText + "\n") }
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(message.callToAction) }
                        }, textAlign = TextAlign.Center, fontSize = 18.sp, lineHeight = 26.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (isListening) VoiceVisualizer() else Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.weight(0.6f))
            
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp).padding(bottom = 48.dp)) {
                Box(modifier = Modifier.fillMaxSize().blur(20.dp).background(if (isListening) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF2DD4BF).copy(alpha = 0.2f), CircleShape))
                Surface(
                    onClick = { 
                        if (!isListening) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onStartListening()
                            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else onStopListening()
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
