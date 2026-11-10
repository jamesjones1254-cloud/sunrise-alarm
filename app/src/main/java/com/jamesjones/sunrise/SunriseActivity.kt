package com.jamesjones.sunrise

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.util.Date
import kotlin.math.abs
import kotlin.math.pow

/**
 * The full-screen sunrise. Everything is worked out from the clock, so the light is
 * always where it should be for the time, even if the screen is reopened part-way.
 *
 * Timeline: black -> red glow -> orange -> warm daylight at the wake time.
 * Birdsong and chimes start very quietly shortly before the wake time and swell.
 * Snooze silences the sound but leaves the light fully on.
 */
class SunriseActivity : Activity() {

    private lateinit var sky: SunriseView
    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var snoozeButton: Button
    private lateinit var dismissButton: Button

    private var wakeAt = 0L
    private var sunriseMs = 20 * 60_000L
    private var snoozeMs = 9 * 60_000L
    private var preview = false

    private var player: MediaPlayer? = null
    private var playerStartedAt = 0L
    private var rampMs = 60_000L
    private var snoozedUntil = 0L
    private var silenced = false
    private var lastBrightness = -1f
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 100L)
        }
    }

    private val startAt: Long get() = wakeAt - sunriseMs
    private val soundLeadMs: Long get() = minOf(2 * 60_000L, sunriseMs / 4)
    private val soundStartAt: Long get() = wakeAt - soundLeadMs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_sunrise)
        hideSystemBars()

        sky = findViewById(R.id.sky)
        clock = findViewById(R.id.clock)
        status = findViewById(R.id.status)
        snoozeButton = findViewById(R.id.snooze)
        dismissButton = findViewById(R.id.dismiss)
        snoozeButton.setOnClickListener { snooze() }
        dismissButton.setOnClickListener { dismiss() }

        readExtras(intent)

        // Keep ticking even if the power button turns the screen off.
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sunrise:alarm").apply {
            setReferenceCounted(false)
            acquire(sunriseMs + 60 * 60_000L)
        }

        handler.post(ticker)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
        update()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Back does nothing here so the alarm can't be dismissed by accident.")
    override fun onBackPressed() {
        // Intentionally ignored: use Snooze or Dismiss.
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        stopSound()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun readExtras(intent: Intent) {
        val now = System.currentTimeMillis()
        val newWake = intent.getLongExtra(AlarmScheduler.EXTRA_WAKE_AT, now + sunriseMs)
        if (newWake != wakeAt) {
            stopSound()
            snoozedUntil = 0L
            silenced = false
        }
        wakeAt = newWake
        sunriseMs = intent.getLongExtra(AlarmScheduler.EXTRA_SUNRISE_MS, sunriseMs).coerceAtLeast(10_000L)
        snoozeMs = intent.getLongExtra(AlarmScheduler.EXTRA_SNOOZE_MS, snoozeMs).coerceAtLeast(5_000L)
        preview = intent.getBooleanExtra(AlarmScheduler.EXTRA_PREVIEW, false)
    }

    private fun update() {
        val now = System.currentTimeMillis()
        val p = ((now - startAt).toDouble() / sunriseMs).coerceIn(0.0, 1.0).toFloat()

        sky.setProgress(p)
        setBrightness(0.004f + 0.996f * p.pow(1.6f))

        // Sound: start once it's time, restart after snooze, swell gently.
        if (snoozedUntil > 0L) {
            if (now >= snoozedUntil) {
                snoozedUntil = 0L
                startSound(now, 30_000L)
            }
        } else if (player == null && !silenced && now >= soundStartAt) {
            startSound(now, (wakeAt - now).coerceAtLeast(0L) + 60_000L)
        }
        player?.let { mp ->
            val t = ((now - playerStartedAt).toFloat() / rampMs).coerceIn(0f, 1f)
            val v = 0.03f + 0.97f * t * t
            mp.setVolume(v, v)
            if (now - playerStartedAt > AUTO_SILENCE_MS) {
                stopSound()
                silenced = true
            }
        }

        val textColor = textColorFor(p)
        clock.text = DateFormat.getTimeFormat(this).format(Date(now))
        clock.setTextColor(textColor)
        status.setTextColor(textColor)
        status.text = when {
            snoozedUntil > 0L -> "Snoozing · birdsong returns at ${fmt(snoozedUntil)}"
            silenced -> "Sound stopped automatically. The light stays on."
            player != null -> if (preview) "Preview · good morning" else "Good morning"
            preview -> "Preview · full light at ${fmt(wakeAt)}"
            else -> "Sunrise · full light at ${fmt(wakeAt)}"
        }

        val ringing = player != null
        snoozeButton.visibility = if (ringing) View.VISIBLE else View.GONE
        snoozeButton.setTextColor(textColor)
        dismissButton.setTextColor(textColor)
        dismissButton.text = if (ringing || snoozedUntil > 0L || silenced) "Dismiss" else "Turn off sunrise"
    }

    private fun snooze() {
        stopSound()
        snoozedUntil = System.currentTimeMillis() + snoozeMs
        update()
    }

    private fun dismiss() {
        stopSound()
        SunriseLauncher.cancelNotification(this)
        finish()
    }

    private fun startSound(now: Long, ramp: Long) {
        stopSound()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audio = getSystemService(AudioManager::class.java)
        val mp = MediaPlayer.create(this, R.raw.dawn_chorus, attrs, audio.generateAudioSessionId()) ?: return
        mp.isLooping = true
        mp.setVolume(0.03f, 0.03f)
        mp.start()
        player = mp
        playerStartedAt = now
        rampMs = maxOf(ramp, 5_000L)
    }

    private fun stopSound() {
        player?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // already stopped
            }
            it.release()
        }
        player = null
    }

    private fun setBrightness(b: Float) {
        val value = b.coerceIn(0.004f, 1f)
        if (abs(value - lastBrightness) < 0.003f) return
        lastBrightness = value
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    /** Dim warm text in the dark (so it doesn't light the room), dark brown once the sky is bright. */
    private fun textColorFor(p: Float): Int {
        val night = Color.argb(0x38, 255, 200, 160)
        val dawn = Color.argb(0xCC, 255, 225, 190)
        val day = Color.argb(0xE6, 74, 36, 16)
        return if (p < 0.55f) {
            SunriseView.lerpColor(night, dawn, p / 0.55f)
        } else {
            SunriseView.lerpColor(dawn, day, (p - 0.55f) / 0.45f)
        }
    }

    private fun fmt(ms: Long): String = DateFormat.getTimeFormat(this).format(Date(ms))

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    companion object {
        /** Stop the sound if nobody responds for this long. The light stays on. */
        private const val AUTO_SILENCE_MS = 20 * 60_000L
    }
}
