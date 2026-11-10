package com.jamesjones.sunrise

import android.content.Context

/** Everything the user can set, stored on the phone. */
class AlarmSettings(context: Context) {
    private val prefs = context.getSharedPreferences("alarm", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var hour: Int
        get() = prefs.getInt("hour", 7)
        set(v) = prefs.edit().putInt("hour", v).apply()

    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(v) = prefs.edit().putInt("minute", v).apply()

    /** How long the light takes to go from black to full daylight. */
    var sunriseMinutes: Int
        get() = prefs.getInt("sunrise_minutes", 20)
        set(v) = prefs.edit().putInt("sunrise_minutes", v).apply()

    var snoozeMinutes: Int
        get() = prefs.getInt("snooze_minutes", 9)
        set(v) = prefs.edit().putInt("snooze_minutes", v).apply()

    var repeat: Int
        get() = prefs.getInt("repeat", REPEAT_DAILY)
        set(v) = prefs.edit().putInt("repeat", v).apply()

    /** The wake-up time (full light) of the alarm currently scheduled, or 0 if none. */
    var nextWakeAt: Long
        get() = prefs.getLong("next_wake_at", 0L)
        set(v) = prefs.edit().putLong("next_wake_at", v).apply()

    companion object {
        const val REPEAT_ONCE = 0
        const val REPEAT_DAILY = 1
        const val REPEAT_WEEKDAYS = 2
    }
}
