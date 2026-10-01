package com.screenreader.translator.ui.util

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.screenreader.translator.engine.translation.BatchLlmTranslator

/**
 * مدیریت بهتر خطاها و نمایش پیام‌های کاربر‌پسند
 * جایگزینی خاموشی خطاها با بازخورد واضح
 */
class UserFeedbackHandler(private val context: Context) {

    fun showLlmError(exception: BatchLlmTranslator.ApiException) {
        val message = when (exception.code) {
            401 -> "خطای احراز هویت: کلید API نامعتبر است"
            403 -> "دسترسی مسدود: شاید کیوتا پر شده باشد"
            429 -> "سرور شلوغ است، لطفا دقایقی بعد دوباره تلاش کنید"
            503, 502 -> "سرویس موقتاً دردسترس نیست"
            504 -> "مهلت زمانی: اتصال بسیار کند"
            else -> "خطای سرور (‎‎‎‎‎‎‎‎‎‎${exception.code}‎‎‎‎‎‎‎‎‎‎): ‎‎‎‎‎‎‎‎‎‎${exception.serverMessage}‎‎‎‎‎‎‎‎‎‎"
        }
        showErrorDialog("خطای ترجمه", message)
    }

    fun showOcrError(exception: Exception) {
        val message = when {
            exception.message?.contains("OutOfMemory") == true ->
                "حافظه دستگاه کافی نیست برای پردازش تصویر بزرگ"
            exception.message?.contains("Timeout") == true ->
                "تصویر بسیار پیچیده است، لطفا تصویری ساده‌تر انتخاب کنید"
            else -> "خطا در تشخیص متن: ‎‎‎‎‎‎‎‎‎‎${exception.localizedMessage}‎‎‎‎‎‎‎‎‎‎"
        }
        showErrorDialog("خطای OCR", message)
    }

    fun showNetworkError() {
        showErrorDialog(
            "خطای شبکه",
            "اینترنت قطع است یا VPN مورد نیاز است"
        )
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("باشه") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
}