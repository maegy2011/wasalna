package com.app.wasalna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.ui.navigation.NavGraph
import com.app.wasalna.ui.theme.WasalnaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Phase 7: collect elderly mode so WasalnaTheme recomposes on toggle.
            val elderlyMode by settingsRepository.isElderlyMode
                .collectAsState(initial = false)
            // Screen-density adaptivity (Oct 2026): width class drives
            // adaptive dimens + column counts; recomposes on rotation.
            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSizeClass = calculateWindowSizeClass(this)
            WasalnaTheme(
                elderlyMode = elderlyMode,
                widthClass = windowSizeClass.widthSizeClass,
            ) {
                NavGraph()
            }
        }
    }
}
