package com.example.livetranscriber

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnCopy: Button
    private lateinit var etSpeaker: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvLive: TextView
    private lateinit var container: LinearLayout
    private lateinit var scrollView: ScrollView

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private val fullText = StringBuilder()

    private val file by lazy {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        File(getExternalFilesDir(null), "meeting_$name.txt")
    }

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
            else Toast.makeText(this, "دسترسی میکروفون لازم است", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        btnCopy = findViewById(R.id.btnCopy)
        etSpeaker = findViewById(R.id.etSpeaker)
        tvStatus = findViewById(R.id.tvStatus)
        tvLive = findViewById(R.id.tvLive)
        container = findViewById(R.id.transcriptContainer)
        scrollView = findViewById(R.id.scrollView)

        btnToggle.setOnClickListener {
            if (isListening) stopListening()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("transcript", fullText.toString()))
            Toast.makeText(this, "متن کپی شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "تشخیص گفتار روی این دستگاه فعال نیست", Toast.LENGTH_LONG).show()
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }

        isListening = true
        btnToggle.text = "توقف"
        tvStatus.text = "در حال گوش دادن..."
        listenOnce()
    }

    private fun stopListening() {
        isListening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        btnToggle.text = "شروع"
        tvStatus.text = "ذخیره شد: ${file.name}"
        tvLive.text = ""
    }

    private fun listenOnce() {
        if (!isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun restartWithDelay() {
        handler.postDelayed({ listenOnce() }, 300)
    }

    /** نتیجه نهایی یک جمله: در فایل و صفحه ثبت می‌شود. */
    private fun commitLine(text: String) {
        val speaker = etSpeaker.text.toString().trim()
        val time = timeFmt.format(Date())
        val prefix = if (speaker.isEmpty()) "" else "$speaker: "
        val line = "[$time] $prefix$text"

        file.appendText(line + "\n")
        fullText.append(line).append("\n")

        val tv = TextView(this).apply {
            this.text = line
            textSize = 16f
            textDirection = TextView.TEXT_DIRECTION_RTL
            setPadding(0, 6, 0, 6)
        }
        container.addView(tv)
        tvLive.text = ""
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            runOnUiThread { tvLive.text = text ?: "" }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            runOnUiThread {
                if (!text.isNullOrEmpty()) commitLine(text)
            }
            restartWithDelay()
        }

        override fun onError(error: Int) {
            runOnUiThread { tvLive.text = "" }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartWithDelay()
                else -> {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "خطا: $error", Toast.LENGTH_SHORT).show()
                    }
                    restartWithDelay()
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
