package com.screenreader.translator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.screenreader.translator.data.local.AppDatabase
import com.screenreader.translator.data.repository.TranslationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenTranslatorApp : Application() {

    companion object {
        const val CHANNEL_BUBBLE_ID = "screen_translator_bubble_channel"
        const val CHANNEL_CAPTURE_ID = "screen_translator_capture_channel"

        lateinit var instance: ScreenTranslatorApp
            private set

        lateinit var database: AppDatabase
            private set

        lateinit var repository: TranslationRepository
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // راه‌اندازی دیتابیس پایدار و مخزن ترجمه
        database = AppDatabase.getInstance(this)
        repository = TranslationRepository(database)

        // پاکسازی رکوردهای اشتباه خودآموز قدیمی در پس‌زمینه بدون دستکاری کش ترجمه‌های معتبر
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.purgeCorruptedAutoLearned()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ساخت کانال‌های اعلان برای سرویس‌های پیش‌زمینه
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bubbleChannel = NotificationChannel(
                CHANNEL_BUBBLE_ID,
                "سرویس حباب مترجم شناور",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت سرویس شناور ترجمه صفحه"
            }

            val captureChannel = NotificationChannel(
                CHANNEL_CAPTURE_ID,
                "سرویس ضبط و اسکن صفحه",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "پردازش اسکرین‌شات و OCR صفحه"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(bubbleChannel)
            manager.createNotificationChannel(captureChannel)
        }
    }
}
