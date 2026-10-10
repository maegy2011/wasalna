package com.app.wasalna.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.data.wear.WatchAlertSender
import com.app.wasalna.domain.audio.HeadphoneMonitor
import com.app.wasalna.domain.audio.HeadphoneState
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.utils.DndChoice
import com.app.wasalna.utils.DndPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phase 7: Settings state — elderly mode + alarm lead time via [SettingsRepository].
 * All Flows are DataStore-backed; setters run in viewModelScope.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val headphoneMonitor: HeadphoneMonitor,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    init {
        headphoneMonitor.start()
    }

    override fun onCleared() {
        headphoneMonitor.stop()
        super.onCleared()
    }

    val elderlyMode: StateFlow<Boolean> =
        settingsRepository.isElderlyMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val leadTimeMinutes: StateFlow<Int> =
        settingsRepository.alarmLeadTimeMinutes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 5,
        )

    val isPremium: StateFlow<Boolean> =
        settingsRepository.isPremium.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val silentAlarm: StateFlow<Boolean> =
        settingsRepository.isSilentAlarmEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    // Phase 10.4.3.1: premium custom ringtone URI ("" = system default).
    val customRingtoneUri: StateFlow<String> =
        settingsRepository.customRingtoneUri.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    fun setCustomRingtoneUri(uri: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setCustomRingtoneUri(uri) }
        }
    }

    fun setElderlyMode(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setElderlyMode(enabled) }
        }
    }

    fun setLeadTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAlarmLeadTimeMinutes(minutes.coerceIn(1, 30)) }
        }
    }

    fun setSilentAlarm(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setSilentAlarmEnabled(enabled) }
        }
    }

    // Headphone-only alerts (premium): alarm plays ONLY through connected
    // headphones; the speaker stays silent. Live connection state comes
    // from HeadphoneMonitor (NONE / WIRED / BLUETOOTH).
    val headphoneOnly: StateFlow<Boolean> =
        settingsRepository.isHeadphoneOnlyEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val headphoneState: StateFlow<HeadphoneState> = headphoneMonitor.state

    fun setHeadphoneOnly(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setHeadphoneOnlyEnabled(enabled) }
        }
    }

    // Phase 10.4.3.3: adaptive trigger distance per transport mode.
    val adaptiveAlarm: StateFlow<Boolean> =
        settingsRepository.isAdaptiveAlarmEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    fun setAdaptiveAlarm(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAdaptiveAlarmEnabled(enabled) }
        }
    }

    // Silent-mode (DND) bypass choice — persisted, default RESPECT (safe).
    // BYPASS needs Notification-policy access; without the grant every
    // fire-time path falls back to respect-DND (checked in AlarmActivity).
    val dndChoice: StateFlow<String> =
        settingsRepository.dndChoice.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DndChoice.RESPECT,
        )

    fun setDndChoice(choice: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setDndChoice(choice) }
        }
    }

    fun isDndPolicyAccessGranted(): Boolean =
        runCatching { DndPolicy.isPolicyAccessGranted(appContext) }
            .getOrDefault(false)

    // Phase 10.9.x: smart gradual alarm — vibration first, then sound ramps
    // up slowly. Disabled by default → legacy path untouched when off.
    val gradualEnabled: StateFlow<Boolean> =
        settingsRepository.isGradualAlarmEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val gradualVibrationSec: StateFlow<Int> =
        settingsRepository.gradualVibrationSec.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 10,
        )

    val gradualRampSec: StateFlow<Int> =
        settingsRepository.gradualRampSec.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 35,
        )

    val gradualStrength: StateFlow<Int> =
        settingsRepository.gradualVibrationStrength.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 3,
        )

    val gradualRingtoneUri: StateFlow<String> =
        settingsRepository.gradualRingtoneUri.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    fun setGradualEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setGradualAlarmEnabled(enabled) }
        }
    }

    fun setGradualVibrationSec(seconds: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setGradualVibrationSec(seconds) }
        }
    }

    fun setGradualRampSec(seconds: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setGradualRampSec(seconds) }
        }
    }

    fun setGradualStrength(level: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setGradualVibrationStrength(level) }
        }
    }

    fun setGradualRingtoneUri(uri: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setGradualRingtoneUri(uri) }
        }
    }

    // Phase 10.5.x Step 4: developer test-mode — force offline routing.
    val forceOffline: StateFlow<Boolean> =
        settingsRepository.isForceOffline.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun setForceOffline(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setForceOffline(enabled) }
        }
    }

    // Traffic overlay toggle — reuses SettingsRepository.isTrafficEnabled
    // (same DataStore flow as MapViewModel); two-way synced with the map FAB.
    val isTrafficEnabled: StateFlow<Boolean> =
        settingsRepository.isTrafficEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun setTrafficEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setTrafficEnabled(enabled) }
        }
    }

    // In-app language ("" = system, "ar", "en") — DataStore-backed.
    val appLanguage: StateFlow<String> =
        settingsRepository.appLanguageCode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    fun setAppLanguage(code: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAppLanguageCode(code) }
        }
    }

    // TRANSIT BYOK: user's own Routes API v2 key ("" = none → approximate).
    val routesApiKey: StateFlow<String> =
        settingsRepository.routesApiKey.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    fun setRoutesApiKey(key: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setRoutesApiKey(key) }
                .onFailure { Log.e(TAG, "routes key save failed: ${it.message}") }
        }
    }

    // Phase 10.10: watch-only destination alert (PREMIUM-gated) — the
    // phone stays silent and only the paired watch vibrates/rings.
    val watchOnlyAlert: StateFlow<Boolean> =
        settingsRepository.isWatchOnlyAlert.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun setWatchOnlyAlert(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setWatchOnlyAlert(enabled) }
        }
    }

    private val _watchStatus =
        MutableStateFlow(WatchAlertSender.WatchStatus.UNKNOWN)
    val watchStatus: StateFlow<WatchAlertSender.WatchStatus> =
        _watchStatus.asStateFlow()

    private val _watchTestBusy = MutableStateFlow(false)
    val watchTestBusy: StateFlow<Boolean> = _watchTestBusy.asStateFlow()

    fun refreshWatchStatus() {
        viewModelScope.launch {
            _watchStatus.value = WatchAlertSender.WatchStatus.CHECKING
            _watchStatus.value = withContext(Dispatchers.IO) {
                runCatching { WatchAlertSender.checkStatus(appContext) }
                    .getOrDefault(WatchAlertSender.WatchStatus.UNKNOWN)
            }
        }
    }

    fun sendWatchTestVibration(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _watchTestBusy.value = true
            val delivered = withContext(Dispatchers.IO) {
                runCatching { WatchAlertSender.sendTestVibration(appContext) }
                    .getOrDefault(false)
            }
            _watchTestBusy.value = false
            refreshWatchStatus()
            onResult(delivered)
        }
    }

    // Phase 10.4.3.4: gentle heads-up ~3 min before the main alarm.
    val proactiveAlert: StateFlow<Boolean> =
        settingsRepository.isProactiveAlertEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    fun setProactiveAlert(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setProactiveAlertEnabled(enabled) }
        }
    }

    // Pre-arrival NUDGE lead (minutes, 1..10, default 3) — StateFlow-exposed.
    val nudgeLeadMinutes: StateFlow<Int> =
        settingsRepository.nudgeLeadMinutes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 3,
        )

    fun setNudgeLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setNudgeLeadMinutes(minutes) }
        }
    }

    // Phase 10.13-exp: Google account (EXPERIMENTAL, local-only).
    // "" = signed out. Setters run in viewModelScope; never throw.
    val accountDisplayName: StateFlow<String> =
        settingsRepository.accountDisplayName.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    val accountEmail: StateFlow<String> =
        settingsRepository.accountEmail.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    fun setGoogleAccount(displayName: String, email: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setGoogleAccount(displayName, email) }
        }
    }

    fun clearGoogleAccount() {
        viewModelScope.launch {
            runCatching { settingsRepository.clearGoogleAccount() }
        }
    }

    // DYNAMIC WAKE-UP (التنبيه المرتبط بالزحام): DISTANCE (legacy default)
    // vs TIME (minutes-before-arrival). Persisted, StateFlow-exposed.
    val alarmMode: StateFlow<String> =
        settingsRepository.alarmMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "DISTANCE",
        )

    val alarmTimeMinutes: StateFlow<Int> =
        settingsRepository.alarmTimeMinutes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 5,
        )

    fun setAlarmMode(mode: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAlarmMode(mode) }
        }
    }

    fun setAlarmTimeMinutes(minutes: Int) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAlarmTimeMinutes(minutes) }
        }
    }

    companion object {
        private const val TAG = "SettingsVM"
    }
}
