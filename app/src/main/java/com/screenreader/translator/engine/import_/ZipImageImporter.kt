package com.screenreader.translator.engine.import_

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * وارد‌کننده تصاویر از فایل ZIP — فایل‌های WebP/JPEG/PNG داخل آرشیو را استخراج،
 * مرتب‌سازی طبیعی و به Bitmap تبدیل می‌کند
 * بدون کتابخانه خارجی — فقط Java ZIP و Android BitmapFactory
 */
object ZipImageImporter {

    /** فرمت‌های تصویری پشتیبانی‌شده */
    private val SUPPORTED_EXTENSIONS = setOf("webp", "jpg", "jpeg", "png", "bmp")

    /**
     * فایل ZIP را باز و تصاویر داخلش را استخراج، دیکد و با اندازه بهینه برمی‌گرداند
     *
     * @param zipUri آدرس فایل ZIP ورودی
     * @param targetWidth عرض هدف Bitmap‌ها (پیش‌فرض 1440px)
     * @param onProgress گزارش پیشرفت (current, total) — total ممکن است در حین کار تغییر کند
     * @return لیست Bitmap‌های مرتب‌شده آماده پردازش OCR و ترجمه
     */
    suspend fun extractImagesFromZip(
        context: Context,
        zipUri: Uri,
        targetWidth: Int = 1440,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<Pair<String, ByteArray>>()

        // مرحله ۱: خواندن همه ورودی‌های تصویری ZIP به حافظه
        try {
            context.contentResolver.openInputStream(zipUri)?.use { rawIn ->
                ZipInputStream(rawIn).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.lowercase()
                            val ext = name.substringAfterLast('.', "")

                            if (ext in SUPPORTED_EXTENSIONS) {
                                // خواندن محتوای فایل تصویری
                                val buffer = ByteArrayOutputStream(256 * 1024)
                                val readBuf = ByteArray(8192)
                                var bytesRead: Int
                                while (zip.read(readBuf).also { bytesRead = it } != -1) {
                                    buffer.write(readBuf, 0, bytesRead)
                                }
                                entries.add(entry.name to buffer.toByteArray())
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }

        if (entries.isEmpty()) return@withContext emptyList()

        // مرحله ۲: مرتب‌سازی طبیعی بر اساس نام فایل (1, 2, ..., 10 به جای 1, 10, 2)
        val sorted = entries.sortedWith { a, b -> naturalCompare(a.first, b.first) }

        // مرحله ۳: دیکد و تغییر اندازه تصاویر
        val bitmaps = mutableListOf<Bitmap>()
        for ((index, pair) in sorted.withIndex()) {
            withContext(Dispatchers.Main) {
                onProgress?.invoke(index + 1, sorted.size)
            }

            val bitmap = decodeSampledBitmapFromBytes(pair.second, targetWidth)
            if (bitmap != null) {
                bitmaps.add(bitmap)
            }
        }

        bitmaps
    }

    /**
     * دیکد بهینه تصویر از آرایه بایت‌ها با تنظیم inSampleSize برای جلوگیری از OutOfMemory
     */
    private fun decodeSampledBitmapFromBytes(data: ByteArray, targetWidth: Int): Bitmap? {
        return try {
            // مرحله ۱: فقط ابعاد را بخوان
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)

            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) return null

            // مرحله ۲: محاسبه inSampleSize بهینه
            var sampleSize = 1
            while (origW / (sampleSize * 2) >= targetWidth) {
                sampleSize *= 2
            }

            // مرحله ۳: دیکد واقعی
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
                ?: return null

            // مرحله ۴: تغییر اندازه به عرض هدف اگر لازم باشد
            if (sampledBitmap.width == targetWidth) {
                sampledBitmap
            } else {
                val scale = targetWidth.toFloat() / sampledBitmap.width.toFloat()
                val scaledHeight = (sampledBitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(sampledBitmap, targetWidth, scaledHeight, true)
                if (scaled != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                scaled
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * مقایسه عددی-الفبایی طبیعی (مانند naturalCompare موجود در ManhwaReaderScreen)
     */
    private fun naturalCompare(s1: String, s2: String): Int {
        val regex = "(\\d+)|(\\D+)".toRegex()
        val match1 = regex.findAll(s1).map { it.value }.toList()
        val match2 = regex.findAll(s2).map { it.value }.toList()
        val minSize = minOf(match1.size, match2.size)
        for (i in 0 until minSize) {
            val part1 = match1[i]
            val part2 = match2[i]
            val num1 = part1.toLongOrNull()
            val num2 = part2.toLongOrNull()
            if (num1 != null && num2 != null) {
                val cmp = num1.compareTo(num2)
                if (cmp != 0) return cmp
            } else {
                val cmp = part1.compareTo(part2, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return match1.size.compareTo(match2.size)
    }
}
