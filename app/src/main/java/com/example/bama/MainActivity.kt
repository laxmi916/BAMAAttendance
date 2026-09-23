package com.example.bama

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max


class MainActivity : Activity() {

    companion object {

        const val PREFS = "attendance"

        const val MORNING_PREFIX = "morning_"
        const val EVENING_PREFIX = "evening_"

        const val CHANNEL_ID = "attendance_channel"
        const val NOTIFICATION_ID = 7

        const val ALARM_REQUEST_CODE = 100

        // 7 hours
        const val SEVEN_HOURS =
            7L * 60L * 60L * 1000L
    }


    // =========================================================
    // VIEWS
    // =========================================================

    private lateinit var statusText: TextView

    private lateinit var morningButton: Button
    private lateinit var eveningButton: Button
    private lateinit var historyButton: Button
    private lateinit var clearButton: Button


    // =========================================================
    // PREFERENCES
    // =========================================================

    private val prefs by lazy {

        getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
    }


    // =========================================================
    // DATE AND TIME FORMAT
    // =========================================================

    private val dateFormat =
        SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        )

    private val timeFormat =
        SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        )


    // =========================================================
    // HANDLER
    // =========================================================

    private val handler =
        Handler(Looper.getMainLooper())


    private val statusUpdater =
        object : Runnable {

            override fun run() {

                updateStatus()

                handler.postDelayed(
                    this,
                    1000
                )
            }
        }


    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )


        setContentView(
            R.layout.activity_main
        )


        // -----------------------------------------------------
        // Find views
        // -----------------------------------------------------

        statusText =
            findViewById(
                R.id.statusText
            )


        morningButton =
            findViewById(
                R.id.morningButton
            )


        eveningButton =
            findViewById(
                R.id.eveningButton
            )


        historyButton =
            findViewById(
                R.id.historyButton
            )


        clearButton =
            findViewById(
                R.id.clearButton
            )


        // -----------------------------------------------------
        // Button clicks
        // -----------------------------------------------------

        morningButton.setOnClickListener {

            markMorning()
        }


        eveningButton.setOnClickListener {

            markEvening()
        }


        historyButton.setOnClickListener {

            showHistory()
        }


        clearButton.setOnClickListener {

            clearToday()
        }


        // -----------------------------------------------------
        // Notification
        // -----------------------------------------------------

        createNotificationChannel()

        requestNotificationPermission()


        // -----------------------------------------------------
        // Display today's status
        // -----------------------------------------------------

        updateStatus()
    }


    // =========================================================
    // ON RESUME
    // =========================================================

    override fun onResume() {

        super.onResume()


        updateStatus()


        // Re-create alarm if today's Morning exists
        // and seven hours have not yet completed.

        scheduleSavedMorningAlarm()


        handler.post(
            statusUpdater
        )
    }


    // =========================================================
    // ON PAUSE
    // =========================================================

    override fun onPause() {

        super.onPause()

        handler.removeCallbacks(
            statusUpdater
        )
    }


    // =========================================================
    // GET DATE
    // =========================================================

    private fun dateString(
        daysAgo: Int = 0
    ): String {

        val calendar =
            Calendar.getInstance()


        calendar.add(
            Calendar.DAY_OF_YEAR,
            -daysAgo
        )


        return dateFormat.format(
            calendar.time
        )
    }


    // =========================================================
    // FORMAT TIME
    // =========================================================

    private fun formatTime(
        milliseconds: Long
    ): String {

        return timeFormat.format(
            Date(milliseconds)
        )
    }


    // =========================================================
    // FORMAT DURATION
    // =========================================================

    private fun formatDuration(
        milliseconds: Long
    ): String {

        if (milliseconds <= 0L) {

            return "00:00:00"
        }


        val totalSeconds =
            milliseconds / 1000L


        val hours =
            totalSeconds / 3600L


        val minutes =
            (totalSeconds % 3600L) / 60L


        val seconds =
            totalSeconds % 60L


        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }


    // =========================================================
    // CHECK MORNING TIME
    // =========================================================

    private fun morningTimeAllowed(): Boolean {

        val calendar =
            Calendar.getInstance()


        val hour =
            calendar.get(
                Calendar.HOUR_OF_DAY
            )


        val minute =
            calendar.get(
                Calendar.MINUTE
            )


        val currentMinutes =
            hour * 60 + minute


        val start =
            8 * 60 + 15


        val end =
            13 * 60 + 45


        return currentMinutes in start..end
    }


    // =========================================================
    // CHECK EVENING TIME
    // =========================================================

    private fun eveningTimeAllowed(): Boolean {

        val calendar =
            Calendar.getInstance()


        val hour =
            calendar.get(
                Calendar.HOUR_OF_DAY
            )


        val minute =
            calendar.get(
                Calendar.MINUTE
            )


        val currentMinutes =
            hour * 60 + minute


        val start =
            12 * 60 + 15


        val end =
            17 * 60 + 45


        return currentMinutes in start..end
    }


    // =========================================================
    // MARK MORNING
    // =========================================================

    private fun markMorning() {

        val date =
            dateString()


        // -----------------------------------------------------
        // Check valid Morning time
        // -----------------------------------------------------

        if (!morningTimeAllowed()) {

            toast(
                "Morning time must be between 08:15 and 13:45."
            )

            return
        }


        // -----------------------------------------------------
        // Don't allow duplicate Morning
        // -----------------------------------------------------

        val existingMorning =
            prefs.getLong(
                MORNING_PREFIX + date,
                0L
            )


        if (existingMorning != 0L) {

            toast(
                "Morning already marked today."
            )

            return
        }


        // -----------------------------------------------------
        // Save current time
        // -----------------------------------------------------

        val morning =
            System.currentTimeMillis()


        prefs.edit()

            .putLong(
                MORNING_PREFIX + date,
                morning
            )

            .apply()


        // -----------------------------------------------------
        // Schedule 7-hour notification
        // -----------------------------------------------------

        scheduleAlarm(
            morning
        )


        // -----------------------------------------------------
        // Display
        // -----------------------------------------------------

        val completeTime =
            morning + SEVEN_HOURS


        showMessage(

            "Morning Marked",

            "Morning : " +
                    formatTime(morning) +

                    "\n\n" +

                    "7 Hours Complete At : " +
                    formatTime(completeTime)
        )


        updateStatus()
    }


    // =========================================================
    // MARK EVENING
    // =========================================================

    private fun markEvening() {

        val date =
            dateString()


        // -----------------------------------------------------
        // Check valid evening time
        // -----------------------------------------------------

        if (!eveningTimeAllowed()) {

            toast(
                "Evening time must be between 12:15 and 17:45."
            )

            return
        }


        // -----------------------------------------------------
        // Don't allow duplicate Evening
        // -----------------------------------------------------

        val existingEvening =
            prefs.getLong(
                EVENING_PREFIX + date,
                0L
            )


        if (existingEvening != 0L) {

            toast(
                "Evening already marked today."
            )

            return
        }


        // -----------------------------------------------------
        // Save current evening time
        // -----------------------------------------------------

        val evening =
            System.currentTimeMillis()


        prefs.edit()

            .putLong(
                EVENING_PREFIX + date,
                evening
            )

            .apply()


        // -----------------------------------------------------
        // Get Morning
        // -----------------------------------------------------

        val morning =
            prefs.getLong(
                MORNING_PREFIX + date,
                0L
            )


        // =====================================================
        // CASE 1:
        // Morning exists
        // =====================================================

        if (morning != 0L) {


            // Cancel 7-hour alarm because Evening
            // attendance has now been recorded.

            cancelAlarm()


            val duration =
                max(
                    0L,
                    evening - morning
                )


            val message =

                "Morning : " +
                        formatTime(morning) +

                        "\n\n" +

                        "Evening : " +
                        formatTime(evening) +

                        "\n\n" +

                        "Total Time : " +
                        formatDuration(duration) +

                        "\n\n" +

                        if (duration >= SEVEN_HOURS) {

                            "7 hours completed."

                        } else {

                            "7 hours not completed." +
                                    "\n\n" +
                                    "Remaining : " +
                                    formatDuration(
                                        SEVEN_HOURS - duration
                                    )
                        }


            showMessage(
                "Evening Marked",
                message
            )
        }


        // =====================================================
        // CASE 2:
        // Morning was NOT marked in the app
        // =====================================================

        else {

            showMessage(

                "Evening Marked",

                "Evening : " +
                        formatTime(evening) +

                        "\n\n" +

                        "Morning was not recorded " +
                        "in the app." +

                        "\n\n" +

                        "Status : Morning Missing" +

                        "\n\n" +

                        "Total hours cannot be " +
                        "calculated."
            )
        }


        updateStatus()
    }


    // =========================================================
    // UPDATE MAIN SCREEN
    // =========================================================

    private fun updateStatus() {

        val date =
            dateString()


        val morning =
            prefs.getLong(
                MORNING_PREFIX + date,
                0L
            )


        val evening =
            prefs.getLong(
                EVENING_PREFIX + date,
                0L
            )


        // =====================================================
        // NO MORNING + NO EVENING
        // =====================================================

        if (morning == 0L &&
            evening == 0L) {

            statusText.text =
                "No attendance recorded today."

            return
        }


        // =====================================================
        // EVENING EXISTS, MORNING MISSING
        // =====================================================

        if (morning == 0L &&
            evening != 0L) {

            statusText.text =

                "Evening : " +
                        formatTime(evening) +

                        "\n\n" +

                        "Morning : Not recorded" +

                        "\n\n" +

                        "Status : Morning Missing" +

                        "\n\n" +

                        "Total hours cannot be calculated."

            return
        }


        // =====================================================
        // MORNING EXISTS, EVENING MISSING
        // =====================================================

        if (morning != 0L &&
            evening == 0L) {


            val now =
                System.currentTimeMillis()


            val elapsed =
                max(
                    0L,
                    now - morning
                )


            val remaining =
                SEVEN_HOURS - elapsed


            if (remaining > 0L) {

                statusText.text =

                    "Morning : " +
                            formatTime(morning) +

                            "\n\n" +

                            "Elapsed : " +
                            formatDuration(elapsed) +

                            "\n\n" +

                            "Remaining : " +
                            formatDuration(remaining) +

                            "\n\n" +

                            "Complete at : " +
                            formatTime(
                                morning + SEVEN_HOURS
                            )

            } else {

                statusText.text =

                    "Morning : " +
                            formatTime(morning) +

                            "\n\n" +

                            "7 HOURS COMPLETED"
            }


            return
        }


        // =====================================================
        // MORNING + EVENING EXIST
        // =====================================================

        val duration =
            max(
                0L,
                evening - morning
            )


        if (duration >= SEVEN_HOURS) {

            statusText.text =

                "Morning : " +
                        formatTime(morning) +

                        "\n\n" +

                        "Evening : " +
                        formatTime(evening) +

                        "\n\n" +

                        "Total : " +
                        formatDuration(duration) +

                        "\n\n" +

                        "7 HOURS COMPLETED"

        } else {

            statusText.text =

                "Morning : " +
                        formatTime(morning) +

                        "\n\n" +

                        "Evening : " +
                        formatTime(evening) +

                        "\n\n" +

                        "Total : " +
                        formatDuration(duration) +

                        "\n\n" +

                        "7 Hours NOT completed" +

                        "\n\n" +

                        "Remaining : " +
                        formatDuration(
                            SEVEN_HOURS - duration
                        )
        }
    }


    // =========================================================
    // SCHEDULE ALARM
    // =========================================================

    private fun scheduleAlarm(
        morning: Long
    ) {

        val alarmManager =
            getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager


        val intent =
            Intent(
                this,
                AlarmReceiver::class.java
            )


        val pendingIntent =
            PendingIntent.getBroadcast(

                this,

                ALARM_REQUEST_CODE,

                intent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )


        val triggerTime =
            morning + SEVEN_HOURS


        // -----------------------------------------------------
        // Android 12+
        // -----------------------------------------------------

        if (Build.VERSION.SDK_INT >= 31) {

            if (!alarmManager.canScheduleExactAlarms()) {

                try {

                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        )
                    )

                } catch (e: Exception) {

                    // If exact-alarm settings cannot be opened,
                    // use an inexact alarm instead.

                    alarmManager.setAndAllowWhileIdle(

                        AlarmManager.RTC_WAKEUP,

                        triggerTime,

                        pendingIntent
                    )
                }

                return
            }
        }


        // -----------------------------------------------------
        // Exact alarm
        // -----------------------------------------------------

        if (Build.VERSION.SDK_INT >= 23) {

            alarmManager.setExactAndAllowWhileIdle(

                AlarmManager.RTC_WAKEUP,

                triggerTime,

                pendingIntent
            )

        } else {

            alarmManager.setExact(

                AlarmManager.RTC_WAKEUP,

                triggerTime,

                pendingIntent
            )
        }
    }


    // =========================================================
    // RE-SCHEDULE SAVED MORNING ALARM
    // =========================================================

    private fun scheduleSavedMorningAlarm() {

        val date =
            dateString()


        val morning =
            prefs.getLong(
                MORNING_PREFIX + date,
                0L
            )


        if (morning == 0L) {
            return
        }


        val completeTime =
            morning + SEVEN_HOURS


        if (System.currentTimeMillis() >=
            completeTime) {

            return
        }


        scheduleAlarm(
            morning
        )
    }


    // =========================================================
    // CANCEL ALARM
    // =========================================================

    private fun cancelAlarm() {

        val alarmManager =
            getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager


        val intent =
            Intent(
                this,
                AlarmReceiver::class.java
            )


        val pendingIntent =
            PendingIntent.getBroadcast(

                this,

                ALARM_REQUEST_CODE,

                intent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )


        alarmManager.cancel(
            pendingIntent
        )
    }


    // =========================================================
    // HISTORY - LAST 45 DAYS
    // =========================================================

    private fun showHistory() {

        val history =
            StringBuilder()


        for (i in 0 until 45) {

            val date =
                dateString(i)


            val morning =
                prefs.getLong(
                    MORNING_PREFIX + date,
                    0L
                )


            val evening =
                prefs.getLong(
                    EVENING_PREFIX + date,
                    0L
                )


            history.append(
                date
            )


            history.append(
                "\n"
            )


            // -------------------------------------------------
            // No attendance
            // -------------------------------------------------

            if (morning == 0L &&
                evening == 0L) {

                history.append(
                    "Status : Not attended"
                )

            }


            // -------------------------------------------------
            // Evening only
            // -------------------------------------------------

            else if (morning == 0L &&
                     evening != 0L) {

                history.append(
                    "Morning : Not recorded\n"
                )

                history.append(
                    "Evening : " +
                            formatTime(evening) +
                            "\n"
                )

                history.append(
                    "Status : Morning missing"
                )
            }


            // -------------------------------------------------
            // Morning only
            // -------------------------------------------------

            else if (morning != 0L &&
                     evening == 0L) {

                history.append(
                    "Morning : " +
                            formatTime(morning) +
                            "\n"
                )

                history.append(
                    "Evening : Not marked\n"
                )

                history.append(
                    "Status : Incomplete"
                )
            }


            // -------------------------------------------------
            // Morning + Evening
            // -------------------------------------------------

            else {

                val duration =
                    max(
                        0L,
                        evening - morning
                    )


                history.append(
                    "Morning : " +
                            formatTime(morning) +
                            "\n"
                )


                history.append(
                    "Evening : " +
                            formatTime(evening) +
                            "\n"
                )


                history.append(
                    "Total : " +
                            formatDuration(
                                duration
                            ) +
                            "\n"
                )


                history.append(
                    "Status : " +
                            if (duration >= SEVEN_HOURS) {

                                "7 hours completed"

                            } else {

                                "Less than 7 hours"
                            }
                )
            }


            history.append(
                "\n\n"
            )
        }


        // -----------------------------------------------------
        // Scrollable history
        // -----------------------------------------------------

        val scrollView =
            ScrollView(this)


        val textView =
            TextView(this)


        textView.text =
            history.toString()


        textView.textSize =
            16f


        textView.setPadding(
            20,
            20,
            20,
            20
        )


        scrollView.addView(
            textView
        )


        AlertDialog.Builder(this)

            .setTitle(
                "Last 45 Days"
            )

            .setView(
                scrollView
            )

            .setPositiveButton(
                "OK",
                null
            )

            .show()
    }


    // =========================================================
    // CLEAR TODAY
    // =========================================================

    private fun clearToday() {

        val date =
            dateString()


        val morning =
            prefs.getLong(
                MORNING_PREFIX + date,
                0L
            )


        val evening =
            prefs.getLong(
                EVENING_PREFIX + date,
                0L
            )


        if (morning == 0L &&
            evening == 0L) {

            toast(
                "No attendance recorded today."
            )

            return
        }


        // Cancel today's alarm

        cancelAlarm()


        // Remove today's records

        prefs.edit()

            .remove(
                MORNING_PREFIX + date
            )

            .remove(
                EVENING_PREFIX + date
            )

            .apply()


        updateStatus()


        toast(
            "Today's attendance cleared."
        )
    }


    // =========================================================
    // NOTIFICATION CHANNEL
    // =========================================================

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(

                    CHANNEL_ID,

                    "Attendance Timer",

                    NotificationManager
                        .IMPORTANCE_HIGH
                )


            channel.description =
                "7-hour attendance notification"


            val manager =
                getSystemService(
                    NotificationManager::class.java
                )


            manager.createNotificationChannel(
                channel
            )
        }
    }


    // =========================================================
    // NOTIFICATION PERMISSION
    // =========================================================

    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= 33) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(

                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),

                    10
                )
            }
        }
    }


    // =========================================================
    // TOAST
    // =========================================================

    private fun toast(
        message: String
    ) {

        Toast.makeText(

            this,

            message,

            Toast.LENGTH_LONG

        ).show()
    }


    // =========================================================
    // DIALOG
    // =========================================================

    private fun showMessage(
        title: String,
        message: String
    ) {

        AlertDialog.Builder(this)

            .setTitle(title)

            .setMessage(message)

            .setPositiveButton(
                "OK",
                null
            )

            .show()
    }
}


// =============================================================
// ALARM RECEIVER
// =============================================================

class AlarmReceiver :
    BroadcastReceiver() {


    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {

        // -----------------------------------------------------
        // Notification manager
        // -----------------------------------------------------

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )


        // -----------------------------------------------------
        // Open app when notification is tapped
        // -----------------------------------------------------

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


        // -----------------------------------------------------
        // Notification
        // -----------------------------------------------------

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

                .setAutoCancel(
                    true
                )

                .setPriority(
                    Notification.PRIORITY_HIGH
                )

                .build()


        // -----------------------------------------------------
        // Show
        // -----------------------------------------------------

        manager.notify(

            MainActivity.NOTIFICATION_ID,

            notification
        )
    }
}