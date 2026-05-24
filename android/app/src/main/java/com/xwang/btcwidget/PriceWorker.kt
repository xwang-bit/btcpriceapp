package com.xwang.btcwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import androidx.work.*
import java.util.concurrent.TimeUnit

class PriceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val symbol = PriceRepository.getSelectedSymbol(applicationContext)
            val (price, change24h) = PriceRepository.fetchPriceData(symbol)
            
            // 检查提醒
            checkPriceAlert(symbol, price)
            
            PriceRepository.savePrice(applicationContext, price, change24h)
            PriceWidget().updateAll(applicationContext)

            // 实现自我调度，以支持 1-10 分钟的频率
            scheduleNextWork()

            return Result.success()
        } catch (e: Exception) {
            // 出错也调度下一次，避免任务链中断
            scheduleNextWork()
            return Result.retry()
        }
    }

    private fun scheduleNextWork() {
        val interval = PriceRepository.getRefreshInterval(applicationContext)
        val nextWork = OneTimeWorkRequestBuilder<PriceWorker>()
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .build()
        
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "price_refresh",
            ExistingWorkPolicy.REPLACE,
            nextWork
        )
    }

    private fun checkPriceAlert(symbol: String, currentPrice: Double) {
        val threshold = PriceRepository.getAlertThreshold(applicationContext, symbol) ?: return
        val lastPrice = PriceRepository.getCurrentPrice(applicationContext) ?: 0.0

        if (lastPrice == 0.0) return // 第一次运行不提醒

        // 更加鲁棒的触发逻辑：只要当前价格和上次价格分别在阈值两侧，或者当前价格达到阈值
        val triggered = (lastPrice < threshold && currentPrice >= threshold) ||
                        (lastPrice > threshold && currentPrice <= threshold)

        if (triggered) {
            sendNotification(
                "Price Alert: $symbol",
                "$symbol has reached your target of ${PriceRepository.formatPrice(threshold)}. Current: ${PriceRepository.formatPrice(currentPrice)}"
            )
        }
    }

    private fun sendNotification(title: String, message: String) {
        val soundEnabled = PriceRepository.getNotificationSound(applicationContext)
        val channelId = if (soundEnabled) "price_alerts_sound" else "price_alerts_silent"
        val channelName = if (soundEnabled) "Price Alerts (Sound)" else "Price Alerts (Silent)"
        
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, 
            channelName, 
            if (soundEnabled) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW
        )
        
        if (soundEnabled) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            channel.setSound(alarmSound, null)
            channel.enableVibration(true)
        } else {
            channel.setSound(null, null)
            channel.enableVibration(false)
        }
        
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (soundEnabled) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        if (soundEnabled) {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
