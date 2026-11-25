package dev.uwiii.nav.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceNavigator(context: Context) {
    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set

    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L
    private var pendingSpeech: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Deutsche Sprachausgabe einstellen
                var langResult = tts?.setLanguage(Locale.GERMANY)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    langResult = tts?.setLanguage(Locale.GERMAN)
                }

                // Audio-Attribute für Navigationsansagen konfigurieren
                // Sorgt dafür, dass Radio/Spotify im Auto geduckt wird (leiser wird)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                isInitialized = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED

                Log.d("uWiiiNav", "TTS Initialized: $isInitialized, status: $status")

                // Falls während der Initialisierung bereits gesprochen werden sollte: Jetzt nachholen!
                pendingSpeech?.let { text ->
                    pendingSpeech = null
                    speak(text, force = true)
                }
            } else {
                Log.e("uWiiiNav", "TTS Init failed with status: $status")
            }
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (text.isBlank()) return

        if (!isInitialized) {
            // Ansage merken und abspielen, sobald TTS fertig geladen ist
            pendingSpeech = text
            return
        }

        val now = System.currentTimeMillis()

        // Vermeiden, dass derselbe Satz innerhalb von 8 Sekunden wiederholt wird
        if (!force && text == lastSpokenText && (now - lastSpokenTime) < 8000L) {
            return
        }

        lastSpokenText = text
        lastSpokenTime = now

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "uWiiiNavVoice_${System.currentTimeMillis()}")
    }

    fun testVoice() {
        speak("Die Sprachausgabe von uWiiiNav funktioniert einwandfrei!", force = true)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
