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


class MainActivity : android.app.Activity() {

    companion object {

        const val PREFS = "attendance"

        const val MORNING_PREFIX = "morning_"

        const val EVENING_PREFIX = "evening_"

        const val CHANNEL_ID = "attendance_channel"

        const val NOTIFICATION_ID = 7

        const val ALARM_REQUEST_CODE = 100

        const val SEVEN_HOURS =
            7L * 60L * 60L * 1000L
    }


    // =========================================================
    // VIEWS
    // =========================================================

    private lateinit var statusText: TextView

    private lateinit var morningStatusText: TextView

    private lateinit var eveningStatusText: TextView

    private lateinit var dateText: TextView


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
    // DATE / TIME
    // =========================================================

    private val dateFormat =
        SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        )


    private val timeFormat =
        SimpleDateFormat(
            "hh:mm:ss a",
            Locale.getDefault()
        )


    // =========================================================
    // HANDLER
    // =========================================================

    private val handler =
        Handler(
            Looper.getMainLooper()
        )


    private val updater =
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
        // UI
        // -----------------------------------------------------

        statusText =
            findViewById(
                R.id.statusText
            )


        morningStatusText =
            findViewById(
                R.id.morningStatusText
            )


        eveningStatusText =
            findViewById(
                R.id.eveningStatusText
            )


        dateText =
            findViewById(
                R.id.dateText
            )


        // -----------------------------------------------------
        // Morning
        // -----------------------------------------------------

        findViewById<Button>(
            R.id.morningButton
        ).setOnClickListener {
            markMorning()
        }

        // -----------------------------------------------------
        // Evening
        // -----------------------------------------------------

        findViewById<Button>(
            R.id.eveningButton
        ).setOnClickListener {
            markEvening()
        }

        // -----------------------------------------------------
        // History
        // -----------------------------------------------------

        findViewById<Button>(
            R.id.historyButton
        ).setOnClickListener {
            showHistory()
        }

        // -----------------------------------------------------
        // Hamburger menu
        // -----------------------------------------------------

        findViewById<Button>(
            R.id.menuButton
        ).setOnClickListener {
            showMenu()
        }

        // -----------------------------------------------------
        // Bottom menu
        // -----------------------------------------------------

        findViewById<Button>(
            R.id.menuBottomButton
        ).setOnClickListener {
            showMenu()
        }

        // -----------------------------------------------------
        // Home
        // -----------------------------------------------------

        findViewById<Button>(
            R.id.homeButton
        ).setOnClickListener {
            updateStatus()

            Toast.makeText(
                this,
                "Home",
                Toast.LENGTH_SHORT
            ).show()
        }

        // -----------------------------------------------------
        // Notification
        // -----------------------------------------------------

        createNotificationChannel()
        requestNotificationPermission()

        // -----------------------------------------------------
        // Current status
        // -----------------------------------------------------

        updateStatus()
    }


    // =========================================================
    // ON RESUME
    // =========================================================

    override fun onResume() {

        super.onResume()

        updateStatus()

        scheduleSavedAlarm()

        handler.post(
            updater
        )
    }


    // =========================================================
    // ON PAUSE
    // =========================================================

    override fun onPause() {

        super.onPause()

        handler.removeCallbacks(
            updater
        )
    }


    // =========================================================
    // TODAY
    // =========================================================

    private fun today(): String {

        return dateFormat.format(
            Date()
        )
    }


    // =========================================================
    // DATE N DAYS AGO
    // =========================================================

    private fun dateDaysAgo(
        daysAgo: Int
    ): String {

        val cal =
            Calendar.getInstance()

        cal.add(
            Calendar.DAY_OF_YEAR,
            -daysAgo
        )

        return dateFormat.format(
            cal.time
        )
    }


    // =========================================================
    // FORMAT TIME
    // =========================================================

    private fun formatTime(
        time: Long
    ): String {

        return timeFormat.format(
            Date(time)
        )
    }


    // =========================================================
    // FORMAT DURATION
    // =========================================================

    private fun formatDuration(
        milliseconds: Long
    ): String {

        val totalSeconds =
            max(
                0L,
                milliseconds
            ) / 1000L


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
    // MORNING TIME
    // 08:15 AM - 02:00 PM
    // =========================================================

    private fun morningAllowed(): Boolean {

        val cal =
            Calendar.getInstance()


        val minutes =
            cal.get(
                Calendar.HOUR_OF_DAY
            ) * 60 +
                    cal.get(
                        Calendar.MINUTE
                    )


        return minutes in
                (8 * 60 + 15)..(14 * 60)
    }


    // =========================================================
    // EVENING TIME
    // 12:15 PM - 06:00 PM
    // =========================================================

    private fun eveningAllowed(): Boolean {

        val cal =
            Calendar.getInstance()


        val minutes =
            cal.get(
                Calendar.HOUR_OF_DAY
            ) * 60 +
                    cal.get(
                        Calendar.MINUTE
                    )


        return minutes in
                (12 * 60 + 15)..(18 * 60)
    }


    // =========================================================
    // MARK MORNING
    // =========================================================

    private fun markMorning() {

        val date =
            today()


        val morningKey =
            MORNING_PREFIX + date


        val eveningKey =
            EVENING_PREFIX + date


        if (!morningAllowed()) {

            toast(
                "Morning time must be between 08:15 AM and 02:00 PM"
            )

            return
        }


        // -----------------------------------------------------
        // Morning can only be marked once
        // -----------------------------------------------------

        if (prefs.contains(morningKey)) {

            toast(
                "Morning already marked today"
            )

            return
        }


        // -----------------------------------------------------
        // Do not add Morning after Evening-only record
        // -----------------------------------------------------

        if (prefs.contains(eveningKey)) {

            toast(
                "Evening is already marked. " +
                        "Morning time cannot be added automatically."
            )

            return
        }


        val morning =
            System.currentTimeMillis()


        prefs.edit()

            .putLong(
                morningKey,
                morning
            )

            .apply()


        // -----------------------------------------------------
        // 7-hour notification
        // -----------------------------------------------------

        scheduleAlarm(
            morning
        )


        toast(

            "Morning marked at " +
                    formatTime(morning) +

                    "\n\n" +

                    "7 hours complete at " +

                    formatTime(
                        morning +
                                SEVEN_HOURS
                    )
        )


        updateStatus()
    }


    // =========================================================
    // MARK EVENING
    // =========================================================

    private fun markEvening() {

        val date =
            today()


        val eveningKey =
            EVENING_PREFIX + date


        if (!eveningAllowed()) {

            toast(
                "Evening time must be between 12:15 PM and 06:00 PM"
            )

            return
        }


        // -----------------------------------------------------
        // IMPORTANT
        //
        // Evening can be marked many times.
        // Latest time is always retained.
        // -----------------------------------------------------

        val evening =
            System.currentTimeMillis()


        prefs.edit()

            .putLong(
                eveningKey,
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
        // MORNING EXISTS
        // =====================================================

        if (morning != 0L) {

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

                        "Total : " +
                        formatDuration(
                            duration
                        ) +

                        "\n\n" +

                        if (
                            duration >=
                            SEVEN_HOURS
                        ) {

                            "7 hours completed."

                        } else {

                            "7 hours not completed.\n\n" +

                                    "Remaining : " +

                                    formatDuration(
                                        SEVEN_HOURS -
                                                duration
                                    )
                        }


            showMessage(
                "Evening Marked",
                message
            )
        }


        // =====================================================
        // MORNING MISSING
        // =====================================================

        else {

            showMessage(

                "Evening Marked",

                "Evening : " +
                        formatTime(evening) +

                        "\n\n" +

                        "Morning : Not recorded" +

                        "\n\n" +

                        "Status : Morning missing" +

                        "\n\n" +

                        "Total hours cannot be calculated."
            )
        }


        updateStatus()
    }


    // =========================================================
    // UPDATE UI
    // =========================================================

    private fun updateStatus() {

        val date =
            today()


        dateText.text =
            date


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
        // NOTHING
        // =====================================================

        if (
            morning == 0L &&
            evening == 0L
        ) {

            morningStatusText.text =
                "Not marked"


            eveningStatusText.text =
                "Not marked"


            statusText.text =
                "No attendance recorded today."

            return
        }


        // =====================================================
        // EVENING ONLY
        // =====================================================

        if (
            morning == 0L &&
            evening != 0L
        ) {

            morningStatusText.text =
                "Not recorded"


            eveningStatusText.text =
                formatTime(evening) +
                        "\n✓ Recorded"


            statusText.text =

                "Morning : Not recorded\n\n" +

                        "Evening : " +
                        formatTime(evening) +

                        "\n\n" +

                        "Status : Morning missing"


            return
        }


        // =====================================================
        // MORNING ONLY
        // =====================================================

        if (
            morning != 0L &&
            evening == 0L
        ) {

            morningStatusText.text =
                formatTime(morning) +
                        "\n✓ Recorded"


            eveningStatusText.text =
                "Not marked"


            val elapsed =
                max(
                    0L,
                    System.currentTimeMillis() -
                            morning
                )


            val remaining =
                SEVEN_HOURS -
                        elapsed


            statusText.text =

                if (
                    remaining > 0L
                ) {

                    "Elapsed : " +
                            formatDuration(
                                elapsed
                            ) +

                            "\n\n" +

                            "Remaining : " +
                            formatDuration(
                                remaining
                            ) +

                            "\n\n" +

                            "Complete at : " +
                            formatTime(
                                morning +
                                        SEVEN_HOURS
                            )

                } else {

                    "✓ 7 HOURS COMPLETED"
                }


            return
        }


        // =====================================================
        // MORNING + EVENING
        // =====================================================

        morningStatusText.text =
            formatTime(morning) +
                    "\n✓ Recorded"


        eveningStatusText.text =
            formatTime(evening) +
                    "\n✓ Recorded"


        val duration =
            max(
                0L,
                evening - morning
            )


        statusText.text =

            "Morning : " +
                    formatTime(morning) +

                    "\n\n" +

                    "Evening : " +
                    formatTime(evening) +

                    "\n\n" +

                    "Total : " +
                    formatDuration(
                        duration
                    ) +

                    "\n\n" +

                    if (
                        duration >=
                        SEVEN_HOURS
                    ) {

                        "✓ 7 HOURS COMPLETED"

                    } else {

                        "7 hours NOT completed\n\n" +

                                "Remaining : " +

                                formatDuration(
                                    SEVEN_HOURS -
                                            duration
                                )
                    }
    }


    // =========================================================
    // 45 DAY HISTORY
    // =========================================================

    private fun showHistory() {

        val history =
            StringBuilder()


        for (
            i in 0 until 45
        ) {

            val date =
                dateDaysAgo(i)


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
            ).append("\n")


            if (
                morning == 0L &&
                evening == 0L
            ) {

                history.append(
                    "Status : Not attended\n"
                )

            } else if (
                morning == 0L &&
                evening != 0L
            ) {

                history.append(
                    "Morning : Not recorded\n"
                )

                history.append(
                    "Evening : " +
                            formatTime(evening) +
                            "\n"
                )

                history.append(
                    "Status : Morning missing\n"
                )

            } else if (
                morning != 0L &&
                evening == 0L
            ) {

                history.append(
                    "Morning : " +
                            formatTime(morning) +
                            "\n"
                )

                history.append(
                    "Evening : Not marked\n"
                )

                history.append(
                    "Status : Incomplete\n"
                )

            } else {

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

                            if (
                                duration >=
                                SEVEN_HOURS
                            ) {

                                "7 hours completed"

                            } else {

                                "Less than 7 hours"
                            } +

                            "\n"
                )
            }


            history.append(
                "\n"
            )
        }


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
            today()


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


        if (
            morning == 0L &&
            evening == 0L
        ) {

            toast(
                "No attendance recorded today"
            )

            return
        }


        cancelAlarm()


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
            "Today's attendance cleared"
        )
    }


    // =========================================================
    // MENU
    // =========================================================

    private fun showMenu() {

        val items =
            arrayOf(
                "Today's Status",
                "45 Day History",
                "Clear Today",
                "About"
            )


        AlertDialog.Builder(this)

            .setTitle(
                "BAMA Attendance"
            )

            .setItems(
                items
            ) { _, which ->

                when (which) {

                    0 -> {

                        updateStatus()
                    }

                    1 -> {

                        showHistory()
                    }

                    2 -> {

                        confirmClear()
                    }

                    3 -> {

                        showAbout()
                    }
                }
            }

            .show()
    }


    // =========================================================
    // CONFIRM CLEAR
    // =========================================================

    private fun confirmClear() {

        AlertDialog.Builder(this)

            .setTitle(
                "Clear Today's Attendance?"
            )

            .setMessage(
                "Morning and Evening records for today will be deleted."
            )

            .setNegativeButton(
                "Cancel",
                null
            )

            .setPositiveButton(
                "Clear"
            ) { _, _ ->

                clearToday()
            }

            .show()
    }


    // =========================================================
    // ABOUT
    // =========================================================

    private fun showAbout() {

        showMessage(

            "BAMA Attendance",

            "Personal attendance timing app.\n\n" +

                    "Morning: first marking is retained.\n\n" +

                    "Evening: latest marking is retained.\n\n" +

                    "7-hour notification and 45-day history."
        )
    }


    // =========================================================
    // RESCHEDULE ALARM
    // =========================================================

    private fun scheduleSavedAlarm() {

        val date =
            today()


        val morning =
            prefs.getLong(
                MORNING_PREFIX + date,
                0L
            )


        if (
            morning == 0L
        ) {
            return
        }


        if (
            prefs.contains(
                EVENING_PREFIX + date
            )
        ) {
            return
        }


        if (
            System.currentTimeMillis() >=
            morning + SEVEN_HOURS
        ) {
            return
        }


        if (
            Build.VERSION.SDK_INT >= 31
        ) {

            val manager =
                getSystemService(
                    ALARM_SERVICE
                ) as AlarmManager


            if (
                !manager.canScheduleExactAlarms()
            ) {
                return
            }
        }


        scheduleAlarm(
            morning,
            false
        )
    }


    // =========================================================
    // SCHEDULE ALARM
    // =========================================================

    private fun scheduleAlarm(
        morning: Long,
        askPermission: Boolean = true
    ) {

        val manager =
            getSystemService(
                ALARM_SERVICE
            ) as AlarmManager


        if (
            Build.VERSION.SDK_INT >= 31 &&
            !manager.canScheduleExactAlarms()
        ) {

            if (askPermission) {

                toast(
                    "Allow 'Alarms & reminders' for the 7-hour notification"
                )


                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    )
                )
            }


            return
        }


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


        manager.setExactAndAllowWhileIdle(

            AlarmManager.RTC_WAKEUP,

            morning + SEVEN_HOURS,

            pendingIntent
        )
    }


    // =========================================================
    // CANCEL ALARM
    // =========================================================

    private fun cancelAlarm() {

        val manager =
            getSystemService(
                ALARM_SERVICE
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


        manager.cancel(
            pendingIntent
        )
    }


    // =========================================================
    // NOTIFICATION CHANNEL
    // =========================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >= 26
        ) {

            val channel =
                NotificationChannel(

                    CHANNEL_ID,

                    "Attendance",

                    NotificationManager
                        .IMPORTANCE_HIGH
                )


            channel.description =
                "7-hour attendance reminder"


            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(
                channel
            )
        }
    }


    // =========================================================
    // NOTIFICATION PERMISSION
    // =========================================================

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >= 33 &&

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

            .setTitle(
                title
            )

            .setMessage(
                message
            )

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

                .setAutoCancel(
                    true
                )

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