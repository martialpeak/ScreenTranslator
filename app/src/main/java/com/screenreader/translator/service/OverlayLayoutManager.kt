package com.screenreader.translator.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.screenreader.translator.R
import com.screenreader.translator.engine.ocr.RecognizedBlock
import java.util.Locale

class OverlayLayoutManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayRootView: FrameLayout? = null
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isTtsReady = false

    private var isOriginalVisible = false
    private var currentBlocks: List<RecognizedBlock> = emptyList()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("fa")
            isTtsReady = true
        }
    }

    private var isMangaWhiteStyle = true
    var isAutoScrollMode: Boolean = false
    var onAutoScrollToggleListener: ((Boolean) -> Unit)? = null

    /**
     * رندر درجا و مستقیم کلیه ترجمه‌ها با طراحی اختصاصی متناسب با بالن‌های مانهوا و مانگا
     */
    fun showTranslations(blocks: List<RecognizedBlock>, isAutoScroll: Boolean = false) {
        hideOverlay()
        val isTouchPassThrough = isAutoScroll || isAutoScrollMode
        var windowFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (isTouchPassThrough) {
            windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            windowFlags,
            PixelFormat.TRANSLUCENT
        )

        val root = FrameLayout(context).apply {
            if (isTouchPassThrough) {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = false
            } else {
                setBackgroundColor(Color.parseColor("#44000000"))
                setOnClickListener {
                    hideOverlay()
                }
            }
        }

        // افزودن نوار ابزار بالا فقط در حالت دستی (در حالت اسکرول خودکار صفحه جهت مطالعه خلوت می‌ماند)
        if (!isTouchPassThrough) {
            addTopControlBar(root)
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        // استفاده از الگوریتم ضد تداخل جهت جلوگیری کامل از افتادن بالن‌ها روی یکدیگر
        val nonOverlappingBubbles = com.screenreader.translator.engine.rendering.ComicBubbleRenderer.resolveNonOverlappingLayout(
            blocks,
            screenWidth,
            screenHeight
        )

        for (bubble in nonOverlappingBubbles) {
            val chipView = TextView(context).apply {
                text = bubble.text
                tag = "TRANSLATION_CHIP"
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(14, 8, 14, 8)
                elevation = 14f

                // سایزینگ خودکار و یکنواخت فونت متناسب با فضای بالن
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAutoSizeTextTypeUniformWithConfiguration(
                        10,
                        16,
                        1,
                        android.util.TypedValue.COMPLEX_UNIT_SP
                    )
                } else {
                    textSize = 13f
                }

                // اعمال پوسته بالن مانهوا (سفید مانگا یا دارک)
                applyBubbleStyle(this, isMangaWhiteStyle)

                // باز شدن دیالوگ جزییات با کلیک روی هر بالن
                setOnClickListener {
                    showBlockDetailDialog(root, bubble.block)
                }
            }

            val chipParams = FrameLayout.LayoutParams(bubble.width.toInt(), bubble.height.toInt()).apply {
                leftMargin = bubble.left.toInt()
                topMargin = bubble.top.toInt()
            }

            root.addView(chipView, chipParams)
        }

        overlayRootView = root
        try {
            windowManager.addView(root, layoutParams)
            root.alpha = 0f
            root.animate().alpha(1f).setDuration(200).start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyBubbleStyle(textView: TextView, isWhite: Boolean) {
        if (isWhite) {
            textView.setTextColor(Color.parseColor("#0F172A"))
            textView.background = GradientDrawable().apply {
                setColor(Color.WHITE) // سفید کاملاً مات برای پوشش صددرصدی متن اصلی
                setStroke(3, Color.parseColor("#1E293B")) // حاشیه تیره بالن مانهوا
                cornerRadius = 24f
            }
        } else {
            textView.setTextColor(Color.WHITE)
            textView.background = GradientDrawable().apply {
                setColor(Color.parseColor("#F20F172A"))
                setStroke(2, Color.parseColor("#38BDF8"))
                cornerRadius = 18f
            }
        }
    }

    private fun updateAllChipsStyle(root: FrameLayout) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is TextView && child.tag == "TRANSLATION_CHIP") {
                applyBubbleStyle(child, isMangaWhiteStyle)
            }
        }
    }

    private fun addTopControlBar(root: FrameLayout) {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 12, 20, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                cornerRadius = 32f
                setStroke(1, Color.parseColor("#334155"))
            }
        }

        // دکمه فعال‌سازی اسکرول خودکار (ترجمه زنده در حال خواندن)
        val autoScrollBtn = TextView(context).apply {
            text = if (isAutoScrollMode) "⚡ اسکرول خودکار: فعال" else "⚡ اسکرول خودکار"
            setTextColor(if (isAutoScrollMode) Color.parseColor("#34D399") else Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(14, 8, 14, 8)
            setOnClickListener {
                isAutoScrollMode = !isAutoScrollMode
                text = if (isAutoScrollMode) "⚡ اسکرول خودکار: فعال" else "⚡ اسکرول خودکار"
                setTextColor(if (isAutoScrollMode) Color.parseColor("#34D399") else Color.parseColor("#94A3B8"))
                onAutoScrollToggleListener?.invoke(isAutoScrollMode)
                if (isAutoScrollMode) {
                    root.setBackgroundColor(Color.TRANSPARENT)
                    root.isClickable = false
                    Toast.makeText(context, "⚡ حالت اسکرول خودکار فعال شد؛ متن‌ها همزمان با اسکرول ترجمه می‌شوند", Toast.LENGTH_SHORT).show()
                } else {
                    root.setBackgroundColor(Color.parseColor("#44000000"))
                    root.setOnClickListener { hideOverlay() }
                }
            }
        }

        // دکمه تغییر پوسته بالن مانهوا
        val styleBtn = TextView(context).apply {
            text = if (isMangaWhiteStyle) "🎨 بالن سفید مانهوا" else "🎨 بالن تیره"
            setTextColor(Color.parseColor("#FBBF24"))
            textSize = 12f
            setPadding(14, 8, 14, 8)
            setOnClickListener {
                isMangaWhiteStyle = !isMangaWhiteStyle
                text = if (isMangaWhiteStyle) "🎨 بالن سفید مانهوا" else "🎨 بالن تیره"
                updateAllChipsStyle(root)
            }
        }

        // دکمه سوئیچ بین متن اصلی و ترجمه
        val toggleBtn = TextView(context).apply {
            text = "👁 متن اصلی"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            setPadding(14, 8, 14, 8)
            setOnClickListener {
                toggleOriginalTextVisibility(root)
            }
        }

        // دکمه بستن لایه
        val closeBtn = TextView(context).apply {
            text = "✕ بستن"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(18, 8, 18, 8)
            setOnClickListener {
                hideOverlay()
            }
        }

        bar.addView(autoScrollBtn)
        bar.addView(styleBtn)
        bar.addView(toggleBtn)
        bar.addView(closeBtn)

        val barParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 70
        }

        root.addView(bar, barParams)
    }

    private fun toggleOriginalTextVisibility(root: FrameLayout) {
        isOriginalVisible = !isOriginalVisible
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is TextView && child.tag == "TRANSLATION_CHIP") {
                child.visibility = if (isOriginalVisible) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    private fun showBlockDetailDialog(root: FrameLayout, block: RecognizedBlock) {
        val dialog = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F80F172A"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#2563EB"))
            }
        }

        val originalTitle = TextView(context).apply {
            text = "متن اصلی:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
        }

        val originalText = TextView(context).apply {
            text = block.originalText
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 4, 0, 16)
        }

        val translatedTitle = TextView(context).apply {
            text = "ترجمه فارسی:"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 11f
        }

        val translatedText = TextView(context).apply {
            text = block.translatedText ?: ""
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 20)
        }

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val copyBtn = TextView(context).apply {
            text = "📋 کپی"
            setTextColor(Color.parseColor("#34D399"))
            textSize = 13f
            setPadding(20, 12, 20, 12)
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ترجمه", block.translatedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "متن ترجمه کپی شد", Toast.LENGTH_SHORT).show()
            }
        }

        val speakBtn = TextView(context).apply {
            text = "🔊 تلفظ"
            setTextColor(Color.parseColor("#60A5FA"))
            textSize = 13f
            setPadding(20, 12, 20, 12)
            setOnClickListener {
                if (isTtsReady) {
                    tts?.speak(block.translatedText, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
                }
            }
        }

        val dismissBtn = TextView(context).apply {
            text = "تایید"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(20, 12, 20, 12)
            setOnClickListener {
                root.removeView(dialog)
            }
        }

        actionsRow.addView(copyBtn)
        actionsRow.addView(speakBtn)
        actionsRow.addView(dismissBtn)

        dialog.addView(originalTitle)
        dialog.addView(originalText)
        dialog.addView(translatedTitle)
        dialog.addView(translatedText)
        dialog.addView(actionsRow)

        val dialogParams = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        root.addView(dialog, dialogParams)
    }

    fun setOverlayVisibility(visible: Boolean) {
        overlayRootView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun hasActiveOverlay(): Boolean = overlayRootView != null

    fun hideOverlay() {
        overlayRootView?.let { root ->
            try {
                windowManager.removeView(root)
            } catch (e: Exception) {
                // قبلاً حذف شده است
            }
            overlayRootView = null
        }
    }

    fun release() {
        hideOverlay()
        tts?.stop()
        tts?.shutdown()
    }
}
