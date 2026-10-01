package com.screenreader.translator.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.screenreader.translator.data.local.dao.HistoryDao
import com.screenreader.translator.data.local.dao.TranslationCacheDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * سرویس نگهداری دیتابیس: پاکسازی خودکار کش‌ها و تاریخچه منقضی
 * جلوگیری از رشد بی‌کران دیتابیس و بهبود عملکرد
 */
class DatabaseMaintenanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = AppDatabase.getInstance(context)
    private val cacheDao = database.translationCacheDao()
    private val historyDao = database.historyDao()
    private val prefs = AppPreferences.getInstance(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            cleanExpiredCache()
            trimHistorySize()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun cleanExpiredCache() {
        val ttlDays = prefs.translationCacheTTLDays
        val cutoffTime = System.currentTimeMillis() - (ttlDays * 24 * 60 * 60 * 1000L)
        cacheDao.deleteOlderThan(cutoffTime)
    }

    private suspend fun trimHistorySize() {
        val maxEntries = prefs.maxHistoryEntries
        val currentCount = historyDao.getCount()

        if (currentCount > maxEntries) {
            val toDelete = currentCount - maxEntries
            historyDao.deleteOldest(toDelete)
        }
    }

    companion object {
        fun scheduleMaintenance(context: Context) {
            val maintenanceWork = PeriodicWorkRequestBuilder<DatabaseMaintenanceWorker>(
                1, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "db_maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                maintenanceWork
            )
        }
    }
}