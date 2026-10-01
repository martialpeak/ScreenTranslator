package com.screenreader.translator.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.screenreader.translator.MainActivity
import com.screenreader.translator.R
import com.screenreader.translator.ScreenTranslatorApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager

    private var bubbleRootView: FrameLayout? = null
    private var bubbleIcon: ImageView? = null
    private var progressBar: ProgressBar? = null

    private var overlayLayoutManager: OverlayLayoutManager? = null
    private var screenCaptureService: ScreenCaptureService? = null
    private var isBoundToCapture = false

    private val captureServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenCaptureService.LocalBinder
            screenCaptureService = binder.getService()
            isBoundToCapture = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenCaptureService = null
            isBoundToCapture = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayLayoutManager = OverlayLayoutManager(this).apply {
            onAutoScrollToggleListener = { active ->
                toggleAutoScrollMode(active)
            }
        }

        bindToCaptureService()
        startForegroundService()
        createFloatingBubble()
    }

    private fun bindToCaptureService() {
        val intent = Intent(this, ScreenCaptureService::class.java)
        bindService(intent, captureServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startForegroundService() {
        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, ScreenTranslatorApp.CHANNEL_BUBBLE_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_bubble_translate)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop_service), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        isRunning = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        val bubbleSize = (56 * resources.displayMetrics.density).toInt()

        val layoutParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - bubbleSize - 20)
            y = resources.displayMetrics.heightPixels / 3
        }

        val container = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2563EB")) // رنگ آبی جذاب مدرن
                setStroke(3, Color.parseColor("#60A5FA"))
            }
            elevation = 16f
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble_translate)
            setPadding(24, 24, 24, 24)
        }

        val progress = ProgressBar(this).apply {
            visibility = View.GONE
            setPadding(16, 16, 16, 16)
        }

        container.addView(icon, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(progress, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(container, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    val duration = System.currentTimeMillis() - touchStartTime

                    // در صورت لمس طولانی (> 500ms): سوییچ به حالت ترجمه زنده اسکرول
                    if (duration > 500 && diffX < 20 && diffY < 20) {
                        toggleAutoScrollMode()
                    } else if (diffX < 20 && diffY < 20) {
                        onBubbleClicked()
                    } else {
                        // چسبیدن هوشمند به لبه راست یا چپ صفحه (Magnetic snap)
                        val screenWidth = resources.displayMetrics.widthPixels
                        val middle = screenWidth / 2
                        layoutParams.x = if (layoutParams.x + bubbleSize / 2 < middle) {
                            16
                        } else {
                            screenWidth - bubbleSize - 16
                        }
                        windowManager.updateViewLayout(container, layoutParams)
                    }
                    true
                }
                else -> false
            }
        }

        bubbleRootView = container
        bubbleIcon = icon
        progressBar = progress

        try {
            windowManager.addView(container, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var isAutoScrollActive = false
    private var autoScrollJob: kotlinx.coroutines.Job? = null

    fun toggleAutoScrollMode(forceState: Boolean? = null) {
        val newState = forceState ?: !isAutoScrollActive
        isAutoScrollActive = newState
        overlayLayoutManager?.isAutoScrollMode = newState
        autoScrollJob?.cancel()

        if (newState) {
            // تغییر استایل حباب به رنگ سبز درخشان جهت نشان دادن فعال بودن حالت زنده
            bubbleRootView?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#059669"))
                setStroke(4, Color.parseColor("#34D399"))
            }
            Toast.makeText(this, "⚡ حالت ترجمه زنده اسکرول فعال شد (برای خروج لمس طولانی کنید)", Toast.LENGTH_LONG).show()

            autoScrollJob = serviceScope.launch {
                // ۱. اسکن و ترجمه فوری صفحه جاری در لحظه فعال‌سازی
                performInitialAutoScan()

                // ۲. ورود به حلقه پایش هوشمند اسکرول و سکون صفحه
                runAutoScrollWatcher()
            }
        } else {
            // بازگردانی حباب به رنگ آبی پیش‌فرض
            bubbleRootView?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2563EB"))
                setStroke(3, Color.parseColor("#60A5FA"))
            }
            overlayLayoutManager?.hideOverlay()
            Toast.makeText(this, "حالت اسکرول خودکار غیرفعال شد", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * اسکن اولیه: یک کپی مستقل از صفحه گرفته می‌شود و همان کپی هم برای OCR و هم برای
     * Vision (sourceBitmap) استفاده می‌شود. قبلاً bitmap ارسالی به Vision توسط فراخوانی بعدی
     * کپچر recycle می‌شد و باعث کرش یا تصویر خراب می‌شد.
     */
    private suspend fun performInitialAutoScan() {
        val capture = screenCaptureService ?: return
        if (!capture.isReady()) return

        var snapshot: Bitmap? = null
        try {
            overlayLayoutManager?.hideOverlay()
            bubbleRootView?.visibility = View.INVISIBLE
            delay(120)

            snapshot = capture.snapshotScreenBitmap(waitAttempts = 10)
            bubbleRootView?.visibility = View.VISIBLE
            val shot = snapshot ?: return

            val blocks = capture.scanScreenBitmap(shot)
            if (blocks.isNotEmpty()) {
                val translated = ScreenTranslatorApp.repository.translateBlocks(blocks, sourceBitmap = shot)
                overlayLayoutManager?.showTranslations(translated, isAutoScroll = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bubbleRootView?.visibility = View.VISIBLE
            snapshot?.recycle()
        }
    }

    /**
     * حلقه پایش اسکرول: نمونه‌برداری مستقیم از بافر قابل استفاده مجدد (بدون ساخت Bitmap در هر سیکل)
     * و فقط هنگام توقف روی پنل جدید یک کپی کامل برای OCR گرفته می‌شود. OCR خارج از ترد اصلی اجرا می‌شود.
     */
    private suspend fun runAutoScrollWatcher() {
        val capture = screenCaptureService ?: return
        val sampleBufferA = IntArray(120)
        val sampleBufferB = IntArray(120)
        var useBufferA = true
        var lastFrameSamples: IntArray? = null
        var lastCleanTranslatedSamples: IntArray? = null
        var stationaryStreak = 0
        var isOverlayShowing = false

        fun nextBuffer(): IntArray {
            val buffer = if (useBufferA) sampleBufferA else sampleBufferB
            useBufferA = !useBufferA
            return buffer
        }

        // مکث اولیه جهت ثبات نمایشگر پس از اسکن آغازین
        delay(350)
        capture.sampleScreen(nextBuffer())?.let { samples ->
            lastFrameSamples = samples
            if (overlayLayoutManager?.hasActiveOverlay() == true) {
                isOverlayShowing = true
                lastCleanTranslatedSamples = samples.clone()
            }
        }

        while (isAutoScrollActive) {
            delay(250)
            if (!capture.isReady()) continue

            val currentSamples = capture.sampleScreen(nextBuffer()) ?: continue
            val frameDiff = capture.computeSamplesDiffPercentage(currentSamples, lastFrameSamples)
            lastFrameSamples = currentSamples

            if (isOverlayShowing) {
                // لایه ترجمه در حال نمایش است -> اگر کاربر شروع به اسکرول کرد (> 15%) پنهانش کن
                if (frameDiff > 15f) {
                    isOverlayShowing = false
                    stationaryStreak = 0
                    overlayLayoutManager?.hideOverlay()
                }
                continue
            }

            // لایه مخفی است: شمارش سیکل‌های ساکن
            stationaryStreak = if (frameDiff < 12f) stationaryStreak + 1 else 0
            if (stationaryStreak < 2) continue
            stationaryStreak = 0

            // آیا پنل جدید است یا همان پنل قبلی؟
            val panelDiff = capture.computeSamplesDiffPercentage(currentSamples, lastCleanTranslatedSamples)
            if (lastCleanTranslatedSamples != null && panelDiff <= 18f) continue

            val snapshot = capture.snapshotScreenBitmap() ?: continue
            try {
                val blocks = capture.scanScreenBitmap(snapshot)
                lastCleanTranslatedSamples = currentSamples.clone()

                if (blocks.isNotEmpty()) {
                    val translated = ScreenTranslatorApp.repository.translateBlocks(blocks, sourceBitmap = snapshot)
                    if (!isAutoScrollActive) break
                    overlayLayoutManager?.showTranslations(translated, isAutoScroll = true)
                    isOverlayShowing = true

                    // مکث کوتاه جهت تکمیل رندر لایه و به‌روزرسانی نمونه با لایه جدید
                    delay(300)
                    capture.sampleScreen(nextBuffer())?.let { lastFrameSamples = it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                snapshot.recycle()
            }
        }
    }

    private fun onBubbleClicked() {
        val captureService = screenCaptureService
        if (captureService == null || !captureService.isReady()) {
            Toast.makeText(this, "لطفاً برنامه را باز کرده و مجوز اسکن صفحه را تایید کنید", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            return
        }

        // پنهان کردن موقت حباب و لایه ترجمه در حین اسکرین‌شات
        overlayLayoutManager?.setOverlayVisibility(false)
        bubbleRootView?.visibility = View.INVISIBLE

        serviceScope.launch {
            try {
                delay(100)
                // 1. اسکن OCR تصویر صفحه
                val blocks = captureService.captureAndScanScreen()

                // بازگردانی حباب با انیمیشن لودینگ
                withContext(Dispatchers.Main) {
                    bubbleRootView?.visibility = View.VISIBLE
                    bubbleIcon?.visibility = View.GONE
                    progressBar?.visibility = View.VISIBLE
                }

                if (blocks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingBubbleService, "هیچ متنی بر روی صفحه یافت نشد", Toast.LENGTH_SHORT).show()
                        resetLoadingState()
                        overlayLayoutManager?.setOverlayVisibility(true)
                    }
                    return@launch
                }

                // 2. ترجمه فوق‌سریع موازی بلوک‌ها با کش دیتابیس Room
                val translatedBlocks = ScreenTranslatorApp.repository.translateBlocks(blocks)

                // 3. رندر درجا روی مختصات کادرهای اصلی صفحه
                withContext(Dispatchers.Main) {
                    overlayLayoutManager?.showTranslations(translatedBlocks)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingBubbleService, "خطا در ترجمه صفحه: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    overlayLayoutManager?.setOverlayVisibility(true)
                }
            } finally {
                bubbleRootView?.visibility = View.VISIBLE
                resetLoadingState()
            }
        }
    }

    private fun resetLoadingState() {
        bubbleIcon?.visibility = View.VISIBLE
        progressBar?.visibility = View.GONE
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        isAutoScrollActive = false
        serviceScope.cancel()

        if (isBoundToCapture) {
            try {
                unbindService(captureServiceConnection)
            } catch (_: Exception) {
            }
            isBoundToCapture = false
        }

        // توقف سرویس کپچر همراه با حباب تا MediaProjection و اعلانش بی‌دلیل زنده نمانند
        try {
            startService(Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP_PROJECTION
            })
        } catch (_: Exception) {
            stopService(Intent(this, ScreenCaptureService::class.java))
        }

        bubbleRootView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }

        overlayLayoutManager?.release()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.screenreader.translator.action.STOP_BUBBLE_SERVICE"
        @Volatile
        var isRunning: Boolean = false
    }
}
