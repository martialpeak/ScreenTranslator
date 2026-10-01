package com.screenreader.translator.engine.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.screenreader.translator.engine.ocr.RecognizedBlock
import kotlin.math.max
import kotlin.math.min

/**
 * رندرر اختصاصی رسم بالن‌های کمیک و مانگا بر روی تصویر صفحه (جهت پیش‌پردازش PDF و وب‌تون)
 * مجهز به الگوریتم ضد تداخل (Anti-Collision) جهت جلوگیری از روی هم افتادن متن‌ها
 */
object ComicBubbleRenderer {

    data class BubbleLayout(
        val block: RecognizedBlock,
        val text: String,
        var left: Float,
        var top: Float,
        var width: Float,
        var height: Float
    ) {
        val right get() = left + width
        val bottom get() = top + height
    }

    /**
     * محاسبه مکان‌های بدون تداخل برای بالن‌ها
     */
    fun resolveNonOverlappingLayout(
        blocks: List<RecognizedBlock>,
        pageWidth: Int,
        pageHeight: Int,
        fontScale: Float = 1.0f
    ): List<BubbleLayout> {
        val list = mutableListOf<BubbleLayout>()

        for (block in blocks) {
            val text = block.translatedText ?: continue
            val box = block.boundingBox

            val origW = box.width().toFloat()
            val origH = box.height().toFloat()

            // محاسبه هوشمند عرض و ارتفاع متناسب با کادر اصلی جهت پوشش صددرصدی متن انگلیسی
            val targetW = max(origW * 1.25f + 24f, 100f).coerceIn(100f, (pageWidth - 24).toFloat())

            // تخمین خطوط و ارتفاع متناسب با طول ترجمه فارسی و مقیاس فونت
            val charWidth = 15f * fontScale.coerceIn(0.7f, 1.5f)
            val charsPerLine = max(5, (targetW / charWidth).toInt())
            val estimatedLines = max(1, (text.length + charsPerLine - 1) / charsPerLine)
            val lineHeight = 36f * fontScale.coerceIn(0.7f, 1.5f)
            val targetH = max(origH * 1.25f + 20f, (estimatedLines * lineHeight + 28f)).coerceIn(54f, 600f)

            val centerX = box.centerX().toFloat()
            val centerY = box.centerY().toFloat()

            val left = (centerX - targetW / 2f).coerceIn(12f, (pageWidth - targetW - 12f).coerceAtLeast(12f))
            val top = (centerY - targetH / 2f).coerceIn(12f, (pageHeight - targetH - 12f).coerceAtLeast(12f))

            list.add(BubbleLayout(block, text, left, top, targetW, targetH))
        }

        // مرتب‌سازی از بالا به پایین
        list.sortBy { it.top }

        // الگوریتم جلوگیری از هم‌پوشانی (Relaxation)
        for (i in list.indices) {
            val current = list[i]
            for (j in 0 until i) {
                val above = list[j]
                val xOverlap = min(current.right, above.right) - max(current.left, above.left)
                val yOverlap = min(current.bottom, above.bottom) - max(current.top, above.top)

                if (xOverlap > 10f && yOverlap > -8f) {
                    val newTop = above.bottom + 10f
                    current.top = newTop
                    if (current.bottom > pageHeight - 12f) {
                        current.top = max(12f, pageHeight - 12f - current.height)
                    }
                }
            }
        }

        return list
    }

    /**
     * رسم مستقیم بالن‌های ترجمه روی بیت‌مپ صفحه با تم و مقیاس فونت دلخواه
     */
    fun drawTranslationsOnBitmap(
        sourceBitmap: Bitmap,
        blocks: List<RecognizedBlock>,
        theme: String = "WHITE",
        fontScale: Float = 1.0f
    ): Bitmap {
        val mutableBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val layouts = resolveNonOverlappingLayout(blocks, mutableBitmap.width, mutableBitmap.height, fontScale)

        val (bgColor, borderColor, textColor) = when (theme.uppercase()) {
            "DARK" -> Triple(
                Color.parseColor("#0F172A"),
                Color.parseColor("#38BDF8"),
                Color.parseColor("#F8FAFC")
            )
            "TRANSLUCENT" -> Triple(
                Color.argb(215, 15, 23, 42),
                Color.argb(200, 56, 189, 248),
                Color.WHITE
            )
            else -> Triple(
                Color.WHITE,
                Color.parseColor("#1E293B"),
                Color.parseColor("#0F172A")
            )
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = if (theme == "TRANSLUCENT") 2.5f else 3.5f
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        for (bubble in layouts) {
            val rectF = RectF(bubble.left, bubble.top, bubble.right, bubble.bottom)
            val cornerRadius = 20f

            // رسم پس‌زمینه بالن
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)
            // رسم حاشیه دور بالن
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)

            // محاسبه متن چندخطی فارسی با تنظیم پویای سایز فونت متناسب با ابعاد بالن و ضریب فونت
            val availableWidth = (bubble.width - 24f).toInt().coerceAtLeast(30)
            val initialBase = if (bubble.height < 65f) 18f else 22f
            var currentTextSize = initialBase * fontScale.coerceIn(0.7f, 1.5f)
            textPaint.textSize = currentTextSize

            val minSize = 12f * fontScale.coerceIn(0.7f, 1.5f)
            var staticLayout = buildStaticLayout(bubble.text, textPaint, availableWidth)
            while (staticLayout.height > bubble.height - 14f && currentTextSize > minSize) {
                currentTextSize -= 1.5f
                textPaint.textSize = currentTextSize
                staticLayout = buildStaticLayout(bubble.text, textPaint, availableWidth)
            }

            canvas.save()
            val textY = bubble.top + (bubble.height - staticLayout.height) / 2f
            canvas.translate(bubble.left + 12f, max(bubble.top + 4f, textY))
            staticLayout.draw(canvas)
            canvas.restore()
        }

        return mutableBitmap
    }

    private fun buildStaticLayout(text: String, textPaint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                textPaint,
                width,
                Layout.Alignment.ALIGN_CENTER,
                1.0f,
                0.0f,
                false
            )
        }
    }
}
