package com.screenreader.translator.engine.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * مدیر خروجی WebP در ZIP — تبدیل صفحات ترجمه‌شده به فایل‌های WebP فشرده و بسته‌بندی در یک آرشیو ZIP
 * بدون نیاز به هیچ کتابخانه خارجی — فقط Android SDK و Java ZIP API
 */
object WebpZipExportManager {

    /**
     * صفحات Bitmap را به فایل‌های WebP فشرده تبدیل و در یک ZIP ذخیره می‌کند
     * @param pages لیست Bitmap صفحات ترجمه‌شده
     * @param targetUri آدرس فایل ZIP خروجی
     * @param quality کیفیت WebP (پیش‌فرض 80 — بهترین بالانس حجم/کیفیت برای مانهوا)
     * @param onProgress گزارش پیشرفت عملیات (current, total)
     * @return true در صورت موفقیت
     */
    suspend fun exportBitmapsToWebpZip(
        context: Context,
        pages: List<Bitmap>,
        targetUri: Uri,
        quality: Int = 80,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext false

        try {
            context.contentResolver.openOutputStream(targetUri)?.use { rawOut ->
                BufferedOutputStream(rawOut, 64 * 1024).use { buffered ->
                    ZipOutputStream(buffered).use { zip ->
                        zip.setLevel(1) // فشرده‌سازی سبک — WebP خودش فشرده است

                        val webpBuffer = ByteArrayOutputStream(256 * 1024)
                        val format = getWebpFormat()

                        for ((index, bitmap) in pages.withIndex()) {
                            withContext(Dispatchers.Main) {
                                onProgress?.invoke(index + 1, pages.size)
                            }

                            // تبدیل Bitmap به WebP
                            webpBuffer.reset()
                            bitmap.compress(format, quality, webpBuffer)

                            // ساخت نام فایل با شماره‌گذاری ۳ رقمی
                            val fileName = "page_%03d.webp".format(index + 1)

                            // افزودن به ZIP
                            val entry = ZipEntry(fileName)
                            entry.size = webpBuffer.size().toLong()
                            zip.putNextEntry(entry)
                            zip.write(webpBuffer.toByteArray())
                            zip.closeEntry()
                        }

                        zip.finish()
                    }
                }
            } ?: return@withContext false

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * انتخاب فرمت WebP بر اساس سطح API دستگاه
     * API 30+ از WEBP_LOSSY و WEBP_LOSSLESS پشتیبانی می‌کند
     */
    @Suppress("DEPRECATION")
    private fun getWebpFormat(): Bitmap.CompressFormat {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
    }
}
