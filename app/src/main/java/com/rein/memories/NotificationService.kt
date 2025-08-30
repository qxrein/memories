package com.rein.memories

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class NotificationService {
    companion object {
        const val CHANNEL_ID = "memories_channel"
        const val NOTIFICATION_ID = 1
        const val WORK_TAG = "memory_notification"
        
        fun createNotificationChannel(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val name = "Random Memories"
                    val descriptionText = "Notifications for random memory reminders"
                    val importance = NotificationManager.IMPORTANCE_DEFAULT
                    val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                        description = descriptionText
                        enableVibration(true)
                        setShowBadge(true)
                    }
                    val notificationManager: NotificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.createNotificationChannel(channel)
                    android.util.Log.d("NotificationService", "Notification channel created successfully")
                } else {
                    android.util.Log.d("NotificationService", "Notification channel not needed for this API level")
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationService", "Failed to create notification channel", e)
            }
        }
        
        fun scheduleNotifications(
            context: Context,
            startTime: LocalTime,
            endTime: LocalTime,
            frequency: String
        ) {
            try {
                // Cancel existing notifications
                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
                
                // Calculate delay based on frequency
                val delayInMinutes = when (frequency) {
                    "Daily" -> 24 * 60L
                    "Weekly" -> 7 * 24 * 60L
                    "Monthly" -> 30 * 24 * 60L
                    else -> 24 * 60L
                }
                
                // Calculate initial delay
                val initialDelay = calculateInitialDelay(startTime, endTime)
                
                android.util.Log.d("NotificationService", "Scheduling notifications with initial delay: $initialDelay minutes, frequency: $frequency")
                
                // Create a one-time work request for the first notification
                val initialWork = OneTimeWorkRequestBuilder<NotificationWorker>()
                    .setInitialDelay(initialDelay, TimeUnit.MINUTES)
                    .addTag(WORK_TAG)
                    .build()
                
                // Enqueue the initial work
                WorkManager.getInstance(context).enqueue(initialWork)
                android.util.Log.d("NotificationService", "Initial work enqueued successfully")
                
                // For periodic work, we need to use a different approach since WorkManager has limitations
                // We'll schedule the next notification when the current one is shown
                android.util.Log.d("NotificationService", "Notifications scheduled successfully")
            } catch (e: Exception) {
                android.util.Log.e("NotificationService", "Failed to schedule notifications", e)
                throw e
            }
        }
        
        private fun calculateInitialDelay(startTime: LocalTime, endTime: LocalTime): Long {
            val now = LocalTime.now()
            val timeSlotMinutes = ChronoUnit.MINUTES.between(startTime, endTime)
            
            // If current time is within the time slot, schedule for a random time within the slot
            if (now.isAfter(startTime) && now.isBefore(endTime)) {
                val remainingMinutes = ChronoUnit.MINUTES.between(now, endTime)
                return Random.nextLong(1, remainingMinutes + 1)
            }
            
            // If current time is before the time slot, schedule for a random time within the slot
            if (now.isBefore(startTime)) {
                val minutesUntilStart = ChronoUnit.MINUTES.between(now, startTime)
                return minutesUntilStart + Random.nextLong(0, timeSlotMinutes + 1)
            }
            
            // If current time is after the time slot, schedule for tomorrow
            val minutesUntilTomorrow = ChronoUnit.MINUTES.between(now, LocalTime.MAX) + 1
            val minutesFromMidnightToStart = ChronoUnit.MINUTES.between(LocalTime.MIN, startTime)
            return minutesUntilTomorrow + minutesFromMidnightToStart + Random.nextLong(0, timeSlotMinutes + 1)
        }
        
        fun cancelNotifications(context: Context) {
            try {
                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
                android.util.Log.d("NotificationService", "All notifications cancelled")
            } catch (e: Exception) {
                android.util.Log.e("NotificationService", "Failed to cancel notifications", e)
            }
        }
    }
}

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    override fun doWork(): Result {
        android.util.Log.d("NotificationWorker", "Worker triggered - showing notification")
        showNotification()
        
        // Schedule the next notification
        scheduleNextNotification()
        
        return Result.success()
    }
    
    private fun showNotification() {
        val intent = Intent(applicationContext, MemoriesMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(applicationContext, NotificationService.CHANNEL_ID)
            .setContentTitle("Random Memory")
            .setContentText("Time to revisit a special moment from your past!")
            .setSmallIcon(R.drawable.gear)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationService.NOTIFICATION_ID, notification)
        
        android.util.Log.d("NotificationWorker", "Notification shown successfully")
    }
    
    private fun scheduleNextNotification() {
        try {
            // Schedule the next notification for tomorrow (daily frequency)
            val nextWork = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(24 * 60, TimeUnit.MINUTES) // 24 hours
                .addTag(NotificationService.WORK_TAG)
                .build()
            
            WorkManager.getInstance(applicationContext).enqueue(nextWork)
            android.util.Log.d("NotificationWorker", "Next notification scheduled for tomorrow")
        } catch (e: Exception) {
            android.util.Log.e("NotificationWorker", "Failed to schedule next notification", e)
        }
    }
} 