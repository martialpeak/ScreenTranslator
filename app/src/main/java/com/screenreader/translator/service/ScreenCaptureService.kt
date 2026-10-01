package com.screenreader.translator.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.screenreader.translator.MainActivity
import com.screenreader.translator.R
import com.screenreader.translator.ScreenTranslatorApp
import com.screenreader.translator.data.local.AppPreferences
import com.screenreader.translator.engine.ocr.RecognizedBlock
import com.screenreader.translator.engine.ocr.ScreenOcrScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ScreenCaptureService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ocrScanner = ScreenOcrScanner()

    @Volatile
    private var mediaProjection: MediaProjection? = null
    @Volatile
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile
    private var imageReader: ImageReader? = null

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420

    // بافرهای قابل استفاده مجدد فریم: به‌جای ساخت و recycle یک Bitmap فول‌اسکرین (~10MB)
    // در هر ۲۵۰ میلی‌ثانیه، همیشه در همین دو بافر نوشته می‌شود. دسترسی فقط داخل frameMutex.
    private val frameMutex = Mutex()
    private var frameBitmap: Bitmap? = null
    private var frameCanvas: Canvas? = null
    private var stagingBitmap: Bitmap? = null
    private var hasFrame = false
    private val copyPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        updateDisplayMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROJECTION -> startProjection(intent)
            ACTION_STOP_PROJECTION -> {
                releaseProjection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // توکن MediaProjection قابل استفاده مجدد نیست؛ ری‌استارت خودکار سرویس بدون توکن بی‌فایده است.
        return START_NOT_STICKY
    }

    private fun startProjection(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null) return

        // الزام اندروید ۱۴+: سرویس باید قبل از getMediaProjection در حالت foreground باشد
        startForegroundNotification()

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            projectionManager.getMediaProjection(resultCode, resultData)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return

        // آزادسازی پروجکشن قبلی (در صورت وجود) قبل از جایگزینی
        releaseProjection()
        mediaProjection = projection

        // الزام اندروید ۱۴+: ثبت کال‌بک قبل از فراخوانی createVirtualDisplay
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // فقط اگر هنوز همین پروجکشن فعال است پاکسازی شود (نه پروجکشن جدیدتر)
                if (mediaProjection === projection) {
                    mediaProjection = null
                    releaseCaptureSurfaces()
                }
            }
        }, Handler(Looper.getMainLooper()))

        setupVirtualDisplay()
        isProjectionReady = virtualDisplay != null
    }

    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, ScreenTranslatorApp.CHANNEL_CAPTURE_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText("سرویس آماده اسکن هوشمند صفحه نمایش")
            .setSmallIcon(R.drawable.ic_bubble_translate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateDisplayMetrics() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun setupVirtualDisplay() {
        if (virtualDisplay != null && imageReader != null) {
            return
        }
        val proj = mediaProjection ?: return

        try {
            imageReader?.close()
            virtualDisplay?.release()

            val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            virtualDisplay = proj.createVirtualDisplay(
                "ScreenTranslatorCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )
        } catch (e: Exception) {
            // در اندروید ۱۴+ ساخت دوباره VirtualDisplay روی یک پروجکشن مجاز نیست
            e.printStackTrace()
            imageReader?.close()
            imageReader = null
            virtualDisplay = null
        }
    }

    private fun releaseCaptureSurfaces() {
        isProjectionReady = false
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        hasFrame = false
    }

    private fun releaseProjection() {
        val old = mediaProjection
        mediaProjection = null
        releaseCaptureSurfaces()
        try {
            old?.stop()
        } catch (_: Exception) {
        }
    }

    fun isReady(): Boolean = mediaProjection != null && virtualDisplay != null

    fun getOcrScanner(): ScreenOcrScanner = ocrScanner

    /**
     * خواندن جدیدترین فریم در بافر قابل استفاده مجدد. فقط داخل frameMutex صدا زده شود.
     * اگر فریم جدیدی نیامده باشد (صفحه ساکن)، آخرین فریم معتبر برگردانده می‌شود.
     */
    private suspend fun decodeLatestFrameLocked(waitAttempts: Int = 0): Bitmap? {
        if (mediaProjection == null) return null
        if (imageReader == null || virtualDisplay == null) {
            setupVirtualDisplay()
            delay(120)
        }
        val reader = imageReader ?: return if (hasFrame) frameBitmap else null

        var image: Image? = null
        var attempt = 0
        while (true) {
            image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (image != null || attempt >= waitAttempts) break
            attempt++
            delay(40)
        }

        val img = image
        if (img != null) {
            try {
                copyImageIntoFrame(img)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                img.close()
            }
        }
        return if (hasFrame) frameBitmap else null
    }

    private fun copyImageIntoFrame(image: Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val rowPadding = rowStride - pixelStride * width

        var frame = frameBitmap
        var canvas = frameCanvas
        if (frame == null || canvas == null || frame.width != width || frame.height != height) {
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            canvas = Canvas(frame)
            frameBitmap = frame
            frameCanvas = canvas
        }

        buffer.rewind()
        if (rowPadding == 0) {
            frame.copyPixelsFromBuffer(buffer)
        } else {
            val paddedWidth = width + rowPadding / pixelStride
            var staging = stagingBitmap
            if (staging == null || staging.width != paddedWidth || staging.height != height) {
                staging = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                stagingBitmap = staging
            }
            staging.copyPixelsFromBuffer(buffer)
            canvas.drawBitmap(staging, 0f, 0f, copyPaint)
        }
        hasFrame = true
    }

    /**
     * نمونه‌برداری سبک از جدیدترین فریم بدون هیچ تخصیص حافظه Bitmap جدید (مخصوص حلقه پایش اسکرول)
     */
    suspend fun sampleScreen(reusableBuffer: IntArray? = null): IntArray? = withContext(Dispatchers.IO) {
        frameMutex.withLock {
            decodeLatestFrameLocked()?.let { extractScreenSamples(it, reusableBuffer) }
        }
    }

    /**
     * یک کپی مستقل و پایدار از جدیدترین فریم. مالکیت با فراخواننده است و
     * می‌تواند پس از استفاده آن را recycle کند؛ هیچ کد دیگری آن را recycle نمی‌کند.
     */
    suspend fun snapshotScreenBitmap(waitAttempts: Int = 0): Bitmap? = withContext(Dispatchers.IO) {
        frameMutex.withLock {
            decodeLatestFrameLocked(waitAttempts)?.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    /**
     * سازگاری با نسخه قبلی: اکنون همیشه یک کپی مستقل برمی‌گرداند (دیگر بعداً recycle نمی‌شود).
     */
    suspend fun getLatestScreenBitmap(): Bitmap? = snapshotScreenBitmap()

    /**
     * اجرای OCR روی یک کپی از تصویر ورودی خارج از ترد اصلی. تصویر ورودی دست‌نخورده می‌ماند.
     */
    suspend fun scanScreenBitmap(bitmap: Bitmap): List<RecognizedBlock> = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled) return@withContext emptyList()
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext emptyList()
        try {
            val preferences = AppPreferences.getInstance(applicationContext)
            ocrScanner.scanBitmap(copy, filterSystemMargins = true, ocrLanguageMode = preferences.ocrLanguageMode)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            if (!copy.isRecycled) copy.recycle()
        }
    }

    /**
     * نمونه‌برداری از روشنایی صفحه در قالب یک آرایه عددی سبک (۱۲۰ نقطه در مرکز صفحه کمیک)
     * با امکان استفاده مجدد از بافر موجود جهت بهینه‌سازی GC و کاهش سربار حافظه RAM
     */
    fun extractScreenSamples(bitmap: Bitmap, reusableBuffer: IntArray? = null): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val startX = (width * 0.12).toInt()
        val endX = (width * 0.88).toInt()
        val startY = (height * 0.12).toInt()
        val endY = (height * 0.88).toInt()

        val cols = 10
        val rows = 12
        val total = cols * rows
        val samples = if (reusableBuffer != null && reusableBuffer.size == total) reusableBuffer else IntArray(total)
        val stepX = ((endX - startX) / cols).coerceAtLeast(1)
        val stepY = ((endY - startY) / rows).coerceAtLeast(1)

        var idx = 0
        for (r in 0 until rows) {
            val y = (startY + r * stepY).coerceIn(0, height - 1)
            for (c in 0 until cols) {
                val x = (startX + c * stepX).coerceIn(0, width - 1)
                val pixel = try {
                    bitmap.getPixel(x, y)
                } catch (e: Exception) {
                    0
                }
                val gray = ((pixel shr 16 and 0xFF) * 30 + (pixel shr 8 and 0xFF) * 59 + (pixel and 0xFF) * 11) / 100
                samples[idx++] = gray
            }
        }
        return samples
    }

    /**
     * محاسبه درصد تغییرات بین دو نمونه صفحه (بین 0.0 تا 100.0)
     * فقط تغییرات نوری محسوس (> 25) تغییر محتوا شمرده می‌شود تا نویزهای ریز GPU و انیمیشن ساعت صفر شود.
     */
    fun computeSamplesDiffPercentage(samples1: IntArray?, samples2: IntArray?): Float {
        if (samples1 == null || samples2 == null || samples1.size != samples2.size || samples1.isEmpty()) {
            return 100f
        }
        var diffCount = 0
        for (i in samples1.indices) {
            if (kotlin.math.abs(samples1[i] - samples2[i]) > 25) {
                diffCount++
            }
        }
        return (diffCount.toFloat() / samples1.size) * 100f
    }

    /**
     * محاسبه تفاوت درصدی دو فریم جهت تشخیص قطعی اسکرول بدون تاثیر نویز دیجیتال
     * خروجی: درصد نقاط با تغییر محسوس بین 0.0 تا 100.0
     */
    fun computeScreenDiffPercentage(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        val width = kotlin.math.min(bitmap1.width, bitmap2.width)
        val height = kotlin.math.min(bitmap1.height, bitmap2.height)

        val startX = (width * 0.15).toInt()
        val endX = (width * 0.85).toInt()
        val startY = (height * 0.15).toInt()
        val endY = (height * 0.85).toInt()

        val gridCols = 8
        val gridRows = 10
        val totalPoints = gridCols * gridRows
        val stepX = ((endX - startX) / gridCols).coerceAtLeast(1)
        val stepY = ((endY - startY) / gridRows).coerceAtLeast(1)

        var significantDiffCount = 0

        for (row in 0 until gridRows) {
            val y = startY + row * stepY
            for (col in 0 until gridCols) {
                val x = startX + col * stepX
                val p1 = bitmap1.getPixel(x, y)
                val p2 = bitmap2.getPixel(x, y)

                val g1 = ((p1 shr 16 and 0xFF) * 30 + (p1 shr 8 and 0xFF) * 59 + (p1 and 0xFF) * 11) / 100
                val g2 = ((p2 shr 16 and 0xFF) * 30 + (p2 shr 8 and 0xFF) * 59 + (p2 and 0xFF) * 11) / 100

                if (kotlin.math.abs(g1 - g2) > 20) {
                    significantDiffCount++
                }
            }
        }

        return (significantDiffCount.toFloat() / totalPoints) * 100f
    }

    /**
     * محاسبه فینگرپرینت سریع تصویر جهت تشخیص سکون یا اسکرول صفحه
     */
    fun computeFingerprint(bitmap: Bitmap): Long {
        var hash = 1125899906842597L
        val startX = (bitmap.width * 0.15).toInt()
        val endX = (bitmap.width * 0.85).toInt()
        val startY = (bitmap.height * 0.15).toInt()
        val endY = (bitmap.height * 0.85).toInt()
        val stepX = ((endX - startX) / 16).coerceAtLeast(1)
        val stepY = ((endY - startY) / 16).coerceAtLeast(1)

        for (y in startY until endY step stepY) {
            for (x in startX until endX step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val gray = ((pixel shr 16 and 0xFF) * 30 + (pixel shr 8 and 0xFF) * 59 + (pixel and 0xFF) * 11) / 100
                hash = (hash * 31) xor gray.toLong()
            }
        }
        return hash
    }

    /**
     * تصویربرداری مستقیم برای تک اسکن‌ها با حلقه انتظار جهت فریم تمیز
     */
    suspend fun captureAndScanScreen(): List<RecognizedBlock> {
        val snapshot = snapshotScreenBitmap(waitAttempts = 10) ?: return emptyList()
        return try {
            scanScreenBitmap(snapshot)
        } finally {
            snapshot.recycle()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseProjection()
        ocrScanner.close()
        frameBitmap = null
        frameCanvas = null
        stagingBitmap = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        const val ACTION_START_PROJECTION = "com.screenreader.translator.action.START_PROJECTION"
        const val ACTION_STOP_PROJECTION = "com.screenreader.translator.action.STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        @Volatile
        var isProjectionReady: Boolean = false
    }
}
