package dev.uwiii.nav.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceNavigator(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.GERMAN)
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (!isInitialized || text.isBlank()) return
        val now = System.currentTimeMillis()

        // Vermeiden, dass derselbe Satz innerhalb von 8 Sekunden wiederholt wird
        if (!force && text == lastSpokenText && (now - lastSpokenTime) < 8000L) {
            return
        }

        lastSpokenText = text
        lastSpokenTime = now

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "uWiiiNavVoice")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
