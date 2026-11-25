package dev.uwiii.nav.audio

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class VoiceNavigator(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set

    var isMuted by mutableStateOf(prefs.getBoolean("is_muted", false))
        private set

    var selectedVoiceName by mutableStateOf(prefs.getString("selected_voice", null))
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

                // Gespeicherte Stimme wiederherstellen, falls vorhanden
                selectedVoiceName?.let { savedName ->
                    val matchedVoice = tts?.voices?.find { it.name == savedName }
                    if (matchedVoice != null) {
                        try {
                            tts?.voice = matchedVoice
                            Log.i("uWiiiNavAudio", "Gespeicherte Stimme geladen: ${matchedVoice.name}")
                        } catch (e: Exception) {
                            Log.e("uWiiiNavAudio", "Konnte gespeicherte Stimme nicht setzen", e)
                        }
                    }
                }

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

    fun getGermanVoices(): List<Voice> {
        return try {
            tts?.voices
                ?.filter { it.locale.language == "de" }
                ?.sortedBy { it.name }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun selectVoice(voice: Voice) {
        try {
            tts?.voice = voice
            selectedVoiceName = voice.name
            prefs.edit().putString("selected_voice", voice.name).apply()
            speak("Dies ist Ihre neue Navigationsstimme.", force = true)
        } catch (e: Exception) {
            Log.e("uWiiiNavAudio", "Fehler bei Stimmauswahl", e)
        }
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        if (isMuted) {
            stopSpeaking()
        }
        prefs.edit().putBoolean("is_muted", isMuted).apply()
        val msg = if (isMuted) "🔇 Sprachansagen stumm" else "🔊 Sprachansagen aktiv"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
        }
        return isMuted
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (text.isBlank()) return

        // Wenn stummgeschaltet, keine Ansagen abspielen (außer bei explizitem Test/Force)
        if (isMuted && !force) {
            Log.d("uWiiiNavAudio", "Stummgeschaltet: Ansage übersprungen '$text'")
            return
        }

        Log.i("uWiiiNavAudio", "speak() aufgerufen: '$text' (isInitialized=$isInitialized, isMuted=$isMuted)")

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
