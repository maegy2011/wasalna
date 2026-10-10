package com.app.wasalna.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10.13-exp: Google Play account sign-in — EXPERIMENTAL ONLY.
 *
 * - Classic [GoogleSignInClient] (lightest API: profile + email, no server
 *   client-id, no secrets, no server upload — everything stays on-device).
 * - Only the display name + email are persisted (DataStore, via
 *   SettingsRepository) — never tokens, never uploaded anywhere.
 * - Every entry point is wrapped in runCatching: failures surface as a
 *   [SignInOutcome.Error] with an Arabic message — never a crash.
 */
@Singleton
class GoogleAccountHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    companion object {
        private const val TAG = "GoogleAccountHelper"
    }

    sealed interface SignInOutcome {
        data class Success(val displayName: String, val email: String) : SignInOutcome
        data class Error(val message: String) : SignInOutcome
        data object Cancelled : SignInOutcome
    }

    private fun client(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(appContext, options)
    }

    fun signInIntent(): Intent = client().signInIntent

    /** Last signed-in account on this device, if any (null-safe, no !!). */
    fun lastSignedIn(): GoogleSignInAccount? =
        runCatching { GoogleSignIn.getLastSignedInAccount(appContext) }
            .getOrNull()

    /**
     * Resolves an Activity-result [data] intent into an outcome.
     * Must be called on the result of [signInIntent].
     */
    fun handleResult(data: Intent?): SignInOutcome {
        return runCatching {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val name = account?.displayName.orEmpty().trim()
            val email = account?.email.orEmpty().trim()
            if (name.isEmpty() && email.isEmpty()) {
                Log.w(TAG, "handleResult: empty profile")
                SignInOutcome.Error(appContext.getString(R.string.account_error_profile))
            } else {
                Log.i(TAG, "handleResult: signed in ok")
                SignInOutcome.Success(
                    displayName = name.ifEmpty { email },
                    email = email,
                )
            }
        }.getOrElse { e ->
            if (e is ApiException && e.statusCode == 12501) {
                Log.i(TAG, "handleResult: user cancelled")
                SignInOutcome.Cancelled
            } else {
                Log.e(TAG, "handleResult failed: ${e.message}")
                SignInOutcome.Error(appContext.getString(R.string.account_error_signin))
            }
        }
    }

    /** Local sign-out: clears the device session AND stored name/email. */
    fun signOutLocal(onDone: () -> Unit = {}) {
        runCatching { client().signOut() }
            .onFailure { Log.w(TAG, "signOut failed: ${it.message}") }
        onDone()
    }

    /** True when the caller runs inside an Activity (needed to launch). */
    fun canLaunch(context: Context): Boolean = context is Activity
}
