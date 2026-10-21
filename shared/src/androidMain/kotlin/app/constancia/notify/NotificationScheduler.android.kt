package app.constancia.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.constancia.data.local.PrefsRepositoryImpl
import app.constancia.db.ConstanciaDb
import app.constancia.domain.asTaskItem
import app.constancia.domain.dailyReviewSummary
import app.constancia.domain.dailyReviewText
import app.constancia.domain.orphanOccurrences
import app.constancia.domain.reminderInstantMillis
import app.constancia.model.todayYmd
import app.constancia.model.toLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val CHANNEL_ID = "constancia_reminders"
private const val DAILY_REVIEW_REQUEST = 900101
private const val ACTION_TASK_REMINDER = "app.constancia.TASK_REMINDER"
private const val ACTION_DAILY_REVIEW = "app.constancia.DAILY_REVIEW"

/** Contexto da aplicação para a camada de notificações (padrão do
 *  initShareContext): preenchido pela MainActivity e pelos receivers. */
object NotificationPlatform {
    @Volatile
    var appContext: Context? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

/** Chamado uma vez pela MainActivity. */
fun initNotificationContext(context: Context) {
    NotificationPlatform.init(context)
}

actual class NotificationScheduler {

    actual fun scheduleTaskReminder(
        taskId: String,
        title: String,
        triggerAtMillis: Long,
    ) {
        val context = NotificationPlatform.appContext ?: return
        val am = alarmManager(context) ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReminderReceiver.EXTRA_TITLE, title)
        }
        val pending = PendingIntent.getBroadcast(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExactOrFallback(am, triggerAtMillis, pending)
    }

    actual fun cancelTaskReminder(taskId: String) {
        val context = NotificationPlatform.appContext ?: return
        val am = alarmManager(context) ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
        }
        val pending = PendingIntent.getBroadcast(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pending)
        pending.cancel()
    }

    actual fun scheduleDailyReview(hour: Int, minute: Int) {
        val context = NotificationPlatform.appContext ?: return
        val am = alarmManager(context) ?: return
        val pending = dailyReviewPending(context)
        val first = nextDailyTriggerMillis(hour, minute)
        // Repetição diária inexata basta para a revisão (e dispensa a
        // permissão de alarme exato); o horário pode derivar minutos.
        runCatching {
            am.setRepeating(
                AlarmManager.RTC_WAKEUP, first, AlarmManager.INTERVAL_DAY, pending,
            )
        }
    }

    actual fun cancelDailyReview() {
        val context = NotificationPlatform.appContext ?: return
        val am = alarmManager(context) ?: return
        val pending = dailyReviewPending(context)
        am.cancel(pending)
        pending.cancel()
    }

    private fun dailyReviewPending(context: Context): PendingIntent {
        val intent = Intent(context, DailyReviewReceiver::class.java).apply {
            action = ACTION_DAILY_REVIEW
        }
        return PendingIntent.getBroadcast(
            context, DAILY_REVIEW_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun alarmManager(context: Context): AlarmManager? =
    context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

/** Exato quando permitido (Android 12+ exige concessão); senão cai
 *  para o alarme inexato com Doze-aware, sem quebrar o fluxo. */
private fun scheduleExactOrFallback(
    am: AlarmManager,
    triggerAtMillis: Long,
    pending: PendingIntent,
) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !am.canScheduleExactAlarms()
        ) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pending,
            )
        }
    } catch (e: SecurityException) {
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }
}

/** Próximo instante (epoch millis) de um horário diário HH:mm. */
private fun nextDailyTriggerMillis(hour: Int, minute: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val todayLocal = now.toLocalDateTime(tz).date
    fun at(date: kotlinx.datetime.LocalDate): Long =
        kotlinx.datetime.LocalDateTime(date, LocalTime(hour, minute))
            .toInstant(tz)
            .toEpochMilliseconds()
    val candidate = at(todayLocal)
    return if (candidate <= now.toEpochMilliseconds()) {
        at(todayLocal.plus(kotlinx.datetime.DatePeriod(days = 1)))
    } else {
        candidate
    }
}

/** Monta e posta uma notificação local abrindo o app ao tocar. */
internal fun postNotification(
    context: Context,
    id: Int,
    title: String,
    body: String,
) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
        as? NotificationManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Lembretes e revisão do dia",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
    val launch = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    val contentIntent = launch?.let {
        PendingIntent.getActivity(
            context, id, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    @Suppress("DEPRECATION")
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, CHANNEL_ID)
    } else {
        Notification.Builder(context)
    }
    val notification = builder
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()
    try {
        nm.notify(id, notification)
    } catch (e: SecurityException) {
        // Sem a permissão de notificações (Android 13+), nada a fazer.
    }
}

/** Receiver do lembrete de tarefa: só posta a notificação. */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationPlatform.init(context)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        postNotification(
            context,
            taskId.hashCode(),
            "Lembrete de tarefa",
            title,
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
    }
}

/** Receiver da revisão diária: calcula o resumo do dia no banco local
 *  na hora do disparo (nada de texto congelado do dia anterior). */
class DailyReviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationPlatform.init(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val driver = AndroidSqliteDriver(
                    ConstanciaDb.Schema, context, "constancia.db",
                )
                try {
                    val db = ConstanciaDb(driver)
                    val rows = db.constanciaDbQueries.selectAllTasks()
                        .executeAsList()
                    val tasks = rows.map { row ->
                        app.constancia.model.TaskItem(
                            id = row.id,
                            title = row.title,
                            dueTime = row.due_time,
                            done = row.done == 1L,
                            date = row.date,
                            dueDate = row.due_date,
                            completedAt = row.completed_at,
                        )
                    }
                    // Fase B (R1.1): conclusões preservadas no histórico
                    // de ocorrências também entram no resumo do dia.
                    val history = db.constanciaDbQueries
                        .selectOccurrenceHistory().executeAsList()
                        .map { row ->
                            app.constancia.model.TaskOccurrence(
                                id = row.id,
                                sourceTaskId = row.source_task_id,
                                occurrenceDueDate = row.occurrence_due_date,
                                title = row.title,
                                priority = row.priority.toInt(),
                                deadlineDate = row.deadline_date,
                                durationMinutes = row.duration_minutes
                                    ?.toInt(),
                                completedAt = row.completed_at,
                            )
                        }
                    val summaryTasks = tasks +
                        orphanOccurrences(tasks, history)
                            .map { it.asTaskItem() }
                    val summary = dailyReviewSummary(
                        summaryTasks, todayYmd().toLocalDate(),
                    )
                    val (title, body) = dailyReviewText(summary)
                    postNotification(context, DAILY_REVIEW_REQUEST, title, body)
                } finally {
                    driver.close()
                }
            } catch (t: Throwable) {
                // Resumo indisponível: a revisão simplesmente não dispara.
            } finally {
                pending.finish()
            }
        }
    }
}

/** Após reboot (ou reabertura do app), reagenda a revisão diária e os
 *  lembretes de tarefas pendentes — alarmes não sobrevivem ao reboot. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationPlatform.init(context)
        rescheduleAllNotifications(context)
    }
}

/**
 * Reagenda tudo a partir do estado salvo (prefs + banco). Chamado no
 * boot e na abertura do app, para o agendamento ser auto-curável.
 */
fun rescheduleAllNotifications(context: Context) {
    NotificationPlatform.init(context)
    CoroutineScope(Dispatchers.Default).launch {
        runCatching {
            val prefs = PrefsRepositoryImpl()
            if (prefs.dailyReviewEnabledFlow().first()) {
                val time = prefs.dailyReviewTimeFlow().first()
                val parts = time.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                NotificationScheduler().scheduleDailyReview(hour, minute)
            }
            val driver = AndroidSqliteDriver(
                ConstanciaDb.Schema, context, "constancia.db",
            )
            try {
                val db = ConstanciaDb(driver)
                val now = Clock.System.now().toEpochMilliseconds()
                val scheduler = NotificationScheduler()
                db.constanciaDbQueries.selectAllTasks().executeAsList()
                    .forEach { row ->
                        if (row.done == 1L) return@forEach
                        val task = app.constancia.model.TaskItem(
                            id = row.id,
                            title = row.title,
                            dueTime = row.due_time,
                            done = false,
                            date = row.date,
                            dueDate = row.due_date,
                            completedAt = row.completed_at,
                            reminderMinutesBefore =
                                row.reminder_minutes_before?.toInt(),
                        )
                        val trigger = reminderInstantMillis(task)
                        if (trigger != null && trigger > now) {
                            scheduler.scheduleTaskReminder(row.id, row.title, trigger)
                        }
                    }
            } finally {
                driver.close()
            }
        }
    }
}
