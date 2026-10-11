package com.app.wasalna.ui.screens.trips

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.annotation.ColorInt

/**
 * Share-as-image renderer (Oct 2026 fix).
 *
 * The old implementation measured a DETACHED ComposeView off-screen — a view
 * that was never attached to a window never runs composition, so measure
 * returned 0 (clamped to a 1×1 bitmap) or the draw produced a blank image.
 * That is why "share trip as image" silently produced nothing usable.
 *
 * This renderer draws the same summary card with framework Canvas +
 * StaticLayout (bidi/RTL handled by the text engine automatically). No
 * window, no composition, no attach — works from any thread, any context.
 * Brand greens match WasalnaGreen/WasalnaBackground; live theme colors are
 * preferred via attr lookup with those values as fallback.
 */
object TripCardRenderer {
    private const val TAG = "TripCardRenderer"

    private const val IMG_W = 1080
    private const val CARD_MARGIN = 48
    private const val CARD_RADIUS = 56f
    private const val PAD = 72

    // Brand fallbacks (mirror ui/theme/Color.kt).
    private const val FALLBACK_PRIMARY = 0xFF0D7C66
    private const val FALLBACK_BACKDROP = 0xFFF7F5F0.toInt()
    private const val FALLBACK_CARD = 0xFFFFFFFF.toInt()
    private const val FALLBACK_ON_CARD = 0xFF191C1B.toInt()
    private const val FALLBACK_MUTED = 0xFF5F6368.toInt()

    fun render(
        context: Context,
        destination: String,
        distanceKm: String,
        durationMin: String,
        dateText: String,
        appName: String,
    ): Bitmap? {
        return runCatching {
            val d = context.resources.displayMetrics.density
            fun dp(v: Int): Float = v * d

            @ColorInt val primary = themeColor(
                context,
                android.R.attr.colorPrimary,
                FALLBACK_PRIMARY.toInt(),
            )
            @ColorInt val backdrop = FALLBACK_BACKDROP
            @ColorInt val card = FALLBACK_CARD
            @ColorInt val onCard = FALLBACK_ON_CARD
            @ColorInt val muted = FALLBACK_MUTED

            val innerW = (IMG_W - CARD_MARGIN * 2 - PAD * 2).toInt()

            fun layout(
                text: String,
                sizeSp: Float,
                bold: Boolean,
                @ColorInt color: Int,
            ): StaticLayout {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = sizeSp * context.resources.displayMetrics.scaledDensity
                    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    this.color = color
                }
                return StaticLayout.Builder.obtain(text, 0, text.length, paint, innerW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1.15f)
                    .setIncludePad(true)
                    .build()
            }

            // Round-pushpin emoji (surrogate pair U+1F4CD).
            val pin = layout("\uD83D\uDCCD", 96f, false, primary)
            val title = layout(destination.ifBlank { "?" }, 64f, true, onCard)
            val stats = layout("$distanceKm   •   $durationMin", 54f, true, primary)
            val date = layout(dateText, 40f, false, muted)
            val brand = layout(appName, 36f, true, muted)

            val gap1 = dp(24); val gap2 = dp(40); val gap3 = dp(28); val gap4 = dp(20)
            val contentH = PAD + pin.height + gap1 + title.height + gap2 +
                stats.height + gap3 + date.height + gap4 + brand.height + PAD
            val imgH = (CARD_MARGIN * 2 + contentH).toInt()

            val bmp = Bitmap.createBitmap(IMG_W, imgH, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            c.drawColor(backdrop)
            val cardRect = RectF(
                CARD_MARGIN.toFloat(), CARD_MARGIN.toFloat(),
                (IMG_W - CARD_MARGIN).toFloat(), (imgH - CARD_MARGIN).toFloat(),
            )
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = card
                setShadowLayer(dp(12), 0f, dp(6), 0x33000000)
            }
            c.drawRoundRect(cardRect, CARD_RADIUS, CARD_RADIUS, cardPaint)

            var y = (CARD_MARGIN + PAD).toFloat()
            fun draw(l: StaticLayout) {
                c.save()
                c.translate((CARD_MARGIN + PAD).toFloat(), y)
                l.draw(c)
                c.restore()
                y += l.height
            }
            draw(pin); y += gap1
            draw(title); y += gap2
            draw(stats); y += gap3
            draw(date); y += gap4
            draw(brand)

            bmp
        }.onFailure {
            Log.w(TAG, "render failed: ${it.message}")
        }.getOrNull()
    }

    @ColorInt
    private fun themeColor(context: Context, attr: Int, @ColorInt fallback: Int): Int {
        return runCatching {
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(attr, tv, true)) tv.data else fallback
        }.getOrDefault(fallback)
    }
}
