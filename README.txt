BAMA ATTENDANCE - MINIMAL KOTLIN APP

Features:
- Morning: mark office biometric attendance time on the phone.
- Shows elapsed time, remaining time, and 7-hour completion time.
- Evening: mark leaving time and calculate total duration.
- 7-hour notification using an Android exact alarm.
- History: last 45 calendar days, including Not attended days.
- Clear Today.
- No SQLite and no internet connection are required.

GITHUB APK BUILD
1. Create a private GitHub repository named BamaAttendance.
2. Upload the contents of this project to the repository root.
3. Open Actions.
4. Select Build APK.
5. Tap Run workflow.
6. Wait for the green successful run.
7. Open the run and scroll to Artifacts.
8. Download BamaAttendance-APK.
9. Open the downloaded ZIP and install app-debug.apk on the Android phone.

FIRST TEST
For a quick notification test, temporarily change SEVEN_HOURS in MainActivity.kt to:
    const val SEVEN_HOURS = 2L * 60L * 1000L
Build and install again, press MORNING, close the app, and wait about two minutes.
After testing, change it back to:
    const val SEVEN_HOURS = 7L * 60L * 60L * 1000L

PHONE PERMISSIONS
- Android 13+: allow Notifications.
- Android 12+: allow Alarms & reminders when requested so the 7-hour notification can be exact.

LIMITATION
This minimal version does not recreate an alarm after a phone reboot. The saved 45-day history remains, but the active 7-hour alarm must be set again by opening the app after reboot.
