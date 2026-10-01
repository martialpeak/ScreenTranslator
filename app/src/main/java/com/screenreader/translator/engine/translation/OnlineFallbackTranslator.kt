package com.screenreader.translator.engine.translation

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class OnlineFallbackTranslator {

    private val client = OkHttpClient.Builder()
        .dns(ResilientDns())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * ترجمه آنلاین از طریق سرویس ابری به زبان فارسی با ساختار فال‌بک
     */
    suspend fun translate(text: String, sourceLang: String = "auto", targetLang: String = "fa"): String = withContext(Dispatchers.IO) {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encodedText"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP translation error: ${response.code}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response from translation server")
            parseTranslationResponse(body)
        }
    }

    private fun parseTranslationResponse(jsonString: String): String {
        val jsonArray = JsonParser.parseString(jsonString).asJsonArray
        val sentencesArray = jsonArray.get(0).asJsonArray
        val sb = StringBuilder()
        for (i in 0 until sentencesArray.size()) {
            val sentence = sentencesArray.get(i).asJsonArray
            sb.append(sentence.get(0).asString)
        }
        return sb.toString().trim()
    }
}
