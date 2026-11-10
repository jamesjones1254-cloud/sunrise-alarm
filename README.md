# Sunrise

A personal Android sunrise alarm clock.

- Set a wake-up time. The screen starts fading in before it (10, 20 or 30 minutes), from black through deep red and orange to warm daylight, and reaches full brightness at the wake-up time. The app controls its own screen brightness, so it starts truly dim.
- Birdsong and wind chimes fade in very quietly a couple of minutes before the wake-up time and slowly get louder.
- **Snooze** silences the sound for 5, 9 or 10 minutes but leaves the light fully on.
- Repeats every day, on weekdays only, or once. Survives reboots.

## Installing

Every push to `main` builds a signed APK on GitHub Actions and attaches it to the
[latest release](../../releases/latest). Open that page on the phone, download `Sunrise.apk`,
and allow installs from your browser when Android asks.

## First run

Open the app once and allow notifications. That's how the sunrise opens over the lock screen.
If any permission is missing, the app shows a card with a button that goes straight to the right setting.
Use **Preview the sunrise** to see a 45-second version.

## Layout

- `MainActivity` handles settings and permission checks
- `AlarmScheduler` handles `AlarmManager.setAlarmClock` and works out the next wake time
- `AlarmReceiver` and `BootReceiver` start the sunrise and reschedule
- `SunriseLauncher` posts the full-screen alarm notification
- `SunriseActivity` and `SunriseView` show the sunrise screen, brightness ramp, sound and snooze
- `tools/make_sound.py` synthesises `res/raw/dawn_chorus.ogg`
