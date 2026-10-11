package com.screenreader.translator.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * مدیریت ترجیحات برنامه، تنظیمات هوش مصنوعی، زنجیره مدل‌ها و رفع ریت‌لیمیت
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var llmProvider: String
        get() = prefs.getString(KEY_LLM_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
        set(value) = prefs.edit().putString(KEY_LLM_PROVIDER, value).apply()

    fun getApiKeyForProvider(provider: String): String {
        val prefKey = when (provider) {
            PROVIDER_GEMINI -> KEY_API_KEY_GEMINI
            PROVIDER_DEEPSEEK -> KEY_API_KEY_DEEPSEEK
            PROVIDER_OPENROUTER -> KEY_API_KEY_OPENROUTER
            PROVIDER_OPENAI -> KEY_API_KEY_OPENAI
            PROVIDER_GROK -> KEY_API_KEY_GROK
            PROVIDER_GROQ -> KEY_API_KEY_GROQ
            PROVIDER_CUSTOM -> KEY_API_KEY_CUSTOM
            else -> KEY_API_KEY
        }
        val specific = readApiKey(prefKey)
        if (specific.isNotBlank()) return specific

        // Migration from legacy single key if available
        val legacy = readApiKey(KEY_API_KEY)
        if (legacy.isNotBlank()) {
            if (provider == PROVIDER_GROQ && legacy.startsWith("gsk_")) {
                writeApiKey(KEY_API_KEY_GROQ, legacy)
                return legacy
            }
            if (provider == PROVIDER_OPENROUTER && legacy.startsWith("sk-or-")) {
                writeApiKey(KEY_API_KEY_OPENROUTER, legacy)
                return legacy
            }
            if (provider == PROVIDER_GROK && legacy.startsWith("xai-")) {
                writeApiKey(KEY_API_KEY_GROK, legacy)
                return legacy
            }
            if (provider == llmProvider) {
                return legacy
            }
        }
        return ""
    }

    fun setApiKeyForProvider(provider: String, value: String) {
        val prefKey = when (provider) {
            PROVIDER_GEMINI -> KEY_API_KEY_GEMINI
            PROVIDER_DEEPSEEK -> KEY_API_KEY_DEEPSEEK
            PROVIDER_OPENROUTER -> KEY_API_KEY_OPENROUTER
            PROVIDER_OPENAI -> KEY_API_KEY_OPENAI
            PROVIDER_GROK -> KEY_API_KEY_GROK
            PROVIDER_GROQ -> KEY_API_KEY_GROQ
            PROVIDER_CUSTOM -> KEY_API_KEY_CUSTOM
            else -> KEY_API_KEY
        }
        val trimmed = value.trim()
        writeApiKey(prefKey, trimmed)
        if (provider == llmProvider) {
            writeApiKey(KEY_API_KEY, trimmed)
        }
    }

    /**
     * API keys are encrypted with an AES-GCM key held by Android Keystore.
     * Existing plaintext values are read once and transparently migrated.
     */
    private fun readApiKey(key: String): String {
        val stored = prefs.getString(key, "").orEmpty()
        if (stored.isBlank()) return ""
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            writeApiKey(key, stored)
            return stored
        }
        return runCatching { decryptApiKey(stored) }.getOrDefault("")
    }

    private fun writeApiKey(key: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(key).apply()
            return
        }
        // Never fall back to plaintext if Keystore is unavailable.
        prefs.edit().putString(key, encryptApiKey(value)).apply()
    }

    private fun encryptApiKey(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(iv.size + encrypted.size)
            .put(iv)
            .put(encrypted)
            .array()
        return ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decryptApiKey(value: String): String {
        val payload = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        require(payload.size > IV_SIZE) { "Invalid encrypted API key" }
        val iv = payload.copyOfRange(0, IV_SIZE)
        val encrypted = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey = synchronized(KEYSTORE_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        }
    }

    var apiKey: String
        get() = getApiKeyForProvider(llmProvider)
        set(value) = setApiKeyForProvider(llmProvider, value)

    var customModel: String
        get() = prefs.getString(KEY_CUSTOM_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_MODEL, value).apply()

    var customEndpoint: String
        get() = prefs.getString(KEY_CUSTOM_ENDPOINT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_ENDPOINT, value).apply()

    /**
     * دریافت لیست مدل‌های انتخاب شده به تفکیک ارائه‌دهنده
     */
    fun getSelectedModelsForProvider(provider: String): List<String> {
        val prefKey = "${KEY_SELECTED_MODELS}_$provider"
        val raw = prefs.getString(prefKey, "") ?: ""
        if (raw.isNotBlank()) {
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        if (provider == llmProvider) {
            val globalRaw = prefs.getString(KEY_SELECTED_MODELS, "") ?: ""
            if (globalRaw.isNotBlank()) {
                return globalRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        return emptyList()
    }

    fun setSelectedModelsForProvider(provider: String, models: List<String>) {
        val prefKey = "${KEY_SELECTED_MODELS}_$provider"
        val raw = models.distinct().joinToString(",")
        prefs.edit().putString(prefKey, raw).apply()
        if (provider == llmProvider) {
            prefs.edit().putString(KEY_SELECTED_MODELS, raw).apply()
        }
    }

    /**
     * لیست مدل‌های انتخاب شده به عنوان زنجیره پشتیبان (Fallback Chain) جهت رفع کامل ریت‌لیمیت
     */
    var selectedModelsList: List<String>
        get() = getSelectedModelsForProvider(llmProvider)
        set(value) = setSelectedModelsForProvider(llmProvider, value)

    fun toggleSelectedModel(model: String) {
        val current = selectedModelsList.toMutableList()
        if (current.contains(model)) {
            current.remove(model)
        } else {
            current.add(model)
        }
        selectedModelsList = current
    }

    var isAutoLearnEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LEARN, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LEARN, value).apply()

    var overlayOpacity: Float
        get() = prefs.getFloat(KEY_OVERLAY_OPACITY, 0.9f)
        set(value) = prefs.edit().putFloat(KEY_OVERLAY_OPACITY, value).apply()

    var bubbleTheme: String
        get() = prefs.getString(KEY_BUBBLE_THEME, BUBBLE_THEME_WHITE) ?: BUBBLE_THEME_WHITE
        set(value) = prefs.edit().putString(KEY_BUBBLE_THEME, value).apply()

    var bubbleFontSizeScale: Float
        get() = prefs.getFloat(KEY_BUBBLE_FONT_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_BUBBLE_FONT_SCALE, value).apply()

    var ocrLanguageMode: Int
        get() = prefs.getInt(KEY_OCR_LANGUAGE_MODE, OCR_LANG_ENGLISH)
        set(value) = prefs.edit().putInt(KEY_OCR_LANGUAGE_MODE, value).apply()

    var ocrEngineMode: Int
        get() = prefs.getInt(KEY_OCR_ENGINE_MODE, OCR_ENGINE_MLKIT)
        set(value) = prefs.edit().putInt(KEY_OCR_ENGINE_MODE, value).apply()

    var isOcrEnhanceContrast: Boolean
        get() = prefs.getBoolean(KEY_OCR_ENHANCE_CONTRAST, true)
        set(value) = prefs.edit().putBoolean(KEY_OCR_ENHANCE_CONTRAST, value).apply()

    companion object {
        private const val PREFS_NAME = "screen_translator_prefs"
        private const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_API_KEY_GEMINI = "llm_api_key_gemini"
        private const val KEY_API_KEY_DEEPSEEK = "llm_api_key_deepseek"
        private const val KEY_API_KEY_OPENROUTER = "llm_api_key_openrouter"
        private const val KEY_API_KEY_OPENAI = "llm_api_key_openai"
        private const val KEY_API_KEY_GROK = "llm_api_key_grok"
        private const val KEY_API_KEY_GROQ = "llm_api_key_groq"
        private const val KEY_API_KEY_CUSTOM = "llm_api_key_custom"
        private const val ENCRYPTED_PREFIX = "enc:v1:"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "screen_translator_api_keys"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
        private val KEYSTORE_LOCK = Any()

        private const val KEY_CUSTOM_MODEL = "llm_custom_model"
        private const val KEY_CUSTOM_ENDPOINT = "llm_custom_endpoint"
        private const val KEY_SELECTED_MODELS = "llm_selected_models"
        private const val KEY_AUTO_LEARN = "auto_learn_words"
        private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        private const val KEY_BUBBLE_THEME = "bubble_theme"
        private const val KEY_BUBBLE_FONT_SCALE = "bubble_font_scale"
        private const val KEY_OCR_LANGUAGE_MODE = "ocr_language_mode"
        private const val KEY_OCR_ENGINE_MODE = "ocr_engine_mode"
        private const val KEY_OCR_ENHANCE_CONTRAST = "ocr_enhance_contrast"

        const val OCR_LANG_ENGLISH = 0 // فوق‌سریع - تنها لاتین (پیش‌فرض)
        const val OCR_LANG_KOREAN = 1  // کره‌ای + لاتین
        const val OCR_LANG_CHINESE = 2 // چینی + لاتین
        const val OCR_LANG_ALL = 3     // همه زبان‌ها (همزمان)

        const val OCR_ENGINE_MLKIT = 0  // گوگل ML Kit (آفلاین پرسرعت - پیش‌فرض)
        const val OCR_ENGINE_VISION = 1 // هوش دیداری چندوجهی (Gemini / OpenRouter Vision - نهایت دقت ۹۹٪)
        const val OCR_ENGINE_RAPID = 2  // موتور عمیق آفلاین (RapidOCR / PP-OCRv4)

        const val BUBBLE_THEME_WHITE = "WHITE"
        const val BUBBLE_THEME_DARK = "DARK"
        const val BUBBLE_THEME_TRANSLUCENT = "TRANSLUCENT"

        const val PROVIDER_NONE = "NONE" // استفاده اختصاصی از دیکشنری محلی + ML Kit
        const val PROVIDER_GEMINI = "GEMINI"
        const val PROVIDER_DEEPSEEK = "DEEPSEEK"
        const val PROVIDER_OPENAI = "OPENAI"
        const val PROVIDER_OPENROUTER = "OPENROUTER"
        const val PROVIDER_GROK = "GROK"
        const val PROVIDER_GROQ = "GROQ"
        const val PROVIDER_CUSTOM = "CUSTOM"

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
