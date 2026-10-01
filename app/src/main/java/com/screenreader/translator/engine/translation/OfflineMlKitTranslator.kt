package com.screenreader.translator.engine.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class OfflineMlKitTranslator {

    private val modelManager = RemoteModelManager.getInstance()
    private val persianModel = TranslateRemoteModel.Builder(TranslateLanguage.PERSIAN).build()

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.PERSIAN)
        .build()

    private val translator: Translator = Translation.getClient(options)

    /**
     * بررسی آیا مدل زبان فارسی قبلاً دانلود شده و آفلاین در دسترس است یا خیر
     */
    suspend fun isModelDownloaded(): Boolean {
        return try {
            val downloadedModels = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
            downloadedModels.any { it.language == TranslateLanguage.PERSIAN }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * دانلود مدل آفلاین زبان فارسی با حجم بهینه (~30MB)
     */
    suspend fun downloadModel(wifiOnly: Boolean = false): Boolean {
        return try {
            val conditionsBuilder = DownloadConditions.Builder()
            if (wifiOnly) {
                conditionsBuilder.requireWifi()
            }
            translator.downloadModelIfNeeded(conditionsBuilder.build()).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * ترجمه کاملاً آفلاین و بلادرنگ با هوش مصنوعی محلی دستگاه.
     * این مترجم فقط مبدأ انگلیسی دارد؛ متون کره‌ای/چینی/ژاپنی را رد می‌کند تا
     * به‌جای خروجی بی‌معنی، مخزن ترجمه به موتور آنلاین (تشخیص خودکار زبان) سوییچ کند.
     */
    suspend fun translate(text: String): String {
        if (containsCjk(text)) {
            throw UnsupportedOperationException("Offline translator only supports English source text")
        }
        return translator.translate(text).await()
    }

    private fun containsCjk(text: String): Boolean = text.any { c ->
        c in '\u1100'..'\u11FF' ||   // Hangul Jamo
            c in '\u3040'..'\u30FF' || // Hiragana + Katakana
            c in '\u3130'..'\u318F' || // Hangul Compatibility Jamo
            c in '\u3400'..'\u4DBF' || // CJK Extension A
            c in '\u4E00'..'\u9FFF' || // CJK Unified Ideographs
            c in '\uAC00'..'\uD7AF'    // Hangul Syllables
    }

    fun close() {
        translator.close()
    }
}
