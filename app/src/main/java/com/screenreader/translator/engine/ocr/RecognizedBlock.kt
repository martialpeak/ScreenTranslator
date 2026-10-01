package com.screenreader.translator.engine.ocr

import android.graphics.Rect

/**
 * نمایشگر یک بلوک متنی خوانده شده از روی صفحه گوشی به همراه مختصات پیکسلی دقیق
 */
data class RecognizedBlock(
    val id: String,
    val originalText: String,
    val boundingBox: Rect,
    val lines: List<RecognizedLine> = emptyList(),
    var translatedText: String? = null
)

data class RecognizedLine(
    val text: String,
    val boundingBox: Rect
)
