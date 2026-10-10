package com.app.wasalna.ui.screens.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Smart gradual alarm engine (no new wake-locks — runs on the already-awake
 * AlarmActivity; the escalation coroutine is a child of [scope] and is
 * cancelled synchronously in [stop]).
 *
 * Escalation design (all timings/levels come from DataStore settings):
 * - Phase 1 (VIBRATION_ONLY): strong vibration pattern only, no sound,
 *   for [GradualAlarmConfig.vibrationOnlySec] (default 10 s). Wakes light
 *   sleepers without startling them or neighbours.
 * - Phase 2 (RAMPING): the selected ringtone starts looping at ~10% of
 *   STREAM_ALARM max and ramps linearly to max over
 *   [GradualAlarmConfig.rampSec] (default 35 s), stepping once per second.
 * - Phase 3 (FULL): full volume + aggressive vibration until dismissed.
 *
 * DND note: STREAM_ALARM obeys the system's Do-Not-Disturb alarm policy —
 * this controller does NOT request ACCESS_NOTIFICATION_POLICY and does not
 * change the interruption filter. Behaviour matches the legacy path:
 * alarms audibly ring when DND allows alarms, stay muted otherwise.
 */
data class GradualAlarmConfig(
    val vibrationOnlySec: Int = 10,
    val rampSec: Int = 35,
    val vibrationStrength: Int = 3,
)

enum class GradualPhase {
    IDLE,
    VIBRATION_ONLY,
    RAMPING,
    FULL,
    STOPPED,
}

class GradualAlarmController(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {

    private val appContext: Context = context.applicationContext ?: context

    private val _phase = MutableStateFlow(GradualPhase.IDLE)
    val phase: StateFlow<GradualPhase> = _phase.asStateFlow()

    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var savedAlarmVolume: Int = -1
    private var savedStream: Int = AudioManager.STREAM_ALARM

    /**
     * Starts the 3-phase escalation. Any previous run is stopped first.
     * [uri] is the already-resolved ringtone (gradual tone → custom tone →
     * system default); resolution lives in AlarmActivity.
     *
     * Headphone-only mode ([headphoneOnly] = true): the ramp runs on
     * STREAM_MUSIC with USAGE_MEDIA and the player is pinned to
     * [preferredDevice] (API 28+) so the phone speaker stays silent.
     * The phase timings ([config]) are honoured exactly like the
     * speaker path — the volume ramp respects the gradual settings.
     */
    fun start(
        uri: Uri,
        config: GradualAlarmConfig,
        preferredDevice: AudioDeviceInfo? = null,
        headphoneOnly: Boolean = false,
    ) {
        stop()
        _phase.value = GradualPhase.VIBRATION_ONLY
        job = scope.launch {
            runCatching {
                runEscalation(uri, config, preferredDevice, headphoneOnly)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "escalation failed: ${error.message}")
            }
        }
    }

    private suspend fun runEscalation(
        uri: Uri,
        config: GradualAlarmConfig,
        preferredDevice: AudioDeviceInfo?,
        headphoneOnly: Boolean,
    ) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = am
        val stream = if (headphoneOnly) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
        val maxVolume = am?.getStreamMaxVolume(stream) ?: FALLBACK_MAX_VOLUME
        savedStream = stream
        savedAlarmVolume = runCatching {
            am?.getStreamVolume(stream)
        }.getOrNull() ?: -1

        try {
            // Phase 1 — strong vibration only.
            startVibration(patternFor(config.vibrationStrength), repeat = 0)
            Log.i(TAG, "phase1: vibration-only ${config.vibrationOnlySec}s")
            delay(config.vibrationOnlySec.coerceIn(5, 20) * 1_000L)
            currentCoroutineContext().ensureActive()

            // Phase 2 — quiet looping sound, ramping up once per second.
            _phase.value = GradualPhase.RAMPING
            val steps = config.rampSec.coerceIn(15, 60)
            val startLevel = (maxVolume / 10).coerceAtLeast(1)
            preparePlayer(uri, preferredDevice, headphoneOnly)
            Log.i(TAG, "phase2: ramping $startLevel->$maxVolume over ${steps}s" +
                if (headphoneOnly) " (headphones only)" else "")
            for (step in 0..steps) {
                currentCoroutineContext().ensureActive()
                val level = startLevel + ((maxVolume - startLevel) * step / steps)
                runCatching {
                    am?.setStreamVolume(stream, level, 0)
                }
                if (step == steps) break
                delay(1_000L)
            }
            currentCoroutineContext().ensureActive()

            // Phase 3 — full volume + aggressive vibration until dismissed.
            _phase.value = GradualPhase.FULL
            runCatching {
                am?.setStreamVolume(stream, maxVolume, 0)
            }
            startVibration(AlarmActivity.AGGRESSIVE_PATTERN, repeat = 0)
            Log.i(TAG, "phase3: full volume + vibration until dismissed")
            delay(Long.MAX_VALUE)
        } finally {
            // Cancellation lands here: release the player + vibrator and
            // restore the user's volume. stop() also does this synchronously
            // so dismissal is immediate even before this runs.
            releasePlayer()
            cancelVibration()
            restoreVolume()
            if (_phase.value != GradualPhase.STOPPED) {
                _phase.value = GradualPhase.IDLE
            }
        }
    }

    /** MediaPlayer prepare touches storage — stays on Dispatchers.IO. */
    private suspend fun preparePlayer(
        uri: Uri,
        preferredDevice: AudioDeviceInfo? = null,
        headphoneOnly: Boolean = false,
    ) {
        withContext(Dispatchers.IO) {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (headphoneOnly) AudioAttributes.USAGE_MEDIA
                            else AudioAttributes.USAGE_ALARM,
                        )
                        .setContentType(
                            if (headphoneOnly) AudioAttributes.CONTENT_TYPE_MUSIC
                            else AudioAttributes.CONTENT_TYPE_SONIFICATION,
                        )
                        .build(),
                )
                setDataSource(appContext, uri)
                isLooping = true
                if (preferredDevice != null &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ) {
                    runCatching { setPreferredDevice(preferredDevice) }
                        .onFailure { Log.w(TAG, "setPreferredDevice failed: ${it.message}") }
                }
                prepare()
            }
            withContext(Dispatchers.Main.immediate) {
                currentCoroutineContext().ensureActive()
                player?.let {
                    runCatching {
                        if (it.isPlaying) it.stop()
                        it.release()
                    }
                }
                player = mp
                mp.start()
            }
        }
    }

    private fun startVibration(pattern: LongArray, repeat: Int) {
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator = vib
            if (vib == null || !vib.hasVibrator()) {
                Log.w(TAG, "vibrate: no vibrator")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, repeat)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "vibrate denied: ${e.message}")
        }
    }

    /** Wrist "Silent": kills the sound but keeps vibrating. */
    fun silenceSound() {
        Log.i(TAG, "silenceSound: sound off, vibration continues")
        releasePlayer()
    }

    /**
     * Cancels the escalation and stops ALL sound + vibration immediately,
     * then restores the user's STREAM_ALARM volume. Safe to call twice.
     */
    fun stop() {
        job?.cancel()
        job = null
        releasePlayer()
        cancelVibration()
        restoreVolume()
        if (_phase.value != GradualPhase.IDLE) {
            _phase.value = GradualPhase.STOPPED
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun releasePlayer() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "releasePlayer: ${e.message}")
        } finally {
            player = null
        }
    }

    private fun cancelVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "vibrate cancel: ${e.message}")
        } finally {
            vibrator = null
        }
    }

    private fun restoreVolume() {
        val am = audioManager ?: return
        try {
            if (savedAlarmVolume >= 0) {
                am.setStreamVolume(savedStream, savedAlarmVolume, 0)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "restoreVolume denied: ${e.message}")
        }
        savedAlarmVolume = -1
        savedStream = AudioManager.STREAM_ALARM
    }

    companion object {
        private const val TAG = "GradualAlarm"
        private const val FALLBACK_MAX_VOLUME = 7

        /**
         * Strong repeating pulse per strength level (1 = medium,
         * 2 = strong, 3 = maximum). Repeats from index 0 until cancelled.
         */
        fun patternFor(strength: Int): LongArray = when (strength.coerceIn(1, 3)) {
            1 -> longArrayOf(0, 400, 400)
            2 -> longArrayOf(0, 800, 300)
            else -> longArrayOf(0, 1000, 200)
        }
    }
}
