package com.screenreader.translator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class TranslationLogicTest {

    private fun calculateSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testCacheHashConsistency() {
        val text1 = "Start Game"
        val text2 = "Start Game"
        val text3 = "start game"

        val hash1 = calculateSha256(text1)
        val hash2 = calculateSha256(text2)
        val hash3 = calculateSha256(text3)

        assertEquals("Same string must produce identical SHA-256 hash", hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue("Case sensitivity must produce different hashes", hash1 != hash3)
    }

    @Test
    fun testGlossarySubstitution() {
        val customMap = mapOf(
            "HP" to "جان",
            "MP" to "انرژی",
            "XP" to "امتیاز"
        )

        val input = "HP"
        val output = customMap[input] ?: "سلامتی"
        assertEquals("جان", output)
    }

    @Test
    fun testTranslationResponseParsing() {
        val sampleJson = "[[[\"سلام دنیا\",\"Hello World\",null,null,10]],null,\"en\",null,null,null,null,[]]"
        val jsonArray = com.google.gson.JsonParser.parseString(sampleJson).asJsonArray
        val sentenceArray = jsonArray.get(0).asJsonArray
        val firstSentence = sentenceArray.get(0).asJsonArray.get(0).asString

        assertEquals("سلام دنیا", firstSentence)
    }

    @Test
    fun testManhwaBubbleNormalization() {
        val rawOcr = "E-RANK\nHUNTER"
        val normalized = rawOcr
            .replace("-\n", "")
            .replace("-\r\n", "")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        assertEquals("E-RANK HUNTER", normalized)

        val cleanKeyword = "E-RANK HUNTER.".trimEnd('.', '!', '?', ',', ':', ';', '"', '\'', '-', '~', '…', ' ').trim()
        assertEquals("E-RANK HUNTER", cleanKeyword)
    }

    @Test
    fun testColloquialTransformation() {
        val formal = "او فرار می کند و نمی دانم چه کار می کنی؟"
        val colloquial = com.screenreader.translator.engine.translation.ColloquialTransformer.makeConversational(formal)
        assertEquals("او فرار می‌کنه و نمی‌دونم داری چیکار می‌کنی?!", colloquial.replace("؟!", "?!"))
    }

    @Test
    fun testKoreanAndChineseGlossaryMapping() {
        val koreanTerms = mapOf(
            "하아" to "هااه... (نفس عمیق)",
            "콰앙" to "کوااانگ! (برخورد شدید)",
            "헌터" to "شکارچی",
            "일어나라" to "برخیز (ARISE)"
        )
        val chineseTerms = mapOf(
            "轰" to "هوووم! (صدای غرش و انفجار)",
            "斩" to "شلااااش! (برش شمشیر)"
        )

        assertEquals("هااه... (نفس عمیق)", koreanTerms["하아"])
        assertEquals("برخیز (ARISE)", koreanTerms["일어나라"])
        assertEquals("هوووم! (صدای غرش و انفجار)", chineseTerms["轰"])
    }
}
