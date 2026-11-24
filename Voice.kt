package com.axter.opu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** Always-on listening plus a calm spoken voice. Listening pauses while Opu talks. */
class Voice(private val ctx: Context, private val onText: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var enabled = false
    @Volatile var speaking = false
        private set

    init {
        tts = TextToSpeech(ctx) { s ->
            if (s == TextToSpeech.SUCCESS) {
                val t = tts!!
                t.language = Locale.getDefault()
                // Android voices carry no gender label, so prefer the network/natural ones and a soft pitch.
                t.voices?.filter { it.locale.language == Locale.getDefault().language }
                    ?.maxByOrNull { v -> (if (v.name.contains("f", true) && v.name.contains("-")) 1 else 0) + v.quality }
                    ?.let { runCatching { t.voice = it } }
                t.setSpeechRate(0.92f)
                t.setPitch(1.12f)
                ttsReady = true
            }
        }
    }

    fun start() { enabled = true; main.post { loop() } }

    fun stop() {
        enabled = false
        main.post { sr?.destroy(); sr = null }
        tts?.stop()
    }

    fun shutUp() { tts?.stop(); speaking = false }

    private fun loop() {
        if (!enabled || speaking) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try { sr?.startListening(i) } catch (e: Exception) { main.postDelayed({ loop() }, 1500) }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(r: Bundle?) {
            val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) onText(t)
            main.postDelayed({ loop() }, 250)
        }
        override fun onError(e: Int) {
            if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || e == SpeechRecognizer.ERROR_CLIENT) { sr?.cancel() }
            main.postDelayed({ loop() }, if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) 5000 else 600)
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    /** Speaks and returns when finished. */
    suspend fun say(text: String) {
        val clean = text.replace(Regex("[\\p{So}\\p{Cs}]"), "").trim()
        if (clean.isEmpty()) return
        var waited = 0
        while (!ttsReady && waited < 3000) { delay(100); waited += 100 }
        val t = tts ?: return
        speaking = true
        main.post { sr?.cancel() }
        suspendCancellableCoroutine<Unit> { c ->
            val id = "u" + System.nanoTime()
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) {}
                override fun onDone(u: String?) { if (u == id && c.isActive) c.resume(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(u: String?) { if (u == id && c.isActive) c.resume(Unit) }
            })
            t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
            c.invokeOnCancellation { t.stop() }
        }
        speaking = false
        main.postDelayed({ loop() }, 300)
    }
}
