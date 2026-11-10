package com.jamesjones.sunrise

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var settings: AlarmSettings
    private lateinit var timeView: TextView
    private lateinit var enabledSwitch: Switch
    private lateinit var summary: TextView
    private lateinit var warnings: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = AlarmSettings(this)
        SunriseLauncher.ensureChannel(this)

        timeView = findViewById(R.id.time)
        enabledSwitch = findViewById(R.id.enabled)
        summary = findViewById(R.id.summary)
        warnings = findViewById(R.id.warnings)

        timeView.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    settings.hour = h
                    settings.minute = m
                    settings.enabled = true
                    applyChanges()
                },
                settings.hour,
                settings.minute,
                DateFormat.is24HourFormat(this),
            ).show()
        }

        enabledSwitch.isChecked = settings.enabled
        enabledSwitch.setOnCheckedChangeListener { _, on ->
            if (on != settings.enabled) {
                settings.enabled = on
                applyChanges()
            }
        }

        bindChoice(
            R.id.sunrise_group,
            mapOf(R.id.sunrise_10 to 10, R.id.sunrise_20 to 20, R.id.sunrise_30 to 30),
            settings.sunriseMinutes,
        ) { settings.sunriseMinutes = it }

        bindChoice(
            R.id.snooze_group,
            mapOf(R.id.snooze_5 to 5, R.id.snooze_9 to 9, R.id.snooze_10 to 10),
            settings.snoozeMinutes,
        ) { settings.snoozeMinutes = it }

        bindChoice(
            R.id.repeat_group,
            mapOf(
                R.id.repeat_daily to AlarmSettings.REPEAT_DAILY,
                R.id.repeat_weekdays to AlarmSettings.REPEAT_WEEKDAYS,
                R.id.repeat_once to AlarmSettings.REPEAT_ONCE,
            ),
            settings.repeat,
        ) { settings.repeat = it }

        findViewById<Button>(R.id.preview).setOnClickListener { SunriseLauncher.startPreview(this) }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions may have changed in Settings, so re-check and re-schedule.
        if (settings.enabled) AlarmScheduler.schedule(this)
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun bindChoice(groupId: Int, options: Map<Int, Int>, current: Int, onPick: (Int) -> Unit) {
        val group = findViewById<RadioGroup>(groupId)
        options.entries.firstOrNull { it.value == current }?.let { group.check(it.key) }
        group.setOnCheckedChangeListener { _, checkedId ->
            options[checkedId]?.let {
                onPick(it)
                applyChanges()
            }
        }
    }

    private fun applyChanges() {
        AlarmScheduler.schedule(this)
        refresh()
    }

    private fun refresh() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.hour)
            set(Calendar.MINUTE, settings.minute)
        }
        timeView.text = DateFormat.getTimeFormat(this).format(cal.time)
        if (enabledSwitch.isChecked != settings.enabled) enabledSwitch.isChecked = settings.enabled

        val wake = settings.nextWakeAt
        summary.text = if (settings.enabled && wake > 0L) {
            val start = wake - settings.sunriseMinutes * 60_000L
            "Sunrise starts ${day(start)} ${time(start)}\nFull light and birdsong at ${time(wake)}"
        } else if (settings.enabled) {
            "Alarm can't be set yet. See below."
        } else {
            "Alarm is off"
        }
        buildWarnings()
    }

    private fun buildWarnings() {
        warnings.removeAllViews()
        val nm = getSystemService(NotificationManager::class.java)

        if (!AlarmScheduler.canScheduleExact(this)) {
            addWarning("Android needs permission for this app to set alarms.", "Allow alarms") {
                if (Build.VERSION.SDK_INT >= 31) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            }
        }
        if (!nm.areNotificationsEnabled()) {
            addWarning("Notifications are off, so the sunrise can't open over your lock screen.", "Turn on notifications") {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            addWarning("Allow full-screen notifications so the sunrise can wake the screen.", "Allow full screen") {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")),
                )
            }
        }
        val audio = getSystemService(AudioManager::class.java)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            addWarning("Your alarm volume is at zero, so you won't hear the birdsong.", "Turn alarm volume up") {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audio.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.6f).toInt().coerceAtLeast(1), AudioManager.FLAG_SHOW_UI)
                refresh()
            }
        }
    }

    private fun addWarning(text: String, action: String, onClick: () -> Unit) {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_warning)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (12 * density).toInt() }
        }
        card.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(getColor(R.color.text))
        })
        card.addView(Button(this).apply {
            this.text = action
            setAllCaps(false)
            setTextColor(getColor(R.color.accent))
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener { onClick() }
        })
        warnings.addView(card)
    }

    private fun time(ms: Long): String = DateFormat.getTimeFormat(this).format(Date(ms))

    private fun day(ms: Long): String {
        val today = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ms }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        return when {
            sameDay(today, then) -> "today at"
            sameDay(tomorrow, then) -> "tomorrow at"
            else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms)) + " at"
        }
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
