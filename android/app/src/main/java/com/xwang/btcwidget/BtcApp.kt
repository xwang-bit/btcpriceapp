package com.xwang.btcwidget

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BtcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 使用 OneTimeWorkRequest 启动首个任务，后续任务将由 Worker 自我调度实现 1-10min 频率
        val immediateRequest = OneTimeWorkRequestBuilder<PriceWorker>()
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "price_refresh",
            ExistingWorkPolicy.KEEP, // 如果已有任务在运行，则保持现状
            immediateRequest
        )
    }
}
