package com.screenreader.translator.engine.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * تبدیلگر PDF به WebP — هر صفحه PDF را رندر کرده، به فرمت WebP تبدیل و در یک ZIP بسته‌بندی می‌کند
 * بدون نیاز به ترجمه — صرفاً تبدیل فرمت
 * بدون کتابخانه خارجی — فقط Android PdfRenderer و Java ZIP
 */
object PdfToWebpConverter {

    /**
     * فایل PDF را باز می‌کند، هر صفحه را رندر و به WebP تبدیل کرده
     * و همه را در یک فایل ZIP ذخیره می‌کند
     *
     * @param pdfUri آدرس فایل PDF ورودی
     * @param targetZipUri آدرس فایل ZIP خروجی
     * @param quality کیفیت WebP (پیش‌فرض 85 — کیفیت بالاتر چون ترجمه نمی‌شود)
     * @param targetWidth عرض هدف رندر صفحات (پیش‌فرض 1440px)
     * @param onProgress گزارش پیشرفت (current, total)
     * @return true در صورت موفقیت
     */
    suspend fun convertPdfToWebpZip(
        context: Context,
        pdfUri: Uri,
        targetZipUri: Uri,
        quality: Int = 85,
        targetWidth: Int = 1440,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (pfd == null) return@withContext false

            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            if (pageCount == 0) return@withContext false

            context.contentResolver.openOutputStream(targetZipUri)?.use { rawOut ->
                BufferedOutputStream(rawOut, 64 * 1024).use { buffered ->
                    ZipOutputStream(buffered).use { zip ->
                        zip.setLevel(1) // فشرده‌سازی سبک — WebP خودش فشرده است

                        val webpBuffer = ByteArrayOutputStream(256 * 1024)
                        val format = getWebpFormat()

                        for (i in 0 until pageCount) {
                            withContext(Dispatchers.Main) {
                                onProgress?.invoke(i + 1, pageCount)
                            }

                            // رندر صفحه PDF به Bitmap
                            val page = renderer.openPage(i)
                            val scale = targetWidth.toFloat() / page.width.toFloat()
                            val pageHeight = (page.height * scale).toInt()

                            val pageBitmap = Bitmap.createBitmap(
                                targetWidth, pageHeight, Bitmap.Config.ARGB_8888
                            )
                            page.render(
                                pageBitmap, null, null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            page.close()

                            // تبدیل به WebP
                            webpBuffer.reset()
                            pageBitmap.compress(format, quality, webpBuffer)

                            // آزادسازی حافظه Bitmap
                            pageBitmap.recycle()

                            // افزودن به ZIP
                            val fileName = "page_%03d.webp".format(i + 1)
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
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    /**
     * انتخاب فرمت WebP بر اساس سطح API دستگاه
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
