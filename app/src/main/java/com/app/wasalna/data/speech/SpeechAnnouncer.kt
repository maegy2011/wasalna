package com.app.wasalna.data.speech

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Voice announcements (user order Oct 2026): speaks arrival/proximity
 * prompts through the DEVICE's built-in TTS engine (usually Google) —
 * no network, no SDK, no new permission. Engine + voices are the user's
 * own system settings; we only pick the saved voice (Arabic preferred).
 *
 * Lifecycle: [init] once from WasalnaApp; [speak] is a safe no-op until
 * the engine reports SUCCESS. Callers gate on the persisted
 * voice-announce toggle themselves.
 */
object SpeechAnnouncer {
    private const val TAG = "SpeechAnnouncer"
    private const val UTTERANCE = "wasalna-voice"

    @Volatile
    var isReady = false
        private set

    private var tts: TextToSpeech? = null

    fun init(app: Application, savedVoiceName: String = "") {
        if (tts != null) return
        tts = TextToSpeech(app) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS engine init failed: $status")
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val arabic = Locale("ar")
            val availability = engine.isLanguageAvailable(arabic)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = arabic
            }
            if (savedVoiceName.isNotBlank()) {
                applyVoice(savedVoiceName)
            }
            isReady = true
            Log.i(TAG, "TTS ready (engine=${engine.defaultEngine}, lang=${engine.voice?.locale})")
        }
    }

    /** System voices, Arabic first (for the custom-voice picker). */
    fun availableVoices(): List<Voice> {
        val all = runCatching { tts?.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        return all.sortedWith(
            compareBy(
                { it.locale?.language != "ar" },
                { it.locale?.toString().orEmpty() },
                { it.name },
            ),
        )
    }

    fun applyVoice(name: String): Boolean {
        val engine = tts ?: return false
        val voice = runCatching {
            engine.voices?.firstOrNull { it.name == name }
        }.getOrNull() ?: return false
        return runCatching {
            engine.voice = voice
            true
        }.getOrDefault(false)
    }

    fun currentVoiceName(): String = runCatching { tts?.voice?.name.orEmpty() }.getOrDefault("")

    fun speak(text: String) {
        if (!isReady || text.isBlank()) return
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
        }.onFailure { Log.w(TAG, "speak failed: ${it.message}") }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        isReady = false
    }
}
