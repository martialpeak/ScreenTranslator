package com.screenreader.translator.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.screenreader.translator.MainActivity
import com.screenreader.translator.R
import com.screenreader.translator.ScreenTranslatorApp
import com.screenreader.translator.engine.ocr.RecognizedBlock
import com.screenreader.translator.engine.ocr.ScreenOcrScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ScreenCaptureService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ocrScanner = ScreenOcrScanner()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420

    @Volatile
    private var cachedLatestBitmap: Bitmap? = null

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        updateDisplayMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == ACTION_START_PROJECTION) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode != 0 && resultData != null) {
                startForegroundNotification()
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

                // الزام حیاتی اندروید ۱۴+: ثبت کال‌بک قبل از فراخوانی createVirtualDisplay
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        virtualDisplay?.release()
                        virtualDisplay = null
                        imageReader?.close()
                        imageReader = null
                        cachedLatestBitmap?.recycle()
                        cachedLatestBitmap = null
                        isProjectionReady = false
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))

                setupVirtualDisplay()
                isProjectionReady = true
            }
        }
        return START_STICKY
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

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            virtualDisplay = proj.createVirtualDisplay(
                "ScreenTranslatorCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isReady(): Boolean = mediaProjection != null && virtualDisplay != null

    fun getOcrScanner(): ScreenOcrScanner = ocrScanner

    /**
     * تصویربرداری مستقیم از بافر صفحه نمایش با حفظ فریم قبلی در صورت سکون تصویر
     */
    suspend fun getLatestScreenBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        if (mediaProjection == null) return@withContext null
        if (imageReader == null || virtualDisplay == null) {
            setupVirtualDisplay()
            delay(120)
        }
        val reader = imageReader ?: return@withContext null

        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: reader.acquireNextImage()
        } catch (e: Exception) {
            // نادیده گرفتن استثنای بافر
        }

        if (image != null) {
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val cleanBitmap = if (rowPadding == 0) {
                    bitmap
                } else {
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    bitmap.recycle()
                    cropped
                }

                cachedLatestBitmap?.recycle()
                cachedLatestBitmap = cleanBitmap
            } catch (e: Exception) {
                image.close()
            }
        }

        // در صورتی که صفحه کاملاً ساکن باشد و فریم جدید نیامده باشد، از آخرین فریم پایدار استفاده می‌شود
        cachedLatestBitmap
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

                // اختلاف بیش از ۲۰ سطح روشنایی نشانه تغییر محتوای کمیک است نه نویز سنسور/GPU
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
    suspend fun captureAndScanScreen(): List<RecognizedBlock> = withContext(Dispatchers.IO) {
        if (mediaProjection == null) return@withContext emptyList()
        if (imageReader == null || virtualDisplay == null) {
            setupVirtualDisplay()
            delay(100)
        }
        val reader = imageReader ?: return@withContext emptyList()

        var image: Image? = null
        for (attempt in 0 until 10) {
            image = try {
                reader.acquireLatestImage() ?: reader.acquireNextImage()
            } catch (e: Exception) {
                null
            }
            if (image != null) break
            delay(40)
        }

        val bitmapToScan: Bitmap?
        if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val cleanBitmap = if (rowPadding == 0) {
                bitmap
            } else {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()
                cropped
            }

            cachedLatestBitmap?.recycle()
            cachedLatestBitmap = cleanBitmap
            bitmapToScan = cleanBitmap
        } else {
            bitmapToScan = cachedLatestBitmap
        }

        if (bitmapToScan == null) {
            return@withContext emptyList()
        }

        try {
            val copy = bitmapToScan.copy(Bitmap.Config.ARGB_8888, false)
            val preferences = com.screenreader.translator.data.local.AppPreferences.getInstance(applicationContext)
            val blocks = ocrScanner.scanBitmap(copy, filterSystemMargins = true, ocrLanguageMode = preferences.ocrLanguageMode)
            copy.recycle()
            blocks
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        ocrScanner.close()
        cachedLatestBitmap?.recycle()
        cachedLatestBitmap = null
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        const val ACTION_START_PROJECTION = "com.screenreader.translator.action.START_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        var isProjectionReady: Boolean = false
    }
}
