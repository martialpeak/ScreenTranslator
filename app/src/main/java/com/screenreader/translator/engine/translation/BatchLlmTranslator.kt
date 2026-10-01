package com.screenreader.translator.engine.translation

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.screenreader.translator.data.local.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * موتور ترجمه دسته‌ای هوش مصنوعی (Single-Request Batch LLM Translator)
 * مجهز به زنجیره خودکار چند مدلی (Multi-Model Fallback Chain) برای رفع قطعی ریت‌لیمیت
 * و پشتیبانی از آرشیو جامع مدل‌های Gemini, DeepSeek, OpenAI, OpenRouter, Grok
 */
class BatchLlmTranslator(
    private val preferences: AppPreferences
) {

    private val client = OkHttpClient.Builder()
        .dns(ResilientDns())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class BatchItem(
        val id: Int,
        val text: String
    )

    data class ConnectionTestResult(
        val isSuccess: Boolean,
        val latencyMs: Long = 0,
        val modelName: String = "",
        val sampleTranslation: String? = null,
        val message: String = ""
    )

    /**
     * بررسی رایگان بودن مدل بر اساس ارائه‌دهنده یا شناسه مدل
     */
    fun isModelFree(provider: String, modelId: String): Boolean {
        return when (provider) {
            AppPreferences.PROVIDER_GROQ -> true // سرورهای Groq برای تمامی این مدل‌ها رایگان هستند
            AppPreferences.PROVIDER_GEMINI -> true // جمینای دارای سقف مصرف رایگان (15 درخواست در دقیقه) است
            AppPreferences.PROVIDER_OPENROUTER -> modelId.contains(":free", ignoreCase = true) ||
                    modelId.startsWith("openrouter/free", ignoreCase = true) ||
                    modelId == "stealth/space-bunny-alpha"
            else -> false
        }
    }

    /**
     * آرشیو جامع و کامل مدل‌های هوش مصنوعی با اولویت‌بندی مدل‌های رایگان (Free)
     */
    fun getPresetModels(provider: String): List<String> {
        return when (provider) {
            AppPreferences.PROVIDER_GEMINI -> listOf(
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-1.5-flash-8b",
                "gemini-2.0-flash-lite-preview-02-05",
                "gemini-1.5-pro",
                "gemini-2.0-pro-exp-02-05",
                "gemini-exp-1206"
            )
            AppPreferences.PROVIDER_GROQ -> listOf(
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "deepseek-r1-distill-llama-70b",
                "llama3-70b-8192",
                "gemma2-9b-it",
                "mixtral-8x7b-32768"
            )
            AppPreferences.PROVIDER_OPENROUTER -> listOf(
                "nvidia/nemotron-3-ultra-550b-a55b:free",
                "poolside/laguna-s-2.1:free",
                "dots-studio/dots-3-note-preview:free",
                "qwen/qwen3.8-27b:free",
                "nvidia/nemotron-3.5-lightning:free",
                "google/gemma-4-31b-it:free",
                "google/gemma-4-26b-a4b-it:free",
                "nvidia/nemotron-3-super-120b-a12b:free",
                "inclusionai/ling-3.0-flash-sante:free",
                "liquid/lfm-2.5-2.6b:free",
                "thinkingmachines/inkling-small:free",
                "thinkingmachines/inkling:free",
                "poolside/laguna-xs-2.1:free",
                "cohere/north-mini-code:free",
                "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
                "nvidia/nemotron-3.5-content-safety:free",
                "stealth/space-bunny-alpha",
                "openrouter/free",
                "anthropic/claude-3.5-sonnet",
                "anthropic/claude-3.5-haiku",
                "openai/gpt-4o-mini",
                "openai/gpt-4o",
                "openai/o3-mini",
                "deepseek/deepseek-chat",
                "deepseek/deepseek-reasoner",
                "google/gemini-2.0-flash-001",
                "google/gemini-1.5-pro",
                "qwen/qwen-2.5-72b-instruct",
                "mistralai/mistral-large-2411",
                "meta-llama/llama-3.3-70b-instruct"
            )
            AppPreferences.PROVIDER_DEEPSEEK -> listOf(
                "deepseek-chat",
                "deepseek-reasoner"
            )
            AppPreferences.PROVIDER_OPENAI -> listOf(
                "gpt-4o-mini",
                "gpt-4o",
                "o3-mini",
                "o1-mini",
                "gpt-4-turbo",
                "gpt-3.5-turbo"
            )
            AppPreferences.PROVIDER_GROK -> listOf(
                "grok-2-1212",
                "grok-2-vision-1212",
                "grok-beta"
            )
            AppPreferences.PROVIDER_CUSTOM -> listOf(
                "gpt-4o-mini",
                "deepseek-chat",
                "gemini-1.5-flash",
                "claude-3-haiku"
            )
            else -> emptyList()
        }
    }

    /**
     * تعیین مدل پیش‌فرض برای هر ارائه‌دهنده
     */
    fun getDefaultModelForProvider(provider: String): String {
        return when (provider) {
            AppPreferences.PROVIDER_GEMINI -> "gemini-2.0-flash"
            AppPreferences.PROVIDER_GROQ -> "llama-3.3-70b-versatile"
            AppPreferences.PROVIDER_OPENROUTER -> "nvidia/nemotron-3-ultra-550b-a55b:free"
            AppPreferences.PROVIDER_DEEPSEEK -> "deepseek-chat"
            AppPreferences.PROVIDER_GROK -> "grok-2-1212"
            else -> "gpt-4o-mini"
        }
    }

    /**
     * ترجمه دسته‌ای کل لیست متون با سیستم زنجیره چند مدلی (Multi-Model Fallback Chain)
     * و پشتیبانی از هوش مصنوعی دیداری (Vision AI) برای درک بصری بالن‌های مانهوا
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceBitmap: android.graphics.Bitmap? = null
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        val provider = preferences.llmProvider
        val apiKey = preferences.apiKey.trim()

        if (provider == AppPreferences.PROVIDER_NONE || apiKey.isEmpty()) {
            return@withContext emptyMap()
        }

        // لیست مدل‌های نامزد برای ترجمه به ترتیب اولویت (با حفظ اولویت مدل کاربر و افزودن مدل‌های معتبر پشتیبان)
        val candidateModels = mutableListOf<String>()
        if (preferences.selectedModelsList.isNotEmpty()) {
            candidateModels.addAll(preferences.selectedModelsList)
        } else if (preferences.customModel.isNotBlank()) {
            candidateModels.add(preferences.customModel)
        } else {
            candidateModels.add(getDefaultModelForProvider(provider))
        }

        // افزودن مدل‌های رایگان و باکیفیت به عنوان پشتیبان برای جلوگیری از شکست در صورت خطای یک مدل
        for (preset in getPresetModels(provider)) {
            if (!candidateModels.contains(preset)) {
                candidateModels.add(preset)
            }
        }

        val items = texts.mapIndexed { index, text -> BatchItem(index, text) }
        val itemsJson = gson.toJson(items)

        val isVisionActive = preferences.ocrEngineMode == AppPreferences.OCR_ENGINE_VISION && sourceBitmap != null
        val imageBase64 = if (isVisionActive) {
            try {
                com.screenreader.translator.engine.ocr.ComicImagePreprocessor.encodeToCompactBase64Jpeg(sourceBitmap!!)
            } catch (e: Exception) {
                null
            }
        } else null

        val visionInstruction = if (imageBase64 != null) {
            "\nNote: A visual image of the comic page/panel is attached. Use the visual image to accurately read stylized/handwritten fonts and resolve any OCR ambiguities in the text blocks."
        } else ""

        val systemPrompt = """
            You are an expert manhwa and webtoon scanlator and comic translator.
            Translate the following JSON array of comic dialogue blocks into natural, punchy, colloquial Persian (فارسی عامیانه، خودمونی و مانهوایی).
            Guidelines:
            1. Use natural informal Persian used in comic scanlations (e.g. «می‌خوای چیکار کنی؟» instead of «می‌خواهید چه کار کنید»).
            2. Match character emotion, excitement, fury, and Korean action/school manhwa vibes.
            3. Maintain context between dialogues as they appear on the same manhwa page/scene.
            4. Fix comic OCR font glitches: If words were glued (e.g. "SOI" -> "So I", "AMI" -> "Am I", "IFI" -> "If I", "THISIS" -> "This is") or broken with hyphens (e.g. "SOME- THING" -> "Something"), translate the intended words naturally. Never output English fragments like "SOI" or "IS" in the Persian translation.$visionInstruction
            5. Return ONLY a strict JSON array where each object has "id" (integer) and "translation" (string).
            Input:
            $itemsJson
        """.trimIndent()

        // چرخش و تست مدل‌ها در زنجیره فال‌بک
        for ((idx, model) in candidateModels.withIndex()) {
            try {
                Log.d("BatchLlmTranslator", "تلاش برای ترجمه با مدل $model (مدل ${idx + 1} از ${candidateModels.size})...")
                val responseJson = try {
                    callModel(provider, apiKey, model, systemPrompt, imageBase64)
                } catch (e: Exception) {
                    if (imageBase64 != null) {
                        // اگر مدل توانایی خواندن تصویر نداشت، فوراً بدون تصویر تلاش می‌کند
                        callModel(provider, apiKey, model, systemPrompt, null)
                    } else {
                        throw e
                    }
                }
                if (!responseJson.isNullOrBlank()) {
                    val parsed = parseBatchResponse(responseJson)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed // موفقیت کامل!
                    }
                }
            } catch (e: ApiException) {
                Log.w("BatchLlmTranslator", "مدل $model با خطای کد ${e.code} مواجه شد: ${e.message}")
                // در صورت ریت‌لیمیت (429) یا اتمام سهمیه (403) یا خطای سرور (503/502/404)، سوئیچ به مدل بعدی!
                if (e.code == 429 || e.code == 403 || e.code == 503 || e.code == 502 || e.code == 404) {
                    continue
                } else {
                    // خطاهای غیرقابل بازگشت مثل کلید اشتباه (401)
                    break
                }
            } catch (e: Exception) {
                Log.w("BatchLlmTranslator", "خطای شبکه در مدل $model: ${e.message}")
                continue
            }
        }

        emptyMap()
    }

    /**
     * فراخوانی هوشمند سرور متناسب با ارائه‌دهنده با پشتیبانی اختیاری از تصویر Base64
     */
    private fun callModel(provider: String, apiKey: String, model: String, prompt: String, imageBase64: String? = null): String? {
        return when (provider) {
            AppPreferences.PROVIDER_GEMINI -> executeGeminiCall(apiKey, model, prompt, imageBase64)
            AppPreferences.PROVIDER_DEEPSEEK -> executeOpenAiCall(
                url = "https://api.deepseek.com/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                imageBase64 = imageBase64
            )
            AppPreferences.PROVIDER_OPENAI -> executeOpenAiCall(
                url = "https://api.openai.com/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                imageBase64 = imageBase64
            )
            AppPreferences.PROVIDER_OPENROUTER -> executeOpenAiCall(
                url = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                imageBase64 = imageBase64
            )
            AppPreferences.PROVIDER_GROK -> executeOpenAiCall(
                url = "https://api.x.ai/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                imageBase64 = imageBase64
            )
            AppPreferences.PROVIDER_GROQ -> executeOpenAiCall(
                url = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                imageBase64 = imageBase64
            )
            AppPreferences.PROVIDER_CUSTOM -> executeOpenAiCall(
                url = preferences.customEndpoint.ifBlank { "https://api.openai.com/v1/chat/completions" },
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                imageBase64 = imageBase64
            )
            else -> null
        }
    }

    /**
     * تست زنده اتصال یک مدل مشخص
     */
    suspend fun testSingleModel(
        provider: String,
        apiKey: String,
        model: String,
        customEndpoint: String = ""
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext ConnectionTestResult(
                isSuccess = false,
                modelName = model,
                message = "کلید API وارد نشده است."
            )
        }

        val testText = "Who are you?"
        val startTime = System.currentTimeMillis()

        try {
            val prompt = """
                Translate this comic dialogue into natural colloquial Persian. Return strict JSON array with "id" and "translation":
                [{"id": 0, "text": "$testText"}]
            """.trimIndent()

            val responseBody = when (provider) {
                AppPreferences.PROVIDER_GEMINI -> executeGeminiCall(trimmedKey, model, prompt)
                AppPreferences.PROVIDER_DEEPSEEK -> executeOpenAiCall(
                    url = "https://api.deepseek.com/v1/chat/completions",
                    apiKey = trimmedKey,
                    model = model,
                    prompt = prompt
                )
                AppPreferences.PROVIDER_OPENAI -> executeOpenAiCall(
                    url = "https://api.openai.com/v1/chat/completions",
                    apiKey = trimmedKey,
                    model = model,
                    prompt = prompt
                )
                AppPreferences.PROVIDER_OPENROUTER -> executeOpenAiCall(
                    url = "https://openrouter.ai/api/v1/chat/completions",
                    apiKey = trimmedKey,
                    model = model,
                    prompt = prompt
                )
                AppPreferences.PROVIDER_GROK -> executeOpenAiCall(
                    url = "https://api.x.ai/v1/chat/completions",
                    apiKey = trimmedKey,
                    model = model,
                    prompt = prompt
                )
                AppPreferences.PROVIDER_GROQ -> executeOpenAiCall(
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    apiKey = trimmedKey,
                    model = model,
                    prompt = prompt
                )
                AppPreferences.PROVIDER_CUSTOM -> executeOpenAiCall(
                    url = customEndpoint.ifBlank { "https://api.openai.com/v1/chat/completions" },
                    apiKey = trimmedKey,
                    model = model,
                    prompt = prompt
                )
                else -> throw IllegalStateException("ارائه‌دهنده نامعتبر است")
            }

            val latency = System.currentTimeMillis() - startTime
            val parsedMap = parseBatchResponse(responseBody)
            val sampleTrans = parsedMap[0] ?: "پاسخ دریافت شد"

            ConnectionTestResult(
                isSuccess = true,
                latencyMs = latency,
                modelName = model,
                sampleTranslation = sampleTrans,
                message = "مدل «$model» آماده و سالم است (${latency}ms)"
            )
        } catch (e: UnknownHostException) {
            ConnectionTestResult(
                isSuccess = false,
                modelName = model,
                message = "عدم امکان اتصال به سرور: اینترنت یا ابزار دور زدن تحریم را بررسی کنید."
            )
        } catch (e: SocketTimeoutException) {
            ConnectionTestResult(
                isSuccess = false,
                modelName = model,
                message = "مهلت زمانی سرور به پایان رسید (Timeout)."
            )
        } catch (e: ApiException) {
            val detailedMsg = when (e.code) {
                401 -> "خطای ۴۰۱: کلید API نامعتبر است یا منقضی شده."
                403 -> "خطای ۴۰۳: دسترسی مسدود یا نیاز به تغییر آی‌پی تحریم."
                404 -> "خطای ۴۰۴: مدل «$model» در این سرویس وجود ندارد."
                429 -> "خطای ۴۲۹ (ریت‌لیمیت): سهمیه این مدل پر است (به مدل‌های دیگر سوئیچ می‌شود)."
                else -> "خطای ${e.code}: ${e.serverMessage}"
            }
            ConnectionTestResult(
                isSuccess = false,
                modelName = model,
                message = detailedMsg
            )
        } catch (e: Exception) {
            ConnectionTestResult(
                isSuccess = false,
                modelName = model,
                message = "خطا: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    /**
     * تست همگانی و موازی سلامت مدل‌ها با همزمانی بهینه جهت جلوگیری از فریز و خطای سوکت
     */
    suspend fun batchTestModels(
        provider: String,
        apiKey: String,
        models: List<String>,
        customEndpoint: String = "",
        onProgress: (testedCount: Int, total: Int, latestResult: ConnectionTestResult) -> Unit = { _, _, _ -> }
    ): List<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext models.map {
                ConnectionTestResult(isSuccess = false, modelName = it, message = "کلید API وارد نشده است.")
            }
        }
        val results = mutableListOf<ConnectionTestResult>()
        val semaphore = Semaphore(3) // حداکثر ۳ درخواست همزمان برای حفظ پایداری اینترنت موبایل

        val deferreds = models.map { model ->
            async {
                semaphore.withPermit {
                    val res = testSingleModel(provider, trimmedKey, model, customEndpoint)
                    synchronized(results) {
                        results.add(res)
                        onProgress(results.size, models.size, res)
                    }
                    res
                }
            }
        }
        deferreds.map { it.await() }
    }

    /**
     * تست گروهی تمام مدل‌های انتخاب شده در زنجیره رفع ریت‌لیمیت
     */
    suspend fun testAllSelectedModels(
        provider: String,
        apiKey: String,
        models: List<String>,
        customEndpoint: String = ""
    ): List<ConnectionTestResult> = withContext(Dispatchers.IO) {
        val listToTest = if (models.isNotEmpty()) models else listOf(getDefaultModelForProvider(provider))
        batchTestModels(provider, apiKey, listToTest, customEndpoint)
    }

    /**
     * دریافت لیست کامل مدل‌های موجود از سرور ارائه‌دهنده با اولویت‌بندی مدل‌های رایگان (:free)
     */
    suspend fun fetchAvailableModels(
        provider: String,
        apiKey: String,
        customEndpoint: String = ""
    ): List<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (provider != AppPreferences.PROVIDER_OPENROUTER && provider != AppPreferences.PROVIDER_CUSTOM && trimmedKey.isEmpty()) {
            return@withContext getPresetModels(provider)
        }

        try {
            when (provider) {
                AppPreferences.PROVIDER_GEMINI -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey"
                    val request = Request.Builder().url(url).get().build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext getPresetModels(provider)
                        val body = response.body?.string() ?: return@withContext getPresetModels(provider)
                        val root = JsonParser.parseString(body).asJsonObject
                        val modelsArr = root.getAsJsonArray("models") ?: return@withContext getPresetModels(provider)

                        val list = mutableListOf<String>()
                        for (i in 0 until modelsArr.size()) {
                            val m = modelsArr[i].asJsonObject
                            val name = m.get("name").asString.removePrefix("models/")
                            val methods = m.getAsJsonArray("supportedGenerationMethods")
                            val supportsGenerate = methods != null && methods.any { it.asString == "generateContent" }
                            if (supportsGenerate && name.contains("gemini", ignoreCase = true)) {
                                list.add(name)
                            }
                        }
                        if (list.isNotEmpty()) {
                            list.sortedWith(compareByDescending<String> {
                                when {
                                    it.contains("2.0-flash", ignoreCase = true) -> 5
                                    it.contains("1.5-flash", ignoreCase = true) -> 4
                                    it.contains("flash-lite", ignoreCase = true) -> 3
                                    it.contains("1.5-pro", ignoreCase = true) -> 2
                                    it.contains("2.0-pro", ignoreCase = true) -> 1
                                    else -> 0
                                }
                            }.thenBy { it })
                        } else getPresetModels(provider)
                    }
                }
                AppPreferences.PROVIDER_DEEPSEEK -> {
                    val url = "https://api.deepseek.com/v1/models"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $trimmedKey")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext getPresetModels(provider)
                        val body = response.body?.string() ?: return@withContext getPresetModels(provider)
                        val root = JsonParser.parseString(body).asJsonObject
                        val dataArr = root.getAsJsonArray("data") ?: return@withContext getPresetModels(provider)
                        val list = mutableListOf<String>()
                        for (i in 0 until dataArr.size()) {
                            list.add(dataArr[i].asJsonObject.get("id").asString)
                        }
                        if (list.isNotEmpty()) list else getPresetModels(provider)
                    }
                }
                AppPreferences.PROVIDER_OPENROUTER -> {
                    val url = "https://openrouter.ai/api/v1/models"
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .addHeader("Accept", "application/json")
                        .addHeader("HTTP-Referer", "https://openrouter.ai")
                        .addHeader("X-Title", "ScreenTranslator")

                    if (trimmedKey.startsWith("sk-or-")) {
                        reqBuilder.addHeader("Authorization", "Bearer $trimmedKey")
                    }

                    var response = client.newCall(reqBuilder.build()).execute()
                    if (!response.isSuccessful && response.code == 401) {
                        // در صورت رد شدن کلید، بدون Authorization دوباره فراخوانی کن چون اندپوینت رایگان و عمومی است
                        response.close()
                        val fallbackReq = Request.Builder()
                            .url(url)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .addHeader("Accept", "application/json")
                            .addHeader("HTTP-Referer", "https://openrouter.ai")
                            .addHeader("X-Title", "ScreenTranslator")
                            .build()
                        response = client.newCall(fallbackReq).execute()
                    }

                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            Log.w("BatchLlmTranslator", "OpenRouter HTTP ${resp.code}")
                            return@withContext getPresetModels(provider)
                        }
                        val body = resp.body?.string() ?: return@withContext getPresetModels(provider)
                        val root = JsonParser.parseString(body).asJsonObject
                        val dataArr = root.getAsJsonArray("data") ?: return@withContext getPresetModels(provider)

                        val freeList = mutableListOf<String>()
                        val paidList = mutableListOf<String>()

                        for (i in 0 until dataArr.size()) {
                            try {
                                val item = dataArr[i].asJsonObject
                                val id = item.get("id")?.asString ?: continue
                                var isFree = id.contains(":free", ignoreCase = true) || id.endsWith(":free") || id == "openrouter/free"
                                if (!isFree && item.has("pricing")) {
                                    val pricing = item.get("pricing")
                                    if (pricing != null && pricing.isJsonObject) {
                                        val pObj = pricing.asJsonObject
                                        val promptStr = if (pObj.has("prompt") && !pObj.get("prompt").isJsonNull) pObj.get("prompt").asString else ""
                                        val compStr = if (pObj.has("completion") && !pObj.get("completion").isJsonNull) pObj.get("completion").asString else ""
                                        val promptVal = promptStr.toDoubleOrNull() ?: -1.0
                                        val compVal = compStr.toDoubleOrNull() ?: -1.0
                                        if (promptVal == 0.0 && compVal == 0.0) {
                                            isFree = true
                                        }
                                    }
                                }
                                if (isFree) {
                                    freeList.add(id)
                                } else {
                                    paidList.add(id)
                                }
                            } catch (e: Exception) {
                                // رد کردن خطای هر مدل تکی بدون توقف پردازش سایر مدل‌ها
                            }
                        }

                        val freePopularityRank = listOf(
                            "nvidia/nemotron-3-ultra-550b-a55b:free",
                            "poolside/laguna-s-2.1:free",
                            "dots-studio/dots-3-note-preview:free",
                            "qwen/qwen3.8-27b:free",
                            "nvidia/nemotron-3.5-lightning:free",
                            "google/gemma-4-31b-it:free",
                            "google/gemma-4-26b-a4b-it:free",
                            "nvidia/nemotron-3-super-120b-a12b:free",
                            "inclusionai/ling-3.0-flash-sante:free",
                            "liquid/lfm-2.5-2.6b:free",
                            "thinkingmachines/inkling-small:free",
                            "thinkingmachines/inkling:free",
                            "poolside/laguna-xs-2.1:free",
                            "cohere/north-mini-code:free",
                            "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
                            "nvidia/nemotron-3.5-content-safety:free",
                            "stealth/space-bunny-alpha",
                            "openrouter/free"
                        )
                        freeList.sortWith(compareBy<String> { id ->
                            val rank = freePopularityRank.indexOf(id)
                            if (rank != -1) rank else 100
                        }.thenBy { it })

                        paidList.sortWith(compareByDescending<String> {
                            when {
                                it.contains("claude-3.5", ignoreCase = true) -> 10
                                it.contains("claude", ignoreCase = true) -> 9
                                it.contains("gpt-4o", ignoreCase = true) -> 8
                                it.contains("o3", ignoreCase = true) -> 7
                                it.contains("deepseek-chat", ignoreCase = true) -> 6
                                it.contains("deepseek", ignoreCase = true) -> 5
                                it.contains("gemini", ignoreCase = true) -> 4
                                it.contains("qwen", ignoreCase = true) -> 3
                                it.contains("llama", ignoreCase = true) -> 2
                                else -> 0
                            }
                        }.thenBy { it })

                        val combined = (freeList + paidList).distinct()
                        if (combined.isNotEmpty()) combined else getPresetModels(provider)
                    }
                }
                AppPreferences.PROVIDER_OPENAI -> {
                    val url = "https://api.openai.com/v1/models"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $trimmedKey")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext getPresetModels(provider)
                        val body = response.body?.string() ?: return@withContext getPresetModels(provider)
                        val root = JsonParser.parseString(body).asJsonObject
                        val dataArr = root.getAsJsonArray("data") ?: return@withContext getPresetModels(provider)

                        val list = mutableListOf<String>()
                        for (i in 0 until dataArr.size()) {
                            val id = dataArr[i].asJsonObject.get("id").asString
                            if (id.startsWith("gpt-") || id.startsWith("chatgpt-") || id.startsWith("o1") || id.startsWith("o3")) {
                                list.add(id)
                            }
                        }
                        if (list.isNotEmpty()) list.sortedWith(compareByDescending<String> {
                            when {
                                it.contains("4o-mini", ignoreCase = true) -> 5
                                it.contains("4o", ignoreCase = true) -> 4
                                it.contains("o3-mini", ignoreCase = true) -> 3
                                it.contains("o1", ignoreCase = true) -> 2
                                else -> 0
                            }
                        }.thenBy { it }) else getPresetModels(provider)
                    }
                }
                AppPreferences.PROVIDER_GROK -> {
                    val url = "https://api.x.ai/v1/models"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $trimmedKey")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext getPresetModels(provider)
                        val body = response.body?.string() ?: return@withContext getPresetModels(provider)
                        val root = JsonParser.parseString(body).asJsonObject
                        val dataArr = root.getAsJsonArray("data") ?: return@withContext getPresetModels(provider)

                        val list = mutableListOf<String>()
                        for (i in 0 until dataArr.size()) {
                            val id = dataArr[i].asJsonObject.get("id").asString
                            if (id.contains("grok", ignoreCase = true)) {
                                list.add(id)
                            }
                        }
                        if (list.isNotEmpty()) list.sortedWith(compareByDescending<String> {
                            when {
                                it.contains("grok-2", ignoreCase = true) -> 3
                                it.contains("grok-beta", ignoreCase = true) -> 2
                                else -> 0
                            }
                        }.thenBy { it }) else getPresetModels(provider)
                    }
                }
                AppPreferences.PROVIDER_GROQ -> {
                    val url = "https://api.groq.com/openai/v1/models"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $trimmedKey")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext getPresetModels(provider)
                        val body = response.body?.string() ?: return@withContext getPresetModels(provider)
                        val root = JsonParser.parseString(body).asJsonObject
                        val dataArr = root.getAsJsonArray("data") ?: return@withContext getPresetModels(provider)

                        val list = mutableListOf<String>()
                        for (i in 0 until dataArr.size()) {
                            val id = dataArr[i].asJsonObject.get("id").asString
                            // حذف مدل‌های ویسپر یا صوتی و نگه‌داشتن مدل‌های متنی LLM
                            if (!id.contains("whisper", ignoreCase = true) &&
                                !id.contains("tts", ignoreCase = true) &&
                                !id.contains("audio", ignoreCase = true)) {
                                list.add(id)
                            }
                        }
                        if (list.isNotEmpty()) list.sortedWith(compareByDescending<String> {
                            when {
                                it.contains("llama-3.3-70b", ignoreCase = true) -> 6
                                it.contains("llama-3.1-8b", ignoreCase = true) -> 5
                                it.contains("deepseek-r1", ignoreCase = true) -> 4
                                it.contains("llama3-70b", ignoreCase = true) -> 3
                                it.contains("gemma2", ignoreCase = true) -> 2
                                it.contains("mixtral", ignoreCase = true) -> 1
                                else -> 0
                            }
                        }.thenBy { it }) else getPresetModels(provider)
                    }
                }
                AppPreferences.PROVIDER_CUSTOM -> {
                    val baseUrl = if (customEndpoint.contains("/chat/completions")) {
                        customEndpoint.substringBefore("/chat/completions") + "/models"
                    } else {
                        "https://openrouter.ai/api/v1/models"
                    }
                    val request = Request.Builder()
                        .url(baseUrl)
                        .addHeader("Authorization", "Bearer $trimmedKey")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext getPresetModels(provider)
                        val body = response.body?.string() ?: return@withContext getPresetModels(provider)
                        val root = JsonParser.parseString(body).asJsonObject
                        val dataArr = root.getAsJsonArray("data") ?: return@withContext getPresetModels(provider)

                        val list = mutableListOf<String>()
                        for (i in 0 until dataArr.size()) {
                            list.add(dataArr[i].asJsonObject.get("id").asString)
                        }
                        if (list.isNotEmpty()) list else getPresetModels(provider)
                    }
                }
                else -> getPresetModels(provider)
            }
        } catch (e: Exception) {
            getPresetModels(provider)
        }
    }

    private fun executeGeminiCall(apiKey: String, model: String, prompt: String, imageBase64: String? = null): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val rootObj = JsonObject().apply {
            val contentsArr = JsonArray().apply {
                val contentObj = JsonObject().apply {
                    addProperty("role", "user")
                    val partsArr = JsonArray().apply {
                        val partObj = JsonObject().apply {
                            addProperty("text", prompt)
                        }
                        add(partObj)

                        if (!imageBase64.isNullOrBlank()) {
                            val imgPart = JsonObject().apply {
                                val inlineData = JsonObject().apply {
                                    addProperty("mime_type", "image/jpeg")
                                    addProperty("data", imageBase64)
                                }
                                add("inline_data", inlineData)
                            }
                            add(imgPart)
                        }
                    }
                    add("parts", partsArr)
                }
                add(contentObj)
            }
            add("contents", contentsArr)

            val configObj = JsonObject().apply {
                addProperty("temperature", 0.25)
                addProperty("responseMimeType", "application/json")
            }
            add("generationConfig", configObj)
        }

        val request = Request.Builder()
            .url(url)
            .post(rootObj.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(response.code, extractErrorMessage(body))
            }
            val root = JsonParser.parseString(body).asJsonObject
            val candidates = root.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val firstCandidate = candidates[0].asJsonObject
                val content = firstCandidate.getAsJsonObject("content")
                val parts = content.getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    return parts[0].asJsonObject.get("text").asString
                }
            }
            throw Exception("ساختار پاسخ Gemini خالی است")
        }
    }

    private fun executeOpenAiCall(url: String, apiKey: String, model: String, prompt: String, imageBase64: String? = null): String {
        val rootObj = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0.25)
            val messagesArr = JsonArray().apply {
                val sysMsg = JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", "You are a professional manhwa scanlator. Translate each dialogue into natural colloquial Persian. Output strict JSON array.")
                }
                val userMsg = JsonObject().apply {
                    addProperty("role", "user")
                    if (imageBase64.isNullOrBlank()) {
                        addProperty("content", prompt)
                    } else {
                        val contentArr = JsonArray().apply {
                            val textObj = JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("text", prompt)
                            }
                            add(textObj)
                            val imgObj = JsonObject().apply {
                                addProperty("type", "image_url")
                                val urlObj = JsonObject().apply {
                                    addProperty("url", "data:image/jpeg;base64,$imageBase64")
                                }
                                add("image_url", urlObj)
                            }
                            add(imgObj)
                        }
                        add("content", contentArr)
                    }
                }
                add(sysMsg)
                add(userMsg)
            }
            add("messages", messagesArr)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://github.com/screenreader/translator")
            .addHeader("X-Title", "ScreenTranslator")
            .post(rootObj.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(response.code, extractErrorMessage(body))
            }
            val root = JsonParser.parseString(body).asJsonObject
            val choices = root.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val firstChoice = choices[0].asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                return message.get("content")?.asString ?: ""
            }
            throw Exception("پاسخ دریافتی از سرور حاوی متن نبود")
        }
    }

    private fun extractErrorMessage(body: String): String {
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (root.has("error")) {
                val err = root.get("error")
                if (err.isJsonObject && err.asJsonObject.has("message")) {
                    err.asJsonObject.get("message").asString
                } else {
                    err.toString()
                }
            } else {
                body
            }
        } catch (e: Exception) {
            body
        }
    }

    private fun parseBatchResponse(rawText: String): Map<Int, String> {
        val resultMap = mutableMapOf<Int, String>()
        try {
            var cleanText = rawText.trim()
            if (cleanText.startsWith("```json")) {
                cleanText = cleanText.removePrefix("```json")
            } else if (cleanText.startsWith("```")) {
                cleanText = cleanText.removePrefix("```")
            }
            if (cleanText.endsWith("```")) {
                cleanText = cleanText.removeSuffix("```")
            }
            cleanText = cleanText.trim()

            val jsonElement = JsonParser.parseString(cleanText)
            val jsonArray = if (jsonElement.isJsonArray) {
                jsonElement.asJsonArray
            } else if (jsonElement.isJsonObject && jsonElement.asJsonObject.has("translations")) {
                jsonElement.asJsonObject.getAsJsonArray("translations")
            } else {
                return resultMap
            }

            for (i in 0 until jsonArray.size()) {
                val item = jsonArray[i].asJsonObject
                val id = if (item.has("id")) item.get("id").asInt else i
                val trans = when {
                    item.has("translation") -> item.get("translation").asString
                    item.has("persian") -> item.get("persian").asString
                    item.has("text") -> item.get("text").asString
                    else -> ""
                }
                if (trans.isNotBlank()) {
                    resultMap[id] = trans.trim()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultMap
    }

    class ApiException(val code: Int, val serverMessage: String) : Exception("HTTP $code: $serverMessage")
}
