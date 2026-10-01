package com.screenreader.translator.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * حافظ تنظیمات ایمن با رمزگذاری کلیدهای حساس (API Keys)
 * استفاده از EncryptedSharedPreferences برای محافظت از اطلاعات حساس
 */
class AppPreferences private constructor(context: Context) {

    // معمولی SharedPreferences برای تنظیمات عمومی
    private val regularPrefs = context.getSharedPreferences("screen_translator_regular", Context.MODE_PRIVATE)

    // رمزگذاری‌شده SharedPreferences برای API Keys و کلیدهای حساس
    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "screen_translator_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        e.printStackTrace()
        android.util.Log.w("AppPreferences", "Failed to init encrypted prefs, using fallback")
        regularPrefs
    }

    // MARK: - 🔐 Secure Properties (API Keys & Sensitive Data)

    var apiKey: String
        get() = securePrefs.getString("api_key", "") ?: ""
        set(value) {
            securePrefs.edit().putString("api_key", value.trim()).apply()
        }

    var customEndpoint: String
        get() = securePrefs.getString("custom_endpoint", "") ?: ""
        set(value) {
            securePrefs.edit().putString("custom_endpoint", value.trim()).apply()
        }

    // MARK: - General Settings

    var llmProvider: String
        get() = regularPrefs.getString("llm_provider", PROVIDER_NONE) ?: PROVIDER_NONE
        set(value) = regularPrefs.edit().putString("llm_provider", value).apply()

    var selectedModelsList: List<String>
        get() = regularPrefs.getString("selected_models_list", "")?.split("|") ?: emptyList()
        set(value) = regularPrefs.edit().putString("selected_models_list", value.joinToString("|")).apply()

    var customModel: String
        get() = regularPrefs.getString("custom_model", "") ?: ""
        set(value) = regularPrefs.edit().putString("custom_model", value).apply()

    // OCR Settings
    var ocrEngineMode: Int
        get() = regularPrefs.getInt("ocr_engine_mode", OCR_ENGINE_ML_KIT)
        set(value) = regularPrefs.edit().putInt("ocr_engine_mode", value).apply()

    var ocrLanguageMode: Int
        get() = regularPrefs.getInt("ocr_language_mode", OCR_LANG_ENGLISH)
        set(value) = regularPrefs.edit().putInt("ocr_language_mode", value).apply()

    var isOcrEnhanceContrast: Boolean
        get() = regularPrefs.getBoolean("ocr_enhance_contrast", true)
        set(value) = regularPrefs.edit().putBoolean("ocr_enhance_contrast", value).apply()

    // Image Preprocessing
    var jpegCompressionQuality: Int
        get() = regularPrefs.getInt("jpeg_compression_quality", 75)
        set(value) = regularPrefs.edit().putInt("jpeg_compression_quality", value.coerceIn(50, 95)).apply()

    var maxImageWidth: Int
        get() = regularPrefs.getInt("max_image_width", 1920)
        set(value) = regularPrefs.edit().putInt("max_image_width", value.coerceIn(640, 4096)).apply()

    // Database cleanup
    var translationCacheTTLDays: Int
        get() = regularPrefs.getInt("cache_ttl_days", 30)
        set(value) = regularPrefs.edit().putInt("cache_ttl_days", value.coerceIn(1, 365)).apply()

    var maxHistoryEntries: Int
        get() = regularPrefs.getInt("max_history_entries", 10000)
        set(value) = regularPrefs.edit().putInt("max_history_entries", value.coerceIn(100, 100000)).apply()

    fun clearSecureData() {
        securePrefs.edit().clear().apply()
    }

    fun clearAllData() {
        regularPrefs.edit().clear().apply()
        clearSecureData()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                AppPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Provider constants
        const val PROVIDER_NONE = "none"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_OPENROUTER = "openrouter"
        const val PROVIDER_DEEPSEEK = "deepseek"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_GROK = "grok"
        const val PROVIDER_CUSTOM = "custom"

        // OCR Engine modes
        const val OCR_ENGINE_ML_KIT = 0
        const val OCR_ENGINE_VISION = 1

        // OCR Language modes
        const val OCR_LANG_ENGLISH = 0
        const val OCR_LANG_KOREAN = 1
        const val OCR_LANG_CHINESE = 2
        const val OCR_LANG_ALL = 3
    }
}