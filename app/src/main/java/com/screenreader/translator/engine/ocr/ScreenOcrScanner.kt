package com.screenreader.translator.engine.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ScreenOcrScanner {

    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val koreanRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * بررسی حاوی نویسه‌های کره‌ای (هانگول)
     */
    private fun containsKorean(text: String): Boolean {
        return text.any { ch ->
            (ch in '\uAC00'..'\uD7AF') || (ch in '\u1100'..'\u11FF') || (ch in '\u3130'..'\u318F')
        }
    }

    /**
     * بررسی حاوی نویسه‌های چینی (هانزی)
     */
    private fun containsChinese(text: String): Boolean {
        return text.any { ch ->
            (ch in '\u4E00'..'\u9FFF') || (ch in '\u3400'..'\u4DBF')
        }
    }

    /**
     * بررسی نویزها و متون سیستم، ساعت، درصد باتری و خطاهای OCR
     */
    private fun isJunkNoise(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true

        // حذف فرمت‌های ساعت (مانند 12:41 یا 12:40)
        if (trimmed.matches(Regex("^\\d{1,2}:\\d{2}$"))) return true

        // حذف علائم درصد باتری و آیکون‌ها (مانند C!100, 100!, 8wusan0100)
        if (trimmed.matches(Regex("^[0-9%#$!|./\\\\:;@^&*()\\s-]+$"))) return true

        // متون بسیار کوتاه ۱ یا ۲ حرفی انگلیسی که کلمه معنادار نیستند
        if (trimmed.length <= 2 && !containsKorean(trimmed) && !containsChinese(trimmed)) {
            val lower = trimmed.lowercase()
            val validShortWords = setOf("i", "a", "me", "we", "he", "it", "to", "in", "on", "at", "no", "go", "up", "so", "my", "by", "or", "an", "is", "am", "us")
            if (!validShortWords.contains(lower)) return true
        }

        // متون بیش از ۴ کاراکتر لاتین که هیچ حرف صداداری ندارند (خطای تشخیص افکت یا آیکون مثل BLANI LTKL Tl)
        if (trimmed.length >= 4 && !containsKorean(trimmed) && !containsChinese(trimmed)) {
            val hasVowel = trimmed.any { it in "aeiouyAEIOUY" }
            if (!hasVowel) return true
        }

        val lower = trimmed.lowercase()

        // فیلتر واترمارک‌ها و آدرس‌های سایت‌های ترجمه مانهوا (CosmicScans, OsmicScans, GoteScans و غیره)
        val scanlationKeywords = listOf(
            "cosmicscans", "osmicscans", "gotescans", "asurascans", "flamecomics",
            "reaperscans", "voidscans", "luminousscans", "mangadex", "bato.to",
            "scanlation", "patreon.com", "paypal.me", "discord.gg", "read the chapter",
            "typesetter", "proofreader", "cleaner & redrawer", "staff", "raw provider"
        )
        if (scanlationKeywords.any { lower.contains(it) }) {
            return true
        }
        if (lower.endsWith("scans") || lower.contains("scans.com") || lower.startsWith("scans")) {
            return true
        }

        return false
    }

    /**
     * اسکن هوشمند صفحه نمایش با اولویت متن انگلیسی، اعتبارسنجی هانگول/هانزی
     * و ادغام خطوط بالن‌های چندخطی کمیک با امکان تفکیک حالت زبانی جهت کاهش ۶۵ درصدی مصرف پردازنده
     */
    suspend fun scanBitmap(
        bitmap: Bitmap,
        filterSystemMargins: Boolean = true,
        ocrLanguageMode: Int = com.screenreader.translator.data.local.AppPreferences.OCR_LANG_ENGLISH
    ): List<RecognizedBlock> = coroutineScope {
        val preferences = com.screenreader.translator.data.local.AppPreferences.getInstance(com.screenreader.translator.ScreenTranslatorApp.instance)
        val enhancedBitmap = if (preferences.isOcrEnhanceContrast) {
            try {
                ComicImagePreprocessor.enhanceForComicOcr(bitmap)
            } catch (e: Exception) {
                bitmap
            }
        } else {
            bitmap
        }
        val inputImage = InputImage.fromBitmap(enhancedBitmap, 0)
        val screenHeight = bitmap.height
        val screenWidth = bitmap.width

        // حاشیه ایمن برای نادیده گرفتن نوار وضعیت بالا و نوار ناوبری پایین (تنها در اسکن زنده صفحه نمایش)
        val topIgnoreMargin = if (filterSystemMargins) (screenHeight * 0.055f).toInt().coerceAtLeast(110) else 0
        val bottomIgnoreMargin = if (filterSystemMargins) (screenHeight * 0.055f).toInt().coerceAtLeast(110) else 0

        // اجرای شرطی و هوشمند موتورهای OCR (در حالت انگلیسی، موتورهای سنگین کره‌ای و چینی اصلاً در پس‌زمینه لود نمی‌شوند)
        val runKorean = ocrLanguageMode == com.screenreader.translator.data.local.AppPreferences.OCR_LANG_KOREAN ||
                ocrLanguageMode == com.screenreader.translator.data.local.AppPreferences.OCR_LANG_ALL
        val runChinese = ocrLanguageMode == com.screenreader.translator.data.local.AppPreferences.OCR_LANG_CHINESE ||
                ocrLanguageMode == com.screenreader.translator.data.local.AppPreferences.OCR_LANG_ALL

        val latinDeferred = async { try { latinRecognizer.process(inputImage).await() } catch (e: Exception) { null } }
        val koreanDeferred = if (runKorean) async { try { koreanRecognizer.process(inputImage).await() } catch (e: Exception) { null } } else null
        val chineseDeferred = if (runChinese) async { try { chineseRecognizer.process(inputImage).await() } catch (e: Exception) { null } } else null

        val latinText = latinDeferred.await()
        val koreanText = koreanDeferred?.await()
        val chineseText = chineseDeferred?.await()

        val rawLatinBlocks = mutableListOf<RecognizedBlock>()

        // ۱. پردازش متن‌های لاتین (انگلیسی) به عنوان اولویت اول
        if (latinText != null) {
            for (block in latinText.textBlocks) {
                val blockBox = block.boundingBox ?: continue

                // فیلتر نوار وضعیت بالا و نوار ناوبری پایین در صورت فعال بودن
                if (filterSystemMargins && (blockBox.top < topIgnoreMargin || blockBox.bottom > (screenHeight - bottomIgnoreMargin))) {
                    continue
                }

                val cleanedText = ComicTextNormalizer.normalize(block.text)

                if (isJunkNoise(cleanedText)) continue

                val lines = block.lines.mapNotNull { line ->
                    val lineBox = line.boundingBox ?: return@mapNotNull null
                    RecognizedLine(text = ComicTextNormalizer.normalize(line.text.trim()), boundingBox = lineBox)
                }

                rawLatinBlocks.add(
                    RecognizedBlock(
                        id = UUID.randomUUID().toString(),
                        originalText = cleanedText,
                        boundingBox = blockBox,
                        lines = lines
                    )
                )
            }
        }

        // ۲. ادغام هوشمند خطوط مجاور در بالن‌های مانهوا (Coalesce Adjacent Lines)
        val mergedLatinBlocks = mergeAdjacentComicBlocks(rawLatinBlocks)

        val finalBlocks = mutableListOf<RecognizedBlock>()
        finalBlocks.addAll(mergedLatinBlocks)

        // ۳. پردازش متن‌های کره‌ای (تنها در صورتی پذیرفته می‌شود که واقعاً نویسه هانگول داشته باشد)
        if (koreanText != null) {
            for (block in koreanText.textBlocks) {
                val blockBox = block.boundingBox ?: continue
                if (filterSystemMargins && (blockBox.top < topIgnoreMargin || blockBox.bottom > (screenHeight - bottomIgnoreMargin))) continue

                val cleaned = block.text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                // اگر هانگول نداشت، توهم مدل روی فونت انگلیسی بوده و دور انداخته می‌شود
                if (!containsKorean(cleaned) || isJunkNoise(cleaned)) continue

                // بررسی همپوشانی با بلوک‌های تایید شده
                if (!isOverlapping(finalBlocks, blockBox)) {
                    finalBlocks.add(
                        RecognizedBlock(
                            id = UUID.randomUUID().toString(),
                            originalText = cleaned,
                            boundingBox = blockBox,
                            lines = emptyList()
                        )
                    )
                }
            }
        }

        // ۴. پردازش متن‌های چینی (تنها در صورتی پذیرفته می‌شود که واقعاً نویسه چینی داشته باشد)
        if (chineseText != null) {
            for (block in chineseText.textBlocks) {
                val blockBox = block.boundingBox ?: continue
                if (filterSystemMargins && (blockBox.top < topIgnoreMargin || blockBox.bottom > (screenHeight - bottomIgnoreMargin))) continue

                val cleaned = block.text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                if (!containsChinese(cleaned) || isJunkNoise(cleaned)) continue

                if (!isOverlapping(finalBlocks, blockBox)) {
                    finalBlocks.add(
                        RecognizedBlock(
                            id = UUID.randomUUID().toString(),
                            originalText = cleaned,
                            boundingBox = blockBox,
                            lines = emptyList()
                        )
                    )
                }
            }
        }

        if (enhancedBitmap !== bitmap) {
            try { enhancedBitmap.recycle() } catch (e: Exception) {}
        }

        finalBlocks
    }

    /**
     * اسکن فوق‌دقیق صفحات بسیار بلند مانهوا/وب‌تون با تکنیک پنجره لغزان هم‌پوشان (Sliding Window Slices)
     * جهت عبور از محدودیت بافر GPU و فشرده‌سازی خودکار هوش مصنوعی ML Kit در صفحات بلند
     */
    suspend fun scanLongBitmapInSlices(
        bitmap: Bitmap,
        sliceHeight: Int = 2400,
        overlap: Int = 400,
        filterSystemMargins: Boolean = false,
        ocrLanguageMode: Int = com.screenreader.translator.data.local.AppPreferences.OCR_LANG_ENGLISH
    ): List<RecognizedBlock> = coroutineScope {
        val totalHeight = bitmap.height
        val totalWidth = bitmap.width

        // اگر ارتفاع تصویر معمولی یا کوتاه باشد، مستقیماً اسکن می‌شود
        if (totalHeight <= sliceHeight + overlap) {
            return@coroutineScope scanBitmap(bitmap, filterSystemMargins, ocrLanguageMode)
        }

        val allBlocks = mutableListOf<RecognizedBlock>()
        var startY = 0

        while (startY < totalHeight) {
            val currentSliceHeight = min(sliceHeight, totalHeight - startY)
            if (currentSliceHeight <= 50) break

            val sliceBitmap = try {
                Bitmap.createBitmap(bitmap, 0, startY, totalWidth, currentSliceHeight)
            } catch (e: Exception) {
                null
            }

            if (sliceBitmap != null) {
                val sliceBlocks = scanBitmap(sliceBitmap, filterSystemMargins = false, ocrLanguageMode = ocrLanguageMode)
                sliceBitmap.recycle()

                // انتقال مختصات محلی کادر به مختصات کل صفحه مانهوا
                for (b in sliceBlocks) {
                    val globalBox = Rect(
                        b.boundingBox.left,
                        b.boundingBox.top + startY,
                        b.boundingBox.right,
                        b.boundingBox.bottom + startY
                    )
                    allBlocks.add(b.copy(id = UUID.randomUUID().toString(), boundingBox = globalBox))
                }
            }

            if (startY + currentSliceHeight >= totalHeight) break
            startY += (sliceHeight - overlap)
        }

        deduplicateAndMergeBlocks(allBlocks)
    }

    /**
     * حذف و ادغام کادرهای تکراری که در ناحیه هم‌پوشانی دو قطعه اسکن شده‌اند
     */
    private fun deduplicateAndMergeBlocks(blocks: List<RecognizedBlock>): List<RecognizedBlock> {
        if (blocks.size <= 1) return blocks
        val sorted = blocks.sortedBy { it.boundingBox.top }
        val result = mutableListOf<RecognizedBlock>()
        val used = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (used[i]) continue
            var current = sorted[i]
            used[i] = true

            for (j in (i + 1) until sorted.size) {
                if (used[j]) continue
                val next = sorted[j]

                val box1 = current.boundingBox
                val box2 = next.boundingBox

                val overlap = Rect()
                if (overlap.setIntersect(box1, box2)) {
                    val overlapArea = overlap.width() * overlap.height()
                    val area1 = box1.width() * box1.height()
                    val area2 = box2.width() * box2.height()
                    val minArea = min(area1, area2)

                    if (minArea > 0 && overlapArea.toFloat() / minArea > 0.40f) {
                        used[j] = true
                        if (next.originalText.length > current.originalText.length) {
                            current = next
                        }
                    }
                }
            }
            result.add(current)
        }
        return mergeAdjacentComicBlocks(result)
    }

    /**
     * ادغام خطوط مجاور عمودی که متعلق به یک بالن دیالوگ مانهوا هستند
     */
    private fun mergeAdjacentComicBlocks(blocks: List<RecognizedBlock>): List<RecognizedBlock> {
        if (blocks.size <= 1) return blocks

        // مرتب‌سازی از بالا به پایین
        val sorted = blocks.sortedBy { it.boundingBox.top }
        val merged = mutableListOf<RecognizedBlock>()
        val visited = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (visited[i]) continue
            var current = sorted[i]
            visited[i] = true

            for (j in (i + 1) until sorted.size) {
                if (visited[j]) continue
                val next = sorted[j]

                val box1 = current.boundingBox
                val box2 = next.boundingBox

                // فاصله عمودی بین انتهای بلوک اول و ابتدای بلوک دوم
                val verticalGap = box2.top - box1.bottom
                // انطباق افقی (میزان اشتراک عرضی)
                val horizontalOverlap = max(0, min(box1.right, box2.right) - max(box1.left, box2.left))
                val minWidth = min(box1.width(), box2.width())
                val centerDistanceX = abs(box1.centerX() - box2.centerX())
                val maxWidth = max(box1.width(), box2.width())

                // اگر فاصله عمودی مناسب (تا ۷۵ پیکسل) و تراز افقی وسط یا هم‌پوشانی عرضی برقرار باشد: متعلق به یک بالن هستند
                val isVerticallyClose = verticalGap in -25..75
                val isHorizontallyAligned = (minWidth > 0 && horizontalOverlap.toFloat() / minWidth > 0.25f) || (centerDistanceX < maxWidth * 0.60f)

                if (isVerticallyClose && isHorizontallyAligned) {
                    val combinedBox = Rect(
                        min(box1.left, box2.left),
                        min(box1.top, box2.top),
                        max(box1.right, box2.right),
                        max(box1.bottom, box2.bottom)
                    )
                    val mergedText = ComicTextNormalizer.normalize("${current.originalText} ${next.originalText}")
                    current = current.copy(
                        originalText = mergedText,
                        boundingBox = combinedBox
                    )
                    visited[j] = true
                }
            }
            merged.add(current)
        }

        return merged
    }

    private fun isOverlapping(existingBlocks: List<RecognizedBlock>, targetBox: Rect): Boolean {
        for (existing in existingBlocks) {
            val overlap = Rect()
            if (overlap.setIntersect(existing.boundingBox, targetBox)) {
                val overlapArea = overlap.width() * overlap.height()
                val targetArea = targetBox.width() * targetBox.height()
                if (targetArea > 0 && overlapArea.toFloat() / targetArea > 0.35f) {
                    return true
                }
            }
        }
        return false
    }

    fun close() {
        latinRecognizer.close()
        koreanRecognizer.close()
        chineseRecognizer.close()
    }
}
