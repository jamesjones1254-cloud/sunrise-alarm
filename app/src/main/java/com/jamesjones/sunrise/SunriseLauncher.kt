package com.jamesjones.sunrise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Gets the sunrise screen in front of the user, including over the lock screen. */
object SunriseLauncher {
    private const val CHANNEL_ID = "sunrise_alarm"
    private const val NOTIFICATION_ID = 7
    private const val REQUEST_SUNRISE = 10

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Sunrise alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Opens the sunrise screen when your alarm starts"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    fun sunriseIntent(
        context: Context,
        wakeAt: Long,
        sunriseMs: Long,
        snoozeMs: Long,
        preview: Boolean,
    ): Intent = Intent(context, SunriseActivity::class.java)
        .putExtra(AlarmScheduler.EXTRA_WAKE_AT, wakeAt)
        .putExtra(AlarmScheduler.EXTRA_SUNRISE_MS, sunriseMs)
        .putExtra(AlarmScheduler.EXTRA_SNOOZE_MS, snoozeMs)
        .putExtra(AlarmScheduler.EXTRA_PREVIEW, preview)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    /**
     * Called from the alarm. A high-priority notification with a full-screen intent is the
     * route Android supports for alarm clocks: it switches the screen on and opens the
     * sunrise over the lock screen. If the phone is in use it appears as a banner instead.
     */
    fun launchFromAlarm(context: Context, wakeAt: Long, sunriseMs: Long, snoozeMs: Long) {
        ensureChannel(context)
        val intent = sunriseIntent(context, wakeAt, sunriseMs, snoozeMs, preview = false)
        val pending = PendingIntent.getActivity(
            context, REQUEST_SUNRISE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sun)
            .setContentTitle("Sunrise alarm")
            .setContentText("Your sunrise has started. Tap to open.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications not allowed; the direct start below is the fallback.
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Android may block this from the background; the full-screen notification covers it.
        }
    }

    fun startPreview(context: Context) {
        val now = System.currentTimeMillis()
        val length = 45_000L
        context.startActivity(sunriseIntent(context, now + length, length, 15_000L, preview = true))
    }

    fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
