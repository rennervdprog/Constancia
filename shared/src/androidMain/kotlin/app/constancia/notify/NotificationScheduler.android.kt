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
import app.constancia.domain.missedReminders
import app.constancia.domain.orphanOccurrences
import app.constancia.domain.taskReminderInstantMillis
import app.constancia.model.ReminderType
import app.constancia.model.TaskReminder
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

// R12.4 (Fase C): canais SEPARADOS — lembretes (tarefas e hábitos) de um
// lado, revisão diária do outro; o usuário pode silenciar um sem o outro.
private const val REMINDERS_CHANNEL_ID = "constancia_reminders"
private const val DAILY_REVIEW_CHANNEL_ID = "constancia_daily_review"
private const val DAILY_REVIEW_REQUEST = 900101
private const val MISSED_SUMMARY_ID = 900102
private const val ACTION_TASK_REMINDER = "app.constancia.TASK_REMINDER"
private const val ACTION_HABIT_REMINDER = "app.constancia.HABIT_REMINDER"
private const val ACTION_DAILY_REVIEW = "app.constancia.DAILY_REVIEW"
private const val NOTIFY_PREFS = "constancia_notify_prefs"
private const val KEY_MISSED_SUMMARY_LAST = "missed_summary_last_instant"

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
        reminderId: String,
        title: String,
        triggerAtMillis: Long,
    ) {
        val context = NotificationPlatform.appContext ?: return
        val am = alarmManager(context) ?: return
        val pending = taskReminderPending(context, reminderId, title)
        scheduleExactOrFallback(am, triggerAtMillis, pending)
    }

    actual fun cancelTaskReminder(reminderId: String) {
        val context = NotificationPlatform.appContext ?: return
        val am = alarmManager(context) ?: return
        val pending = taskReminderPending(context, reminderId, null)
        am.cancel(pending)
        pending.cancel()
    }

    actual fun scheduleHabitReminder(
        reminderId: String,
        habitName: String,
        hour: Int,
        minute: Int,
    ) {
        val context = NotificationPlatform.appContext ?: return
        val am = alarmManager(context) ?: return
        val pending = habitReminderPending(context, reminderId, habitName)
        // Diário inexato basta para o hábito (como a revisão): o horário
        // pode derivar alguns minutos e dispensa alarme exato por linha.
        runCatching {
            am.setRepeating(
                AlarmManager.RTC_WAKEUP,
                nextDailyTriggerMillis(hour, minute),
                AlarmManager.INTERVAL_DAY,
                pending,
            )
        }
    }

    actual fun cancelHabitReminder(reminderId: String) {
        val context = NotificationPlatform.appContext ?: return
        val am = alarmManager(context) ?: return
        val pending = habitReminderPending(context, reminderId, null)
        am.cancel(pending)
        pending.cancel()
    }

    actual fun notifyMissedReminders(count: Int) {
        val context = NotificationPlatform.appContext ?: return
        val title = if (count == 1) {
            "Você perdeu 1 lembrete"
        } else {
            "Você perdeu $count lembretes"
        }
        postNotification(
            context, MISSED_SUMMARY_ID, title,
            "Alguns lembretes não dispararam (o aparelho estava " +
                "desligado ou o app fechado). Abra o Constância para " +
                "rever suas tarefas.",
            REMINDERS_CHANNEL_ID,
        )
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

    private fun taskReminderPending(
        context: Context,
        reminderId: String,
        title: String?,
    ): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(TaskReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            if (title != null) {
                putExtra(TaskReminderReceiver.EXTRA_TITLE, title)
            }
        }
        return PendingIntent.getBroadcast(
            context, reminderId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun habitReminderPending(
        context: Context,
        reminderId: String,
        habitName: String?,
    ): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_HABIT_REMINDER
            putExtra(HabitReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            if (habitName != null) {
                putExtra(HabitReminderReceiver.EXTRA_HABIT_NAME, habitName)
            }
        }
        return PendingIntent.getBroadcast(
            context, reminderId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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

private fun ensureChannels(nm: NotificationManager) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    nm.createNotificationChannel(
        NotificationChannel(
            REMINDERS_CHANNEL_ID, "Lembretes",
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
    nm.createNotificationChannel(
        NotificationChannel(
            DAILY_REVIEW_CHANNEL_ID, "Revisão do dia",
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
}

/** Monta e posta uma notificação local abrindo o app ao tocar. */
internal fun postNotification(
    context: Context,
    id: Int,
    title: String,
    body: String,
    channelId: String = REMINDERS_CHANNEL_ID,
) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
        as? NotificationManager ?: return
    ensureChannels(nm)
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
        Notification.Builder(context, channelId)
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

/** Receiver de UM lembrete de tarefa: só posta a notificação. */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationPlatform.init(context)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        postNotification(
            context,
            reminderId.hashCode(),
            "Lembrete de tarefa",
            title,
            REMINDERS_CHANNEL_ID,
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
    }
}

/** Receiver de UM lembrete diário de hábito: só posta a notificação. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationPlatform.init(context)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val name = intent.getStringExtra(EXTRA_HABIT_NAME) ?: return
        postNotification(
            context,
            reminderId.hashCode(),
            "Hora do hábito",
            name,
            REMINDERS_CHANNEL_ID,
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_HABIT_NAME = "habit_name"
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
                    postNotification(
                        context, DAILY_REVIEW_REQUEST, title, body,
                        DAILY_REVIEW_CHANNEL_ID,
                    )
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

/** Após reboot (ou reabertura do app), reagenda a revisão diária, os
 *  lembretes de tarefas e os de hábitos — alarmes não sobrevivem ao
 *  reboot — e dispara o catch-up de lembretes perdidos (R6.3). */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationPlatform.init(context)
        rescheduleAllNotifications(context)
    }
}

/**
 * Reagenda tudo a partir do estado salvo (prefs + banco). Chamado no
 * boot e na abertura do app, para o agendamento ser auto-curável.
 * Fase C: agenda CADA lembrete da lista de cada tarefa, os lembretes
 * diários dos hábitos e, se houve lembrete vencido há menos de 24 h de
 * tarefa pendente, posta UMA notificação-resumo (sem repetir o mesmo
 * resumo a cada abertura: o maior instante resumido fica guardado).
 */
fun rescheduleAllNotifications(context: Context) {
    NotificationPlatform.init(context)
    CoroutineScope(Dispatchers.Default).launch {
        runCatching {
            val prefs = PrefsRepositoryImpl()
            val scheduler = NotificationScheduler()
            if (prefs.dailyReviewEnabledFlow().first()) {
                val time = prefs.dailyReviewTimeFlow().first()
                val parts = time.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                scheduler.scheduleDailyReview(hour, minute)
            }
            val driver = AndroidSqliteDriver(
                ConstanciaDb.Schema, context, "constancia.db",
            )
            try {
                val db = ConstanciaDb(driver)
                val q = db.constanciaDbQueries
                val now = Clock.System.now().toEpochMilliseconds()
                val tasks = q.selectAllTasks().executeAsList().map { row ->
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
                val reminders = q.selectAllTaskReminders().executeAsList()
                    .map { row ->
                        TaskReminder(
                            id = row.id,
                            taskId = row.task_id,
                            type = runCatching {
                                ReminderType.valueOf(row.type)
                            }.getOrDefault(ReminderType.RELATIVE),
                            minuteOffset = row.minute_offset?.toInt(),
                            atMillis = row.at_millis,
                            auto = row.auto == 1L,
                        )
                    }
                val titles = tasks.associate { it.id to it.title }
                // Agenda os lembretes futuros, um alarme por linha.
                reminders.forEach { reminder ->
                    val task = tasks.firstOrNull { it.id == reminder.taskId }
                        ?: return@forEach
                    val instant = taskReminderInstantMillis(task, reminder)
                    if (instant != null && instant > now) {
                        scheduler.scheduleTaskReminder(
                            reminder.id,
                            titles[reminder.taskId] ?: task.title,
                            instant,
                        )
                    }
                }
                // R6.3 — catch-up: UMA notificação-resumo pelos perdidos
                // das últimas 24 h (sem repetir o mesmo resumo depois).
                val missed = missedReminders(tasks, reminders, now)
                if (missed.isNotEmpty()) {
                    val latest = missed.maxOf { it.instantMillis }
                    val store = context.getSharedPreferences(
                        NOTIFY_PREFS, Context.MODE_PRIVATE,
                    )
                    val already = store.getLong(KEY_MISSED_SUMMARY_LAST, 0L)
                    if (latest > already) {
                        scheduler.notifyMissedReminders(missed.size)
                        store.edit()
                            .putLong(KEY_MISSED_SUMMARY_LAST, latest)
                            .apply()
                    }
                }
                // Lembretes diários dos hábitos (hábito pausado não apita).
                val habits = q.selectHabits().executeAsList()
                    .associateBy { it.id }
                q.selectAllHabitReminders().executeAsList()
                    .forEach { row ->
                        val habit = habits[row.habit_id]
                            ?: return@forEach
                        if (habit.paused == 1L) return@forEach
                        val parts = row.time.split(":")
                        val hour = parts.getOrNull(0)?.toIntOrNull()
                            ?: return@forEach
                        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        scheduler.scheduleHabitReminder(
                            row.id, habit.name, hour, minute,
                        )
                    }
            } finally {
                driver.close()
            }
        }
    }
}
