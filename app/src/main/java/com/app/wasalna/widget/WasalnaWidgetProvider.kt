package com.app.wasalna.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.app.wasalna.MainActivity
import com.app.wasalna.R
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.domain.repository.TripRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phase 10.8.0d: glanceable widget (RemoteViews — no Glance dependency).
 * - Status line: active trip (destination + ETA) or idle hint.
 * - Two 48dp buttons: Home / Work one-tap → MainActivity deep link
 *   (EXTRA_QUICK_TRIP = "home" | "work"; Map handles when places are set).
 * Reads are bounded (2s timeout, Dispatchers.IO) — never ANR.
 */
class WasalnaWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun tripRepository(): TripRepository
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val status = runCatching { loadStatus(context) }.getOrDefault("Wasalna — set Home/Work in My Places")
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_trip)
            views.setTextViewText(R.id.widget_status, status)
            views.setContentDescription(R.id.widget_status, status)
            views.setOnClickPendingIntent(
                R.id.widget_btn_home,
                launchIntent(context, "home", id),
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_work,
                launchIntent(context, "work", id),
            )
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun launchIntent(context: Context, target: String, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_QUICK_TRIP
            putExtra(EXTRA_QUICK_TRIP, target)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getActivity(
            context,
            if (target == "home") 1001 else 1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun loadStatus(context: Context): String =
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(2_000L) {
                val entry = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java,
                )
                val trip = entry.tripRepository().activeTrip().first()
                if (trip != null) {
                    val eta = trip.etaMinutes?.let { " • ETA ~$it mins" } ?: ""
                    "🚕 ${trip.locationName}$eta"
                } else {
                    val home = entry.settingsRepository().homeAddress.first()
                    if (home.isNotBlank()) "Ready — Home: $home" else "Wasalna — set Home/Work in My Places"
                }
            } ?: "Wasalna"
        }

    companion object {
        const val ACTION_QUICK_TRIP = "com.app.wasalna.QUICK_TRIP"
        const val EXTRA_QUICK_TRIP = "quick_trip"
    }
}
