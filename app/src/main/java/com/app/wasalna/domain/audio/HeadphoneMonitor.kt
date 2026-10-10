package com.app.wasalna.domain.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Headphone connection state, exposed as a StateFlow.
 *
 * Detection (no new permissions required for the core path):
 * - Wired: [AudioManager.getDevices] output types WIRED_HEADSET /
 *   WIRED_HEADPHONES (+ legacy isWiredHeadsetOn as a fallback).
 * - Bluetooth: A2DP / SCO / HEARING_AID output devices via getDevices,
 *   plus BluetoothAdapter profile state (A2DP / HEADSET) guarded by a
 *   SecurityException catch (BLUETOOTH_CONNECT may be denied).
 *
 * Live updates come from an [AudioManager.AudioDeviceCallback]
 * (API 23+; minSdk is 26) registered in [start] and unregistered in
 * [stop]. [refresh] re-queries synchronously from Dispatchers.IO.
 */
enum class HeadphoneState {
    NONE,
    WIRED,
    BLUETOOTH,
}

@Singleton
class HeadphoneMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(HeadphoneState.NONE)
    val state: StateFlow<HeadphoneState> = _state.asStateFlow()

    private var callback: AudioManager.AudioDeviceCallback? = null

    fun start() {
        if (callback != null) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am == null) {
            Log.w(TAG, "start: no AudioManager")
            return
        }
        val cb = object : AudioManager.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                scope.launch { refresh() }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                scope.launch { refresh() }
            }
        }
        callback = cb
        runCatching { am.registerAudioDeviceCallback(cb, null) }
            .onFailure { Log.w(TAG, "register callback failed: ${it.message}") }
        scope.launch { refresh() }
    }

    fun stop() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        callback?.let { cb ->
            runCatching { am?.unregisterAudioDeviceCallback(cb) }
        }
        callback = null
        scope.cancel()
    }

    suspend fun refresh(): HeadphoneState = withContext(Dispatchers.IO) {
        val next = querySync()
        _state.value = next
        next
    }

    /** Synchronous snapshot — safe to call on the alarm path (no I/O). */
    fun querySync(): HeadphoneState {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return HeadphoneState.NONE
        var sawWired = false
        var sawBt = false
        runCatching {
            for (device in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    -> sawWired = true
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_HEARING_AID,
                    -> sawBt = true
                }
            }
        }.onFailure { Log.w(TAG, "getDevices failed: ${it.message}") }
        if (!sawWired) {
            runCatching {
                @Suppress("DEPRECATION")
                if (am.isWiredHeadsetOn) sawWired = true
            }
        }
        if (!sawBt) {
            runCatching {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@runCatching
                if (adapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
                    BluetoothProfile.STATE_CONNECTED ||
                    adapter.getProfileConnectionState(BluetoothProfile.HEADSET) ==
                    BluetoothProfile.STATE_CONNECTED
                ) {
                    sawBt = true
                }
            }.onFailure {
                Log.w(TAG, "BT profile check failed: ${it.message}")
            }
        }
        return when {
            sawBt -> HeadphoneState.BLUETOOTH
            sawWired -> HeadphoneState.WIRED
            else -> HeadphoneState.NONE
        }
    }

    /**
     * The connected headphone output device to pin the alarm player to
     * via MediaPlayer.setPreferredDevice (API 28+). Null when nothing
     * is connected. Prefers wired over Bluetooth for lower latency.
     */
    fun findHeadphoneDevice(): AudioDeviceInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return null
        return runCatching {
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            } ?: outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "HeadphoneMonitor"
    }
}
