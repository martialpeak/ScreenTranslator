package com.screenreader.translator.data.local

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.screenreader.translator.data.local.entity.GlossaryEntity
import com.screenreader.translator.data.local.entity.OfflineDictionaryEntity
import com.screenreader.translator.data.local.entity.TranslationCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * مدیر پشتیبان‌گیری، استخراج و درون‌ریزی پایگاه داده (TSV و JSON)
 * جهت پشتیبان‌گیری، انتقال بین دستگاه‌ها، استفاده در اکسل و غنی‌سازی واژه‌نامه محلی
 */
object DatabaseBackupManager {

    /**
     * استخراج پایگاه داده در قالب فایل متنی TSV (Tab-Separated Values)
     * قابل بازگشایی آسان در اکسل و نوت‌پد
     */
    suspend fun exportToTsv(
        context: Context,
        database: AppDatabase,
        targetUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dictionaryList = database.offlineDictionaryDao().getAllWords()
            val glossaryList = database.glossaryDao().getAllList()
            val cacheList = database.translationCacheDao().getAllCache()

            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8)).use { writer ->
                    // سطر سرستون‌ها (Header)
                    writer.write("واژه یا عبارت اصلی\tترجمه فارسی\tدسته‌بندی / نقش دستوری\tمنبع داده\n")

                    // ۱. نوشتن لغت‌نامه آفلاین
                    for (item in dictionaryList) {
                        val word = item.word.replace("\t", " ").replace("\n", " ")
                        val meaning = item.persianMeanings.replace("\t", " ").replace("\n", " ")
                        val category = (item.partOfSpeech ?: "عمومی").replace("\t", " ")
                        writer.write("$word\t$meaning\t$category\tلغت‌نامه آفلاین\n")
                    }

                    // ۲. نوشتن اصطلاح‌نامه سفارشی
                    for (item in glossaryList) {
                        val term = item.originalTerm.replace("\t", " ").replace("\n", " ")
                        val trans = item.customTranslation.replace("\t", " ").replace("\n", " ")
                        val cat = item.category.replace("\t", " ")
                        writer.write("$term\t$trans\t$cat\tاصطلاح‌نامه مانهوا\n")
                    }

                    // ۳. نوشتن کش جملات ترجمه شده
                    for (item in cacheList) {
                        val orig = item.originalText.replace("\t", " ").replace("\n", " ")
                        val trans = item.translatedText.replace("\t", " ").replace("\n", " ")
                        writer.write("$orig\t$trans\tجملات و دیالوگ‌ها\tکش ترجمه\n")
                    }

                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * استخراج ساخت‌یافته کل پایگاه داده در قالب فرمت استاندارد JSON
     */
    suspend fun exportToJson(
        context: Context,
        database: AppDatabase,
        targetUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dictionaryList = database.offlineDictionaryDao().getAllWords()
            val glossaryList = database.glossaryDao().getAllList()
            val cacheList = database.translationCacheDao().getAllCache()

            val exportMap = mapOf(
                "version" to 1,
                "exported_at" to System.currentTimeMillis(),
                "total_dictionary_count" to dictionaryList.size,
                "total_glossary_count" to glossaryList.size,
                "total_cache_count" to cacheList.size,
                "dictionary" to dictionaryList,
                "glossary" to glossaryList,
                "cache" to cacheList
            )

            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(exportMap)

            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * درون‌ریزی و افزودن داده‌ها به پایگاه داده محلی از فایل TSV اکسل
     * بازگرداندن تعداد کل واژه‌ها و اصطلاحات وارد شده با موفقیت
     */
    suspend fun importFromTsv(
        context: Context,
        database: AppDatabase,
        sourceUri: Uri
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            val dictList = mutableListOf<OfflineDictionaryEntity>()
            val glossaryList = mutableListOf<GlossaryEntity>()

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String?
                    var isFirstLine = true
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.isBlank()) continue

                        // بررسی و رد شدن از سطر سرستون در صورت وجود
                        if (isFirstLine && (currentLine.contains("واژه") || currentLine.contains("Word", ignoreCase = true) || currentLine.contains("Original", ignoreCase = true))) {
                            isFirstLine = false
                            continue
                        }
                        isFirstLine = false

                        val parts = currentLine.split("\t")
                        if (parts.size >= 2) {
                            val word = parts[0].trim()
                            val meaning = parts[1].trim()
                            val category = if (parts.size >= 3) parts[2].trim() else "عمومی"
                            val source = if (parts.size >= 4) parts[3].trim() else ""

                            if (word.isNotBlank() && meaning.isNotBlank()) {
                                if (source.contains("اصطلاح") || category.contains("اصطلاح") || category.contains("مانهوا")) {
                                    glossaryList.add(GlossaryEntity(originalTerm = word, customTranslation = meaning, category = category))
                                } else {
                                    dictList.add(OfflineDictionaryEntity(word = word, persianMeanings = meaning, partOfSpeech = category))
                                }
                                count++
                            }
                        }
                    }
                }
            }

            // ذخیره دسته‌ای جهت بهینه‌سازی سرعت و حافظه
            dictList.chunked(500).forEach { chunk ->
                database.offlineDictionaryDao().insertOrReplaceAll(chunk)
            }
            glossaryList.chunked(500).forEach { chunk ->
                database.glossaryDao().insertAll(chunk)
            }

            Result.success(count)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * بازگردانی و درون‌ریزی کل پایگاه داده از فایل بکاپ JSON
     * پشتیبانی از فرمت بکاپ رسمی برنامه و فایل‌های JSON ساخت‌یافته دلخواه
     */
    suspend fun importFromJson(
        context: Context,
        database: AppDatabase,
        sourceUri: Uri
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).readText()
            } ?: return@withContext Result.failure(Exception("خطا در باز کردن فایل JSON"))

            var totalImported = 0
            val gson = Gson()
            val jsonElement = JsonParser.parseString(content)

            if (jsonElement.isJsonObject) {
                val jsonObject = jsonElement.asJsonObject

                // فرمت کامل اکسترکت شده برنامه شامل کلیدهای dictionary, glossary, cache
                if (jsonObject.has("dictionary") || jsonObject.has("glossary") || jsonObject.has("cache")) {
                    if (jsonObject.has("dictionary")) {
                        val dictArray = jsonObject.getAsJsonArray("dictionary")
                        val listType = object : TypeToken<List<OfflineDictionaryEntity>>() {}.type
                        val dictList: List<OfflineDictionaryEntity> = gson.fromJson(dictArray, listType)
                        dictList.chunked(500).forEach { chunk ->
                            database.offlineDictionaryDao().insertOrReplaceAll(chunk)
                        }
                        totalImported += dictList.size
                    }

                    if (jsonObject.has("glossary")) {
                        val glossArray = jsonObject.getAsJsonArray("glossary")
                        val listType = object : TypeToken<List<GlossaryEntity>>() {}.type
                        val glossList: List<GlossaryEntity> = gson.fromJson(glossArray, listType)
                        glossList.chunked(500).forEach { chunk ->
                            database.glossaryDao().insertAll(chunk)
                        }
                        totalImported += glossList.size
                    }

                    if (jsonObject.has("cache")) {
                        val cacheArray = jsonObject.getAsJsonArray("cache")
                        val listType = object : TypeToken<List<TranslationCacheEntity>>() {}.type
                        val cacheList: List<TranslationCacheEntity> = gson.fromJson(cacheArray, listType)
                        cacheList.chunked(500).forEach { chunk ->
                            for (item in chunk) {
                                database.translationCacheDao().insertOrUpdate(item)
                            }
                        }
                        totalImported += cacheList.size
                    }
                } else {
                    // فایل دیکشنری کلید و مقداری {"hello": "سلام"}
                    val dictList = mutableListOf<OfflineDictionaryEntity>()
                    for ((k, v) in jsonObject.entrySet()) {
                        if (v.isJsonPrimitive) {
                            dictList.add(OfflineDictionaryEntity(word = k, persianMeanings = v.asString))
                        }
                    }
                    dictList.chunked(500).forEach { chunk ->
                        database.offlineDictionaryDao().insertOrReplaceAll(chunk)
                    }
                    totalImported += dictList.size
                }
            } else if (jsonElement.isJsonArray) {
                // آرایه جیسون لغات
                val listType = object : TypeToken<List<OfflineDictionaryEntity>>() {}.type
                val dictList: List<OfflineDictionaryEntity> = gson.fromJson(jsonElement, listType)
                dictList.chunked(500).forEach { chunk ->
                    database.offlineDictionaryDao().insertOrReplaceAll(chunk)
                }
                totalImported += dictList.size
            }

            Result.success(totalImported)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
