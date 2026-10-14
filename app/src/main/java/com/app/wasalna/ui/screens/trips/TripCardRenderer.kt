package com.app.wasalna.ui.screens.trips

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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
 * [optional full-camera map snapshot header with the real route] →
 * marker + title → origin → [dashed flourish when no map] → side
 * stat panel (3 lines, language side) → date → divider → watermark footer (real logo + full app
 * name in the app's language, small, centered).
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
        mapImage: Bitmap? = null,
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

            val hasMap = mapImage != null
            val innerW = (IMG_W - MARGIN * 2 - PAD * 2).toInt()
            val mapW = (IMG_W - MARGIN * 2).toFloat()
            val mapH = if (hasMap) mapW * 9f / 16f else 0f

            fun layout(
                text: String,
                sizeSp: Float,
                bold: Boolean,
                @ColorInt color: Int,
                width: Int = innerW,
                align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
                maxLines: Int = Int.MAX_VALUE,
            ): StaticLayout {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = sizeSp * dm.scaledDensity
                    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    this.color = color
                }
                val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(align)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(true)
                if (maxLines < Int.MAX_VALUE) {
                    b.setMaxLines(maxLines)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END)
                }
                return b.build()
            }

            // Hidden default box (user order Oct 2026): titles live in an
            // invisible full-width box, max 2 lines — the font shrinks
            // dynamically until the whole title fits, then ellipsizes.
            fun fitLayout(
                text: String,
                startSp: Float,
                minSp: Float,
                bold: Boolean,
                @ColorInt color: Int,
            ): StaticLayout {
                var sp = startSp
                while (sp > minSp) {
                    val l = layout(text, sp, bold, color, maxLines = 2)
                    if (l.lineCount <= 2) return l
                    sp -= 4f
                }
                return layout(text, minSp, bold, color, maxLines = 2)
            }

            // Side stat panel (user order Oct 2026): the stats stack as
            // three lines on the START side per app language (RTL → right,
            // LTR → left) — gold bullet + big value + small label in one
            // line, hairline separators, tinted panel, green accent edge.
            val rtl = context.resources.configuration.layoutDirection ==
                android.view.View.LAYOUT_DIRECTION_RTL
            val panelPad = dp(28)
            val panelInnerW = (innerW - panelPad * 2).toInt().coerceAtLeast(100)
            fun statLine(value: String, label: String): StaticLayout {
                val labelUp = label.uppercase()
                val span = android.text.SpannableString("●  $value   $labelUp")
                val vStart = 3
                val vEnd = vStart + value.length
                val lStart = vEnd + 3
                span.setSpan(
                    android.text.style.ForegroundColorSpan(gold),
                    0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.setSpan(
                    android.text.style.AbsoluteSizeSpan(52, true),
                    vStart, vEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    vStart, vEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.setSpan(
                    android.text.style.ForegroundColorSpan(onCard),
                    vStart, vEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.setSpan(
                    android.text.style.AbsoluteSizeSpan(30, true),
                    lStart, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.setSpan(
                    android.text.style.ForegroundColorSpan(muted),
                    lStart, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
                return StaticLayout.Builder.obtain(span, 0, span.length, paint, panelInnerW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(true)
                    .setMaxLines(1)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()
            }
            val statLayouts = buildList {
                add(statLine(distanceKm, distanceLabel))
                add(statLine(durationMin, durationLabel))
                if (avgSpeed != null) add(statLine(avgSpeed, avgSpeedLabel))
            }
            val rowGapS = dp(16)
            val statsH = panelPad * 2 +
                statLayouts.sumOf { it.height.toInt() } +
                rowGapS * (statLayouts.size - 1) +
                dp(2) * (statLayouts.size - 1)
            val title = fitLayout(destination.ifBlank { "?" }, 62f, 34f, true, onCard)
            val origin = originName?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { fitLayout(it, 38f, 26f, false, muted) }
            val date = layout(dateText, 36f, false, muted)
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
            val markRowH = maxOf(if (iconBmp != null) logoSize else 0f, watermark.height.toFloat())

            var contentH = title.height
            contentH += if (hasMap) {
                mapH + dp(28)
            } else {
                PAD + markerR * 2 + gapTitle
            }
            if (origin != null) contentH += gapOrigin + origin.height
            contentH += if (hasMap) dp(8) else gapFlourish + flourishH
            contentH += gapStats + statsH +
                gapDate + date.height + gapDivider + dp(2) +
                gapMark + markRowH + PAD
            // Screenshot size (user order Oct 2026): standard phone
            // screenshot canvas 1080×2340 — shorter content is centered
            // vertically; taller content grows the canvas instead.
            val imgH = maxOf(2340, (MARGIN * 2 + contentH).toInt())
            val top = MARGIN + maxOf(0f, (imgH - MARGIN * 2 - contentH) / 2)

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
                IMG_W / 2f, top - dp(260), dp(340),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gold; alpha = 38
                },
            )

            val cardRect = RectF(
                MARGIN.toFloat(), top,
                (IMG_W - MARGIN).toFloat(), top + contentH,
            )
            c.drawRoundRect(
                cardRect, CARD_RADIUS, CARD_RADIUS,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = card
                    setShadowLayer(dp(16), 0f, dp(8), 0x55000000)
                },
            )

            val cx = IMG_W / 2f
            var y: Float

            if (hasMap && mapImage != null) {
                // Full-camera route snapshot, rounded TOP corners only.
                val mapRect = RectF(
                    MARGIN.toFloat(), top,
                    (IMG_W - MARGIN).toFloat(), top + mapH,
                )
                c.save()
                c.clipPath(
                    Path().apply {
                        addRoundRect(
                            mapRect,
                            floatArrayOf(
                                CARD_RADIUS, CARD_RADIUS,
                                CARD_RADIUS, CARD_RADIUS,
                                0f, 0f, 0f, 0f,
                            ),
                            Path.Direction.CW,
                        )
                    },
                )
                val srcAspect = mapImage.width.toFloat() / mapImage.height.coerceAtLeast(1)
                val dstAspect = mapW / mapH.coerceAtLeast(1f)
                val src = if (srcAspect > dstAspect) {
                    val w = (mapImage.height * dstAspect).toInt().coerceAtLeast(1)
                    Rect(
                        (mapImage.width - w) / 2, 0,
                        (mapImage.width + w) / 2, mapImage.height,
                    )
                } else {
                    val h = (mapImage.width / dstAspect).toInt().coerceAtLeast(1)
                    Rect(
                        0, (mapImage.height - h) / 2,
                        mapImage.width, (mapImage.height + h) / 2,
                    )
                }
                c.drawBitmap(mapImage, src, mapRect, Paint(Paint.FILTER_BITMAP_FLAG))
                c.restore()
                // Gold underline under the map.
                c.drawRect(
                    MARGIN.toFloat(), top + mapH,
                    (IMG_W - MARGIN).toFloat(), top + mapH + dp(6),
                    Paint().apply { color = gold },
                )
                y = top + mapH + dp(28)
            } else {
                // Drawn destination marker (gold disc + white core).
                y = top + PAD
                val my = y + markerR
                c.drawCircle(cx, my, markerR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
                c.drawCircle(
                    cx, my, markerR * 0.42f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card },
                )
                y += markerR * 2 + gapTitle
            }

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

            if (!hasMap) {
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
            } else {
                y += dp(8) + gapStats
            }

            // Tinted side panel + accent edge + three stat lines.
            val panelRect = RectF(
                (MARGIN + PAD).toFloat(), y,
                (IMG_W - MARGIN - PAD).toFloat(), y + statsH,
            )
            c.drawRoundRect(
                panelRect, dp(28), dp(28),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1F0D7C66 },
            )
            val barW = dp(10)
            val barRect = if (rtl) {
                RectF(panelRect.right - barW, y + dp(20), panelRect.right, y + statsH - dp(20))
            } else {
                RectF(panelRect.left, y + dp(20), panelRect.left + barW, y + statsH - dp(20))
            }
            c.drawRoundRect(
                barRect, barW / 2, barW / 2,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary },
            )
            var sy = y + panelPad
            statLayouts.forEachIndexed { i, l ->
                c.save()
                c.translate((MARGIN + PAD).toFloat() + panelPad, sy)
                l.draw(c)
                c.restore()
                sy += l.height
                if (i < statLayouts.size - 1) {
                    sy += rowGapS / 2
                    c.drawRect(
                        (MARGIN + PAD).toFloat() + panelPad, sy,
                        (IMG_W - MARGIN - PAD).toFloat() - panelPad, sy + dp(2),
                        Paint().apply { color = 0xFFE2E5E0.toInt() },
                    )
                    sy += dp(2) + rowGapS / 2
                }
            }
            y += statsH + gapDate

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
                val dst = RectF(
                    rowX, y + (rowH - logoSize) / 2,
                    rowX + logoSize, y + (rowH + logoSize) / 2,
                )
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
