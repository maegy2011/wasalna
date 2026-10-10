package com.app.wasalna.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.wasalna.data.auth.GoogleAccountHelper
import com.app.wasalna.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 10.13-exp: Google account sign-in — EXPERIMENTAL ONLY.
 *
 * Local-only: only display name + email are stored (DataStore). No tokens,
 * no server upload, no secrets. All failures surface as an Arabic error
 * string (one-shot [error]) — never a crash.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val accountHelper: GoogleAccountHelper,
) : ViewModel() {

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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    /** Builds the sign-in intent; returns null + error when unavailable. */
    fun signInIntent(): Intent? =
        runCatching { accountHelper.signInIntent() }
            .getOrElse {
                _error.value = "فشل تسجيل الدخول — تحقق من الإنترنت وحاول مجددًا"
                null
            }

    /** Resolves the sign-in Activity result. Never throws. */
    fun onSignInResult(data: Intent?) {
        when (val outcome = accountHelper.handleResult(data)) {
            is GoogleAccountHelper.SignInOutcome.Success -> {
                viewModelScope.launch {
                    runCatching {
                        settingsRepository.setGoogleAccount(
                            outcome.displayName,
                            outcome.email,
                        )
                    }.onFailure { _error.value = "تعذّر حفظ الحساب — حاول مجددًا" }
                }
            }
            is GoogleAccountHelper.SignInOutcome.Error -> {
                _error.value = outcome.message
            }
            GoogleAccountHelper.SignInOutcome.Cancelled -> {
                // User backed out — stay signed out, no message.
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                accountHelper.signOutLocal()
                settingsRepository.clearGoogleAccount()
            }
        }
    }
}
