package com.screenreader.translator.data.repository

import com.screenreader.translator.data.local.AppDatabase
import com.screenreader.translator.data.local.entity.HistoryEntity
import com.screenreader.translator.data.local.entity.TranslationCacheEntity
import com.screenreader.translator.engine.ocr.RecognizedBlock
import com.screenreader.translator.engine.translation.OfflineMlKitTranslator
import com.screenreader.translator.engine.translation.OnlineFallbackTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.security.MessageDigest

import com.screenreader.translator.ScreenTranslatorApp
import com.screenreader.translator.data.local.AppPreferences
import com.screenreader.translator.data.local.entity.GlossaryEntity
import com.screenreader.translator.data.local.entity.OfflineDictionaryEntity
import com.screenreader.translator.engine.translation.BatchLlmTranslator

class TranslationRepository(
    private val database: AppDatabase,
    private val offlineTranslator: OfflineMlKitTranslator = OfflineMlKitTranslator(),
    private val onlineTranslator: OnlineFallbackTranslator = OnlineFallbackTranslator(),
    private val preferences: AppPreferences = AppPreferences.getInstance(ScreenTranslatorApp.instance)
) {

    private val cacheDao = database.translationCacheDao()
    private val historyDao = database.historyDao()
    private val glossaryDao = database.glossaryDao()
    private val dictionaryDao = database.offlineDictionaryDao()
    private val batchLlmTranslator = BatchLlmTranslator(preferences)

    // لایه حافظه پرسرعت کش L1 In-Memory برای پاسخ‌دهی در کمتر از ۱ میلی‌ثانیه بدون I/O دیسک
    private val memoryCache = android.util.LruCache<String, String>(1000)

    suspend fun isOfflineModelReady(): Boolean {
        return offlineTranslator.isModelDownloaded()
    }

    suspend fun downloadOfflineModel(): Boolean {
        return offlineTranslator.downloadModel()
    }

    /**
     * ترجمه تک متن بر اساس پایپ‌لاین هوشمند:
     * کش حافظه رم (L1) -> اصطلاحات کاربر -> دیکشنری آفلاین -> کش پایگاه داده -> مدل آفلاین -> سرور آنلاین -> ذخیره در کش
     */
    suspend fun translateText(text: String, sourceApp: String? = null): String = withContext(Dispatchers.IO) {
        val trimmed = com.screenreader.translator.engine.ocr.ComicTextNormalizer.normalize(text).trim()
        if (trimmed.isEmpty()) return@withContext ""

        val hash = calculateSha256(trimmed.lowercase())

        // ۰. بررسی سریع در کش فوق‌سریع L1 در حافظه رم (< 1ms)
        val memHit = memoryCache.get(hash)
        if (memHit != null) {
            return@withContext memHit
        }

        // پاکسازی هوشمند علائم انتهای متن برای تطبیق اصطلاحات بالن‌های مانهوا
        val cleanKeyword = trimmed.trimEnd('.', '!', '?', ',', ':', ';', '"', '\'', '-', '~', '…', ' ').trim()
        val upper = cleanKeyword.uppercase()

        // عبارات و جداول رتبه‌بندی مانهوا
        if (upper.contains("SLAVE") && upper.contains("NOBLEMAN")) {
            val res = "برده | رعیت | نجیب‌زاده | پادشاه"
            memoryCache.put(hash, res)
            return@withContext res
        }
        if (upper == "WEAK HERO") {
            val res = "قهرمان ضعیف"
            memoryCache.put(hash, res)
            return@withContext res
        }

        // 1. بررسی در اصطلاح‌نامه تخصصی مانهوا و کمیک
        val manhwaMatch = com.screenreader.translator.engine.translation.ManhwaLexicon.findManhwaPhrase(trimmed)
            ?: com.screenreader.translator.engine.translation.ManhwaLexicon.findManhwaPhrase(cleanKeyword)
        if (manhwaMatch != null) {
            memoryCache.put(hash, manhwaMatch)
            return@withContext manhwaMatch
        }

        // 2. بررسی در اصطلاح‌نامه سفارشی مانهوا و بازی‌ها
        val customTerm = glossaryDao.findCustomTranslation(trimmed)
            ?: glossaryDao.findCustomTranslation(cleanKeyword)
        if (customTerm != null) {
            memoryCache.put(hash, customTerm.customTranslation)
            return@withContext customTerm.customTranslation
        }

        // 3. بررسی در لغت‌نامه آفلاین داخلی
        val dictWord = dictionaryDao.lookupWord(trimmed)
            ?: dictionaryDao.lookupWord(cleanKeyword)
        if (dictWord != null) {
            memoryCache.put(hash, dictWord.persianMeanings)
            return@withContext dictWord.persianMeanings
        }

        // 4. بررسی در کش دیتابیس Room با هش بدون حساسیت به حروف کوچک/بزرگ (Case-Insensitive)
        val cached = cacheDao.getByHash(hash)
        if (cached != null) {
            cacheDao.incrementHitCount(hash)
            memoryCache.put(hash, cached.translatedText)
            return@withContext cached.translatedText
        }

        // 5. ترجمه جدید از طریق موتور آفلاین یا آنلاین
        var translatedResult: String? = null

        // تلاش با موتور آفلاین در صورت آماده بودن مدل
        try {
            translatedResult = offlineTranslator.translate(trimmed)
        } catch (e: Exception) {
            // مدل آفلاین آماده نبوده یا خطا داده است
        }

        // در صورت عدم موفقیت موتور آفلاین، استفاده از موتور آنلاین پشتیبان
        if (translatedResult.isNullOrEmpty()) {
            try {
                translatedResult = onlineTranslator.translate(trimmed)
            } catch (e: Exception) {
                translatedResult = "[خطا در ترجمه: نیازمند اینترنت یا مدل آفلاین]"
            }
        }

        val rawResult = translatedResult ?: ""
        // تبدیل ترجمه به لحن کاملاً خودمونی، روان و لذت‌بخش مانهوا
        val finalResult = com.screenreader.translator.engine.translation.ColloquialTransformer.makeConversational(rawResult)

        // 6. ذخیره هوشمند در کش دیتابیس و حافظه پرسرعت رم (L1)
        if (finalResult.isNotEmpty() && !finalResult.startsWith("[خطا")) {
            memoryCache.put(hash, finalResult)
            cacheDao.insertOrUpdate(
                TranslationCacheEntity(
                    sourceHash = hash,
                    originalText = trimmed,
                    translatedText = finalResult
                )
            )

            // ذخیره در تاریخچه
            historyDao.insert(
                HistoryEntity(
                    originalText = trimmed,
                    translatedText = finalResult,
                    sourceApp = sourceApp
                )
            )
        }

        finalResult
    }

    /**
     * ترجمه کلیه بلوک‌های متنی کشف شده از صفحه نمایش یا کتابخوان مانهوا
     * در صورت فعال بودن هوش مصنوعی، متون ترجمه نشده در ۱ درخواست واحد (Batch) ارسال می‌شوند
     * تا هم از خطای Rate-Limit جلوگیری شود و هم پیوستگی مکالمات حفظ گردد.
     */
    suspend fun translateBlocks(
        blocks: List<RecognizedBlock>,
        sourceApp: String? = null,
        sourceBitmap: android.graphics.Bitmap? = null
    ): List<RecognizedBlock> = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) return@withContext emptyList()

        val results = arrayOfNulls<RecognizedBlock>(blocks.size)
        val missingIndices = mutableListOf<Int>()

        // فیلتر سریع موارد موجود در کش L1 و پایگاه داده برای صرفه‌جویی ۱۰۰٪ در مصرف توکن و پردازش
        for (i in blocks.indices) {
            val block = blocks[i]
            val normalized = com.screenreader.translator.engine.ocr.ComicTextNormalizer.normalize(block.originalText).trim()
            if (normalized.isEmpty()) {
                results[i] = block.copy(translatedText = "")
                continue
            }
            val hash = calculateSha256(normalized.lowercase())
            val memCached = memoryCache.get(hash)
            if (memCached != null) {
                results[i] = block.copy(translatedText = memCached)
                continue
            }
            val dbCached = cacheDao.getByHash(hash)
            if (dbCached != null) {
                cacheDao.incrementHitCount(hash)
                memoryCache.put(hash, dbCached.translatedText)
                results[i] = block.copy(translatedText = dbCached.translatedText)
                continue
            }
            missingIndices.add(i)
        }

        // اگر تمامی بلوک‌ها قبلاً کش شده بودند، فوراً با تأخیر ۰ میلی‌ثانیه برگردانده می‌شوند
        if (missingIndices.isEmpty()) {
            return@withContext results.filterNotNull()
        }

        val isLlmConfigured = preferences.llmProvider != AppPreferences.PROVIDER_NONE && preferences.apiKey.isNotBlank()

        if (isLlmConfigured) {
            try {
                // ارسال دسته‌ای متون ترجمه نشده به هوش مصنوعی (Gemini / Grok / GPT) به همراه تصویر در صورت فعال بودن Vision
                val missingBlocks = missingIndices.map { blocks[it] }
                val textList = missingBlocks.map { com.screenreader.translator.engine.ocr.ComicTextNormalizer.normalize(it.originalText) }
                val batchTranslations = batchLlmTranslator.translateBatch(textList, sourceBitmap)

                if (batchTranslations.isNotEmpty()) {
                    missingIndices.forEachIndexed { batchIdx, origIdx ->
                        val block = blocks[origIdx]
                        val batchText = batchTranslations[batchIdx]
                        val finalTrans = if (!batchText.isNullOrBlank()) {
                            val colloquial = com.screenreader.translator.engine.translation.ColloquialTransformer.makeConversational(batchText)
                            
                            // ۱. ذخیره فوری در کش رم L1 و دیتابیس (۰ میلی‌ثانیه برای بارهای بعد)
                            val hash = calculateSha256(block.originalText.trim().lowercase())
                            memoryCache.put(hash, colloquial)
                            cacheDao.insertOrUpdate(
                                TranslationCacheEntity(
                                    sourceHash = hash,
                                    originalText = block.originalText.trim(),
                                    translatedText = colloquial
                                )
                            )

                            // ۲. یادگیری خودکار واژگان و اصطلاحات جدید در دیتابیس
                            if (preferences.isAutoLearnEnabled) {
                                autoLearnWords(block.originalText.trim(), colloquial)
                            }

                            // ۳. ثبت در تاریخچه
                            historyDao.insert(
                                HistoryEntity(
                                    originalText = block.originalText.trim(),
                                    translatedText = colloquial,
                                    sourceApp = sourceApp
                                )
                            )
                            colloquial
                        } else {
                            translateText(block.originalText, sourceApp)
                        }
                        results[origIdx] = block.copy(translatedText = finalTrans)
                    }
                    return@withContext results.filterNotNull()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // در صورت عدم تنظیم هوش مصنوعی یا خطا در شبکه، فال‌بک موازی به دیکشنری و ML Kit
        val deferredList = missingIndices.map { origIdx ->
            val block = blocks[origIdx]
            async {
                val translation = translateText(block.originalText, sourceApp)
                origIdx to block.copy(translatedText = translation)
            }
        }
        deferredList.awaitAll().forEach { (origIdx, translatedBlock) ->
            results[origIdx] = translatedBlock
        }

        results.filterNotNull()
    }

    /**
     * سیستم خودآموز دیتابیس: ثبت کلمات و عبارات کوتاه استخراج شده در لغت‌نامه آفلاین و اصطلاح‌نامه
     */
    /**
     * حذف بنیادین یک ترجمه از کلیه سطوح رم، کش و دیتابیس تا دیگر تحت هیچ شرایطی بازنگردد.
     */
    suspend fun deleteTranslationCompletely(originalText: String) = withContext(Dispatchers.IO) {
        val trimmed = originalText.trim()
        if (trimmed.isEmpty()) return@withContext

        val clean = trimmed.trimEnd('.', '!', '?', ',', ':', ';', '"', '\'', '-', '~', '…', ' ').trim()
        val hash = calculateSha256(trimmed.lowercase())
        val cleanHash = calculateSha256(clean.lowercase())

        // 1. حذف از حافظه رم L1
        memoryCache.remove(hash)
        memoryCache.remove(cleanHash)

        // 2. حذف از کش پایگاه داده Room
        cacheDao.deleteByHash(hash)
        cacheDao.deleteByHash(cleanHash)
        cacheDao.deleteByOriginalText(trimmed)
        cacheDao.deleteByOriginalText(clean)

        // 3. حذف از اصطلاحات سفارشی (در صورت وجود)
        glossaryDao.deleteByTerm(trimmed)
        glossaryDao.deleteByTerm(clean)

        // 4. حذف از واژه‌نامه آفلاین در صورتی که توسط آموزش خودکار اضافه شده باشد
        dictionaryDao.deleteAutoLearnedWord(trimmed)
        dictionaryDao.deleteAutoLearnedWord(clean)
    }

    /**
     * پاکسازی صرفاً واژگان اشتباه ثبت‌شده ناشی از یادگیری خودکار بدون دستکاری کش ترجمه‌های معتبر کاربر
     */
    suspend fun purgeCorruptedAutoLearned(): Int = withContext(Dispatchers.IO) {
        val deletedGlossary = glossaryDao.deleteByCategory("مانهوا (آموزش‌دیده)")
        val deletedDict = dictionaryDao.deleteByPartOfSpeech("مانهوا (آموزش‌دیده)")
        deletedGlossary + deletedDict
    }

    /**
     * پاکسازی کامل کلیه کش‌های رم و دیتابیس به همراه واژگان آموزش‌دیده اشتباه
     */
    suspend fun clearAllCachesAndAutoLearned(): Int = withContext(Dispatchers.IO) {
        // ۱. تخلیه رم
        memoryCache.evictAll()

        // ۲. تخلیه کش ترجمه‌ها
        cacheDao.clearCache()

        // ۳. پاکسازی واژگان اشتباه ثبت‌شده در اصطلاح‌نامه و دیکشنری
        val deletedGlossary = glossaryDao.deleteByCategory("مانهوا (آموزش‌دیده)")
        val deletedDict = dictionaryDao.deleteByPartOfSpeech("مانهوا (آموزش‌دیده)")

        deletedGlossary + deletedDict
    }

    private val commonStopWords = setOf(
        "you", "your", "yours", "yourself",
        "i", "me", "my", "mine", "myself",
        "he", "him", "his", "himself",
        "she", "her", "hers", "herself",
        "it", "its", "itself",
        "we", "us", "our", "ours", "ourselves",
        "they", "them", "their", "theirs", "themselves",
        "this", "that", "these", "those",
        "what", "which", "who", "whom", "whose", "where", "when", "why", "how",
        "is", "am", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did",
        "a", "an", "the", "and", "but", "or", "if", "because", "as", "until", "while",
        "of", "at", "by", "for", "with", "about", "against", "between", "into", "through", "during", "before", "after", "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", "over", "under", "again", "further", "then", "once",
        "here", "there", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very", "can", "will", "just", "don", "should", "now", "stop", "wake", "sure", "yeah", "hey", "last", "haha", "yes", "oh", "ah", "um", "uh", "ok", "okay"
    )

    /**
     * سیستم خودآموز دیتابیس با اعتبارسنجی دقیق:
     * هرگز ضمایر، کلمات دستوری یا اصطلاحات چندکلمه‌ای را به عنوان لغت خام ذخیره نمی‌کند
     * و هرگز اصطلاح‌نامه سفارشی کاربر را آلوده نمی‌سازد.
     */
    private suspend fun autoLearnWords(originalText: String, translatedText: String) {
        try {
            val cleanOriginal = originalText.trim().trimEnd('.', '!', '?', ',', ':', ';', '"', '\'', '-', '~', '…', ' ')
            val lower = cleanOriginal.lowercase()

            // رد کردن کلمات ممنوعه، ضمایر و علائم
            if (commonStopWords.contains(lower)) return

            val words = cleanOriginal.split("\\s+".toRegex())

            // تنها تک‌کلمه‌های با طول ۴ تا ۲۰ حرف انگلیسی الفبایی خالص که معنی مشخص دارند
            if (words.size == 1 && cleanOriginal.matches(Regex("^[a-zA-Z]{4,20}$"))) {
                if (translatedText.isNotBlank() && !translatedText.startsWith("[") && translatedText.length in 2..25 && !translatedText.contains("\n")) {
                    // فقط با استراتژی IGNORE در لغت‌نامه ثبت می‌شود تا واژگان اصلی را خراب نکند
                    dictionaryDao.insertAll(
                        listOf(
                            OfflineDictionaryEntity(
                                word = cleanOriginal,
                                persianMeanings = translatedText.trim(),
                                partOfSpeech = "مانهوا (آموزش‌دیده)"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
