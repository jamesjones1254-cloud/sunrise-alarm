package com.jamesjones.sunrise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when the sunrise should begin. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = AlarmSettings(context)
        val now = System.currentTimeMillis()
        val defaultSunrise = s.sunriseMinutes * 60_000L
        val wakeAt = intent.getLongExtra(AlarmScheduler.EXTRA_WAKE_AT, now + defaultSunrise)
        val sunriseMs = intent.getLongExtra(AlarmScheduler.EXTRA_SUNRISE_MS, defaultSunrise)

        // Line up the next one straight away (tomorrow, next weekday, or nothing).
        if (s.repeat == AlarmSettings.REPEAT_ONCE) s.enabled = false
        AlarmScheduler.schedule(context, after = wakeAt)

        // Skip alarms that are long past (e.g. the phone was switched off overnight).
        if (now > wakeAt + 30 * 60_000L) return

        SunriseLauncher.launchFromAlarm(context, wakeAt, sunriseMs, s.snoozeMinutes * 60_000L)
    }
}

/** Alarms are wiped on reboot, so put the next one back. Also handles clock/time-zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.schedule(context)
    }
}
