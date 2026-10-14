package com.app.wasalna.ui.screens.trips

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.annotation.ColorInt

/**
 * Share-as-image renderer — professional edition (user order Oct 2026).
 *
 * Framework Canvas + StaticLayout (bidi/RTL automatic). No window, no
 * composition — works from any thread, any context.
 *
 * Layout (1080px): deep-green gradient backdrop → white rounded card →
 * drawn destination marker → title → optional origin line → dashed
 * route flourish → stat columns → date → divider → watermark footer
 * (logo disc + app name, small, centered).
 */
object TripCardRenderer {
    private const val TAG = "TripCardRenderer"

    private const val IMG_W = 1080
    private const val MARGIN = 56
    private const val CARD_RADIUS = 64f
    private const val PAD = 76

    // Brand (mirror ui/theme/Color.kt).
    private const val FALLBACK_PRIMARY = 0xFF0D7C66
    private const val FALLBACK_DEEP = 0xFF0B3D33
    private const val FALLBACK_GOLD = 0xFFF2A007
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
        distanceLabel: String = "Distance",
        durationLabel: String = "Duration",
        originName: String? = null,
        avgSpeed: String? = null,
        avgSpeedLabel: String = "Avg",
    ): Bitmap? {
        return runCatching {
            val dm = context.resources.displayMetrics
            val d = dm.density
            fun dp(v: Int): Float = v * d

            @ColorInt val primary = FALLBACK_PRIMARY.toInt()
            @ColorInt val deep = FALLBACK_DEEP.toInt()
            @ColorInt val gold = FALLBACK_GOLD.toInt()
            @ColorInt val card = FALLBACK_CARD
            @ColorInt val onCard = FALLBACK_ON_CARD
            @ColorInt val muted = FALLBACK_MUTED

            val innerW = (IMG_W - MARGIN * 2 - PAD * 2).toInt()

            fun layout(
                text: String,
                sizeSp: Float,
                bold: Boolean,
                @ColorInt color: Int,
                width: Int = innerW,
                align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
            ): StaticLayout {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = sizeSp * dm.scaledDensity
                    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    this.color = color
                }
                return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(align)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(true)
                    .build()
            }

            val title = layout(destination.ifBlank { "?" }, 62f, true, onCard)
            val origin = originName?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { layout(it, 38f, false, muted) }
            val date = layout(dateText, 36f, false, muted)
            // Stat columns share the width evenly (2 or 3).
            val cols = if (avgSpeed != null) 3 else 2
            val colW = innerW / cols
            fun statValue(t: String) = layout(t, 52f, true, onCard, colW)
            fun statLabel(t: String) = layout(t, 30f, false, muted, colW)
            val distV = statValue(distanceKm)
            val distL = statLabel(distanceLabel.uppercase())
            val durV = statValue(durationMin)
            val durL = statLabel(durationLabel.uppercase())
            val spdV = avgSpeed?.let { statValue(it) }
            val spdL = avgSpeed?.let { statLabel(avgSpeedLabel.uppercase()) }
            val statsH = distV.height + dp(8) + distL.height
            // Watermark: real launcher icon + FULL app name in the app's
            // language (appName already resolves to the current locale).
            val logoSize = dp(64)
            val iconBmp = runCatching {
                val drawable = context.packageManager
                    .getApplicationIcon(context.packageName)
                val px = (logoSize * 2).toInt().coerceAtLeast(64)
                val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                val cv = Canvas(b)
                drawable.setBounds(0, 0, px, px)
                drawable.draw(cv)
                b
            }.getOrNull()
            val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 32f * dm.scaledDensity
                typeface = Typeface.DEFAULT_BOLD
                color = muted
            }
            val nameWidth = namePaint.measureText(appName).toInt().coerceAtLeast(1)
            val rowGap = dp(16)
            val markRowW = (if (iconBmp != null) logoSize + rowGap else 0f) + nameWidth
            val watermark = layout(appName, 32f, true, muted, nameWidth + 8)

            val markerR = dp(46)
            val flourishH = dp(120)
            val gapTitle = dp(20); val gapOrigin = dp(16)
            val gapFlourish = dp(28); val gapStats = dp(32)
            val gapDate = dp(24); val gapDivider = dp(28)
            val gapMark = dp(24)

            var contentH = PAD + markerR * 2 + gapTitle + title.height
            if (origin != null) contentH += gapOrigin + origin.height
            val markRowH = maxOf(if (iconBmp != null) logoSize else 0f, watermark.height.toFloat())
            contentH += gapFlourish + flourishH + gapStats + statsH +
                gapDate + date.height + gapDivider + dp(2) +
                gapMark + markRowH + PAD
            val imgH = (MARGIN * 2 + contentH).toInt()

            val bmp = Bitmap.createBitmap(IMG_W, imgH, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            // Backdrop: deep-green vertical gradient, full bleed.
            c.drawRect(
                0f, 0f, IMG_W.toFloat(), imgH.toFloat(),
                Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, imgH.toFloat(), deep, primary,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            // Soft gold arc accent behind the card top.
            c.drawCircle(
                IMG_W / 2f, MARGIN.toFloat() - dp(260), dp(340),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gold; alpha = 38
                },
            )

            val cardRect = RectF(
                MARGIN.toFloat(), MARGIN.toFloat(),
                (IMG_W - MARGIN).toFloat(), (imgH - MARGIN).toFloat(),
            )
            c.drawRoundRect(
                cardRect, CARD_RADIUS, CARD_RADIUS,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = card
                    setShadowLayer(dp(16), 0f, dp(8), 0x55000000)
                },
            )

            var y = (MARGIN + PAD).toFloat()
            val cx = IMG_W / 2f

            // Drawn destination marker (gold disc + white core + tiny stem).
            val my = y + markerR
            c.drawCircle(cx, my, markerR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
            c.drawCircle(
                cx, my, markerR * 0.42f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card },
            )
            y += markerR * 2 + gapTitle

            fun drawCentered(l: StaticLayout) {
                c.save()
                c.translate((MARGIN + PAD).toFloat(), y)
                l.draw(c)
                c.restore()
                y += l.height
            }
            drawCentered(title)
            if (origin != null) {
                y += gapOrigin
                drawCentered(origin)
            }

            // Dashed route flourish: start dot → curve → gold end dot.
            y += gapFlourish
            val fx0 = MARGIN + PAD.toFloat() + dp(40)
            val fx1 = (IMG_W - MARGIN - PAD).toFloat() - dp(40)
            val fy = y + flourishH / 2
            val path = Path().apply {
                moveTo(fx0, fy)
                cubicTo(
                    fx0 + (fx1 - fx0) * 0.3f, fy - flourishH * 0.55f,
                    fx0 + (fx1 - fx0) * 0.7f, fy + flourishH * 0.55f,
                    fx1, fy,
                )
            }
            c.drawPath(
                path,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = primary
                    style = Paint.Style.STROKE
                    strokeWidth = dp(7)
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(dp(16), dp(12)), 0f)
                },
            )
            c.drawCircle(
                fx0, fy, dp(13),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary },
            )
            c.drawCircle(
                fx1, fy, dp(15),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold },
            )
            c.drawCircle(
                fx1, fy, dp(6),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card },
            )
            y += flourishH + gapStats

            // Stat columns.
            fun drawCol(l: StaticLayout, col: Int) {
                c.save()
                c.translate((MARGIN + PAD).toFloat() + col * colW, y)
                l.draw(c)
                c.restore()
            }
            drawCol(distV, 0); drawCol(durV, 1)
            if (spdV != null) drawCol(spdV, 2)
            y += distV.height + dp(8)
            drawCol(distL, 0); drawCol(durL, 1)
            if (spdL != null) drawCol(spdL, 2)
            y += distL.height + gapDate

            drawCentered(date)
            y += gapDivider

            // Hairline divider.
            c.drawRect(
                (MARGIN + PAD).toFloat(), y,
                (IMG_W - MARGIN - PAD).toFloat(), y + dp(2),
                Paint().apply { color = 0xFFE2E5E0.toInt() },
            )
            y += dp(2) + gapMark

            // Watermark row: [real app logo] + full app name, centered.
            val rowH = maxOf(if (iconBmp != null) logoSize else 0f, watermark.height.toFloat())
            var rowX = cx - markRowW / 2
            if (iconBmp != null) {
                val dst = RectF(rowX, y + (rowH - logoSize) / 2, rowX + logoSize, y + (rowH + logoSize) / 2)
                // White bed so adaptive-icon shapes sit cleanly on white.
                c.drawCircle(
                    dst.centerX(), dst.centerY(), logoSize * 0.62f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card },
                )
                c.drawBitmap(iconBmp, null, dst, Paint(Paint.ANTI_ALIAS_FLAG))
                rowX += logoSize + rowGap
            }
            c.save()
            c.translate(rowX, y + (rowH - watermark.height) / 2)
            watermark.draw(c)
            c.restore()
            y += rowH

            bmp
        }.onFailure {
            Log.w(TAG, "render failed: ${it.message}")
        }.getOrNull()
    }
}
