package com.example.data.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.database.entities.StudyTaskEntity
import com.example.data.preferences.UserPreferences
import com.example.ui.localization.LocalizationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    // Reschedule all uncompleted task reminders after reboot or package replace
                    val db = AppDatabase.getDatabase(context)
                    val uncompletedTasks = db.studyTaskDao().getAllTasksSync().filter { !it.isCompleted }
                    val prefs = UserPreferences(context)
                    val lang = prefs.appLanguage.firstOrNull() ?: "en"
                    for (task in uncompletedTasks) {
                        StudyNotificationHelper.scheduleTaskReminder(context, task, lang)
                    }
                    return@launch
                }

                val taskId = intent.getLongExtra("task_id", 0L)
                val isOverdue = intent.getBooleanExtra("is_overdue", false)
                val fallbackTitle = intent.getStringExtra("task_title") ?: "Study Task"
                val fallbackSubject = intent.getStringExtra("task_subject") ?: "General"
                val initialLang = intent.getStringExtra("language_code") ?: "en"

                val db = AppDatabase.getDatabase(context)
                val task = if (taskId > 0L) db.studyTaskDao().getTaskById(taskId) else null

                // A completed task must NEVER send an unfinished or scheduled reminder notification
                if (task != null && task.isCompleted) {
                    StudyNotificationHelper.cancelTaskReminder(context, taskId)
                    return@launch
                }

                // If task was deleted from db, cancel all reminders and do not notify
                if (taskId > 0L && task == null) {
                    StudyNotificationHelper.cancelTaskReminder(context, taskId)
                    return@launch
                }

                val effectiveTitle = task?.title ?: fallbackTitle
                val effectiveSubject = task?.subject ?: fallbackSubject

                val prefs = UserPreferences(context)
                val effectiveLang = prefs.appLanguage.firstOrNull() ?: initialLang

                StudyNotificationHelper.sendNotification(
                    context = context,
                    taskId = taskId,
                    subject = effectiveSubject,
                    title = effectiveTitle,
                    isOverdue = isOverdue,
                    languageCode = effectiveLang
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object StudyNotificationHelper {
    const val CHANNEL_ID = "study_timetable_reminders_channel"
    private const val CHANNEL_NAME = "Study Timetable Reminders"
    private const val CHANNEL_DESC = "Reminders for scheduled, upcoming and unfinished study tasks"

    const val ACTION_STUDY_TASK_REMINDER = "com.example.action.STUDY_TASK_REMINDER"
    const val ACTION_STUDY_TASK_OVERDUE = "com.example.action.STUDY_TASK_OVERDUE"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 200, 350)
                enableLights(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendNotification(
        context: Context,
        taskId: Long,
        subject: String,
        title: String,
        isOverdue: Boolean,
        languageCode: String
    ) {
        createNotificationChannel(context)

        if (!hasNotificationPermission(context)) return

        val strings = LocalizationHelper.getStrings(languageCode)
        val notifTitle = strings.reminderNotificationTitle
        val notifBody = if (isOverdue) {
            "${strings.reminderNotificationOverdue} $title ($subject)"
        } else {
            "${strings.reminderNotificationTime} $title ($subject)"
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "planner")
        }
        val notifRequestCode = (taskId % 50000).toInt() + (if (isOverdue) 60000 else 1000)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifRequestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notifTitle)
            .setContentText(notifBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notifRequestCode, builder.build())
        } catch (e: SecurityException) {
            // Handled safely
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun scheduleTaskReminder(
        context: Context,
        task: StudyTaskEntity,
        languageCode: String
    ) {
        if (task.isCompleted) {
            cancelTaskReminder(context, task.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val scheduledTimeMs = parseDateTimeToMillis(task.dueDate, task.dueTime)
        val now = System.currentTimeMillis()

        val mainRequestCode = (task.id % 50000).toInt() + 1000

        // Cancel any existing alarms for this task first to prevent duplicate notifications
        cancelTaskReminder(context, task.id)

        // Exactly 5 minutes after the task's scheduled/fixed time:
        val reminderTriggerTimeMs = scheduledTimeMs + (5 * 60 * 1000L)

        // If the 5-minute notification time is in the future, schedule the single notification
        if (reminderTriggerTimeMs > now) {
            val mainIntent = Intent(context, StudyReminderReceiver::class.java).apply {
                action = ACTION_STUDY_TASK_REMINDER
                putExtra("task_id", task.id)
                putExtra("task_title", task.title)
                putExtra("task_subject", task.subject)
                putExtra("language_code", languageCode)
                putExtra("is_overdue", true)
            }
            val mainPendingIntent = PendingIntent.getBroadcast(
                context,
                mainRequestCode,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarmCompat(alarmManager, reminderTriggerTimeMs, mainPendingIntent)
        }
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        if (taskId <= 0L) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val mainRequestCode = (taskId % 50000).toInt() + 1000
        val overdueRequestCode = (taskId % 50000).toInt() + 60000

        try {
            if (alarmManager != null) {
                val mainIntent = Intent(context, StudyReminderReceiver::class.java).apply {
                    action = ACTION_STUDY_TASK_REMINDER
                }
                val mainPendingIntent = PendingIntent.getBroadcast(
                    context,
                    mainRequestCode,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(mainPendingIntent)

                val overdueIntent = Intent(context, StudyReminderReceiver::class.java).apply {
                    action = ACTION_STUDY_TASK_OVERDUE
                }
                val overduePendingIntent = PendingIntent.getBroadcast(
                    context,
                    overdueRequestCode,
                    overdueIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(overduePendingIntent)
            }

            // Immediately dismiss any active notifications in the status bar for this task
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(mainRequestCode)
            notificationManager.cancel(overdueRequestCode)
        } catch (_: Throwable) {}
    }

    fun checkAndNotifyUnfinishedTasks(
        context: Context,
        tasks: List<StudyTaskEntity>,
        languageCode: String
    ) {
        // No-op: Notifications are scheduled strictly via AlarmManager at the user-specified time
    }

    private fun setAlarmCompat(alarmManager: AlarmManager, triggerTimeMs: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    private fun isTodayDate(dateStr: String): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return dateStr.trim() == today
    }

    fun parseDateTimeToMillis(dateStr: String, timeStr: String): Long {
        val cleanDate = dateStr.trim().ifBlank {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
        val cleanTime = timeStr.trim().ifBlank { "09:00 AM" }

        val normalizedTime = cleanTime
            .replace("am", "AM", ignoreCase = true)
            .replace("pm", "PM", ignoreCase = true)
            .replace("\\s+".toRegex(), " ")

        val fullStr = "$cleanDate $normalizedTime"

        val patterns = listOf(
            "yyyy-MM-dd hh:mm a",
            "yyyy-MM-dd h:mm a",
            "yyyy-MM-dd hh:mma",
            "yyyy-MM-dd h:mma",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd H:mm",
            "yyyy-MM-dd"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = true
                val date = sdf.parse(fullStr)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }

        // Fallback: parse date alone and default to 9:00 AM
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(cleanDate)
            if (date != null) {
                val cal = Calendar.getInstance()
                cal.time = date
                cal.set(Calendar.HOUR_OF_DAY, 9)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            } else 0L
        } catch (_: Exception) {
            0L
        }
    }
}
