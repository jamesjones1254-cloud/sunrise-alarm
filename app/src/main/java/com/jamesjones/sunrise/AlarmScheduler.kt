package com.jamesjones.sunrise

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules the next sunrise with AlarmManager.setAlarmClock, which fires on time
 * even in Doze and shows the alarm icon in the status bar.
 * The alarm fires at (wake time - sunrise length) so the light is full at the wake time.
 */
object AlarmScheduler {
    const val EXTRA_WAKE_AT = "wake_at"
    const val EXTRA_SUNRISE_MS = "sunrise_ms"
    const val EXTRA_SNOOZE_MS = "snooze_ms"
    const val EXTRA_PREVIEW = "preview"

    private const val REQUEST_ALARM = 1
    private const val REQUEST_SHOW = 2

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** Next wake-up time strictly after [after] that matches the repeat setting. */
    fun nextWakeTime(s: AlarmSettings, after: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, s.hour)
            set(Calendar.MINUTE, s.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..8) {
            if (cal.timeInMillis > after && dayAllowed(s.repeat, cal.get(Calendar.DAY_OF_WEEK))) {
                return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun dayAllowed(repeat: Int, dayOfWeek: Int): Boolean =
        if (repeat == AlarmSettings.REPEAT_WEEKDAYS) {
            dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
        } else {
            true
        }

    /**
     * Cancels any existing alarm and, if the alarm is on, schedules the next one.
     * Returns false if Android won't let the app set exact alarms.
     */
    fun schedule(context: Context, after: Long = System.currentTimeMillis()): Boolean {
        val s = AlarmSettings(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        am.cancel(PendingIntent.getBroadcast(context, REQUEST_ALARM, Intent(context, AlarmReceiver::class.java), flags))

        if (!s.enabled) {
            s.nextWakeAt = 0L
            return true
        }
        if (!canScheduleExact(context)) {
            s.nextWakeAt = 0L
            return false
        }

        val wake = nextWakeTime(s, after)
        val sunriseMs = s.sunriseMinutes * 60_000L
        val trigger = maxOf(wake - sunriseMs, System.currentTimeMillis() + 1_000L)

        val fire = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_WAKE_AT, wake)
            .putExtra(EXTRA_SUNRISE_MS, sunriseMs)
        val operation = PendingIntent.getBroadcast(context, REQUEST_ALARM, fire, flags)
        val show = PendingIntent.getActivity(context, REQUEST_SHOW, Intent(context, MainActivity::class.java), flags)

        return try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), operation)
            s.nextWakeAt = wake
            true
        } catch (e: SecurityException) {
            s.nextWakeAt = 0L
            false
        }
    }
}
