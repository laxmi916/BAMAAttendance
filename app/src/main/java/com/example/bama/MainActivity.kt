package com.example.bama

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : android.app.Activity() {

    companion object {
        const val PREFS = "attendance"
        const val MORNING_PREFIX = "morning_"
        const val EVENING_PREFIX = "evening_"
        const val CHANNEL_ID = "attendance_channel"
        const val NOTIFICATION_ID = 7
        const val ALARM_REQUEST_CODE = 100
        const val SEVEN_HOURS = 7L * 60L * 60L * 1000L
    }

    private lateinit var statusText: TextView

    private val prefs by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val dateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val timeFormat =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val updater = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.morningButton).setOnClickListener {
            markMorning()
        }

        findViewById<Button>(R.id.eveningButton).setOnClickListener {
            markEvening()
        }

        findViewById<Button>(R.id.historyButton).setOnClickListener {
            showHistory()
        }

        findViewById<Button>(R.id.clearButton).setOnClickListener {
            clearToday()
        }

        createNotificationChannel()
        requestNotificationPermission()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            updateStatus()
            scheduleSavedAlarm()
        }
        handler.post(updater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updater)
    }

    private fun today(): String = dateFormat.format(Date())

    private fun dateDaysAgo(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return dateFormat.format(cal.time)
    }

    private fun formatTime(time: Long): String =
        timeFormat.format(Date(time))

    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = max(0L, milliseconds) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    private fun morningAllowed(): Boolean {
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 +
                cal.get(Calendar.MINUTE)
        return minutes in (8 * 60 + 15)..(13 * 60 + 45)
    }

    private fun eveningAllowed(): Boolean {
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 +
                cal.get(Calendar.MINUTE)
        return minutes in (12 * 60 + 15)..(17 * 60 + 45)
    }

    private fun markMorning() {
        val date = today()
        val key = MORNING_PREFIX + date

        if (!morningAllowed()) {
            toast("Morning time must be between 08:15 and 13:45")
            return
        }

        if (prefs.contains(key)) {
            toast("Morning already marked today")
            return
        }

        val morning = System.currentTimeMillis()

        prefs.edit()
            .putLong(key, morning)
            .remove(EVENING_PREFIX + date)
            .apply()

        scheduleAlarm(morning)

        toast(
            "Morning marked at ${formatTime(morning)}\n" +
                    "7 hours complete at ${formatTime(morning + SEVEN_HOURS)}"
        )

        updateStatus()
    }

    private fun markEvening() {
        val date = today()
        val morning = prefs.getLong(MORNING_PREFIX + date, 0L)

        if (!eveningAllowed()) {
            toast("Evening time must be between 12:15 and 17:45")
            return
        }

        if (morning == 0L) {
            toast("Mark Morning first")
            return
        }

        if (prefs.contains(EVENING_PREFIX + date)) {
            toast("Evening already marked today")
            return
        }

        val evening = System.currentTimeMillis()
        val duration = max(0L, evening - morning)

        prefs.edit()
            .putLong(EVENING_PREFIX + date, evening)
            .apply()

        cancelAlarm()

        val message =
            "Morning : ${formatTime(morning)}\n" +
                    "Evening : ${formatTime(evening)}\n" +
                    "Total : ${formatDuration(duration)}\n\n" +
                    if (duration >= SEVEN_HOURS) {
                        "7 hours completed."
                    } else {
                        "7 hours not completed.\n" +
                                "Remaining : ${formatDuration(SEVEN_HOURS - duration)}"
                    }

        showMessage("Evening Marked", message)
        updateStatus()
    }

    private fun updateStatus() {
        val date = today()
        val morning = prefs.getLong(MORNING_PREFIX + date, 0L)

        if (morning == 0L) {
            statusText.text = "No attendance recorded today."
            return
        }

        val evening = prefs.getLong(EVENING_PREFIX + date, 0L)

        if (evening != 0L) {
            val duration = max(0L, evening - morning)
            statusText.text =
                "Morning : ${formatTime(morning)}\n\n" +
                        "Evening : ${formatTime(evening)}\n\n" +
                        "Total : ${formatDuration(duration)}\n\n" +
                        if (duration >= SEVEN_HOURS) {
                            "7 HOURS COMPLETED"
                        } else {
                            "Remaining : ${formatDuration(SEVEN_HOURS - duration)}"
                        }
            return
        }

        val elapsed = max(0L, System.currentTimeMillis() - morning)
        val remaining = SEVEN_HOURS - elapsed

        statusText.text =
            "Morning : ${formatTime(morning)}\n\n" +
                    if (remaining > 0L) {
                        "Elapsed : ${formatDuration(elapsed)}\n\n" +
                                "Remaining : ${formatDuration(remaining)}\n\n" +
                                "Complete at : ${formatTime(morning + SEVEN_HOURS)}"
                    } else {
                        "7 HOURS COMPLETED"
                    }
    }

    private fun showHistory() {
        val history = StringBuilder()

        for (i in 0 until 45) {
            val date = dateDaysAgo(i)
            val morning = prefs.getLong(MORNING_PREFIX + date, 0L)
            val evening = prefs.getLong(EVENING_PREFIX + date, 0L)

            history.append(date).append("\n")

            when {
                morning == 0L -> {
                    history.append("Not attended\n")
                }

                evening == 0L -> {
                    history.append("Morning : ${formatTime(morning)}\n")
                    history.append("Evening : Not marked\n")
                    history.append("Status : Incomplete\n")
                }

                else -> {
                    val duration = max(0L, evening - morning)
                    history.append("Morning : ${formatTime(morning)}\n")
                    history.append("Evening : ${formatTime(evening)}\n")
                    history.append("Total : ${formatDuration(duration)}\n")
                    history.append(
                        "Status : " +
                                if (duration >= SEVEN_HOURS)
                                    "7 hours completed"
                                else
                                    "Less than 7 hours"
                    ).append("\n")
                }
            }

            history.append("\n")
        }

        val scroll = ScrollView(this)
        val view = TextView(this)
        view.text = history.toString()
        view.textSize = 16f
        view.setPadding(20, 10, 20, 10)
        scroll.addView(view)

        AlertDialog.Builder(this)
            .setTitle("Last 45 Days")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun clearToday() {
        val date = today()
        val morning = prefs.getLong(MORNING_PREFIX + date, 0L)

        if (morning == 0L) {
            toast("No attendance recorded today")
            return
        }

        cancelAlarm()

        prefs.edit()
            .remove(MORNING_PREFIX + date)
            .remove(EVENING_PREFIX + date)
            .apply()

        updateStatus()
        toast("Today's attendance cleared")
    }

    private fun scheduleSavedAlarm() {
        val morning = prefs.getLong(MORNING_PREFIX + today(), 0L)
        if (morning == 0L) return
        if (prefs.contains(EVENING_PREFIX + today())) return
        if (System.currentTimeMillis() >= morning + SEVEN_HOURS) return

        if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!manager.canScheduleExactAlarms()) return
        }

        scheduleAlarm(morning, false)
    }

    private fun scheduleAlarm(morning: Long, askPermission: Boolean = true) {
        val manager = getSystemService(ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            if (askPermission) {
                toast("Allow 'Alarms & reminders' for the 7-hour notification")
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            return
        }

        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            morning + SEVEN_HOURS,
            pendingIntent
        )
    }

    private fun cancelAlarm() {
        val manager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pendingIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Attendance",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "7-hour attendance reminder"
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                10
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        val openApp =
            Intent(
                context,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                200,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            Notification.Builder(
                context,
                MainActivity.CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    "7 Hours Completed"
                )
                .setContentText(
                    "Your 7-hour attendance duration is complete."
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(true)
                .setPriority(
                    Notification.PRIORITY_HIGH
                )
                .build()

        manager.notify(
            MainActivity.NOTIFICATION_ID,
            notification
        )
    }
}