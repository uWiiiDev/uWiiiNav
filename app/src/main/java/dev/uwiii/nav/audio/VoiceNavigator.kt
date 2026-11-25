package dev.uwiii.nav.audio

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale

class VoiceNavigator(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set

    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L
    private var pendingSpeech: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        Log.i("uWiiiNavAudio", "Initialisiere TextToSpeech...")
        tts = TextToSpeech(appContext) { status ->
            Log.i("uWiiiNavAudio", "TTS OnInitListener Status: $status (SUCCESS=${TextToSpeech.SUCCESS})")
            if (status == TextToSpeech.SUCCESS) {
                var langResult = tts?.setLanguage(Locale.GERMANY)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    langResult = tts?.setLanguage(Locale.GERMAN)
                }
                Log.i("uWiiiNavAudio", "Sprache Deutsch gesetzt, Ergebnis: $langResult")

                isInitialized = true

                // Falls während des Startens eine Ansage getriggert wurde: Jetzt ausführen
                pendingSpeech?.let { text ->
                    pendingSpeech = null
                    speak(text, force = true)
                }
            } else {
                Log.e("uWiiiNavAudio", "TTS-Initialisierung fehlgeschlagen mit Status: $status")
            }
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (text.isBlank()) return
        Log.i("uWiiiNavAudio", "speak() aufgerufen: '$text' (isInitialized=$isInitialized)")

        if (!isInitialized || tts == null) {
            pendingSpeech = text
            if (tts == null) {
                initTts()
            }
            return
        }

        val now = System.currentTimeMillis()
        if (!force && text == lastSpokenText && (now - lastSpokenTime) < 8000L) {
            Log.d("uWiiiNavAudio", "Wiederholte Ansage ignoriert: '$text'")
            return
        }

        lastSpokenText = text
        lastSpokenTime = now

        try {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "uWiiiNavVoice_${System.currentTimeMillis()}")
            Log.i("uWiiiNavAudio", "tts.speak() Rückgabewert: $res (SUCCESS=${TextToSpeech.SUCCESS})")
        } catch (e: Exception) {
            Log.e("uWiiiNavAudio", "Fehler beim Aufruf von tts.speak", e)
        }
    }

    fun testVoice() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, "🔊 Teste Sprachausgabe...", Toast.LENGTH_SHORT).show()
        }
        speak("Die Sprachausgabe von uWiiiNav funktioniert einwandfrei!", force = true)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
