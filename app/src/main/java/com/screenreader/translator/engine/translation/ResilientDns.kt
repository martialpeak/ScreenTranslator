package com.screenreader.translator.engine.translation

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * سیستم DNS انعطاف‌پذیر با قابلیت دور زدن اختلالات و فیلترینگ DNS شبکه‌های موبایل و فیلترشکن‌ها
 * در صورت بروز خطای UnknownHostException، به شکل هوشمند از آی‌پی‌های Anycast و پروتکل DoH بهره می‌برد.
 */
class ResilientDns : Dns {

    private val staticIps = mapOf(
        "openrouter.ai" to listOf("104.18.2.115", "104.18.3.115"),
        "api.groq.com" to listOf("104.18.4.195", "104.18.5.195"),
        "api.deepseek.com" to listOf("104.18.38.169", "172.64.155.196"),
        "translate.googleapis.com" to listOf("142.250.180.202", "172.217.18.10")
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val lowerHost = hostname.lowercase().trim()

        // ۱. تلاش اولیه از طریق سیستم عامل
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) {
                return addresses
            }
        } catch (e: Exception) {
            // خطای DNS سیستم (مثلاً فیلترینگ یا نشت DNS فیلترشکن)
        }

        // ۲. استفاده فوری از IPهای Anycast کلودفلر برای ارائه‌دهندگان هوش مصنوعی
        val fallback = staticIps[lowerHost]
        if (fallback != null) {
            try {
                return fallback.map { InetAddress.getByName(it) }
            } catch (e: Exception) {
                // ادامه به DoH
            }
        }

        // ۳. تلاش ثانویه از طریق پروتکل امن DoH (DNS-over-HTTPS) کلودفلر
        try {
            val dohUrl = "https://1.1.1.1/dns-query?name=$lowerHost&type=A"
            val conn = java.net.URL(dohUrl).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Accept", "application/dns-json")
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = com.google.gson.JsonParser.parseString(json).asJsonObject
                if (parsed.has("Answer")) {
                    val answers = parsed.getAsJsonArray("Answer")
                    val list = mutableListOf<InetAddress>()
                    for (i in 0 until answers.size()) {
                        val item = answers[i].asJsonObject
                        if (item.get("type").asInt == 1) { // Type A record
                            list.add(InetAddress.getByName(item.get("data").asString))
                        }
                    }
                    if (list.isNotEmpty()) return list
                }
            }
        } catch (e: Exception) {
            // DoH ناموفق بود
        }

        throw UnknownHostException("ResilientDns: Unable to resolve hostname $hostname")
    }
}
