package app.constancia.notify

import platform.Foundation.NSDateComponents
import platform.Foundation.NSUserDefaults
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import app.constancia.data.DatabaseDriverFactory
import app.constancia.data.appDatabase
import app.constancia.domain.missedReminders
import app.constancia.model.ReminderType
import app.constancia.model.TaskItem
import app.constancia.model.TaskReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * iOS: UNUserNotificationCenter. Fase C: cada lembrete (de tarefa ou de
 * hábito) tem o próprio request, identificado pelo id da linha — como
 * no Android. Diferença conhecida em relação ao Android: o corpo da
 * revisão diária é fixo (o iOS não executa código nosso na hora do
 * disparo para montar o resumo a partir do banco); os lembretes
 * carregam o título/nome normalmente.
 */
actual class NotificationScheduler {

    actual fun scheduleTaskReminder(
        reminderId: String,
        title: String,
        triggerAtMillis: Long,
    ) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        requestAuthorization(center)
        val content = UNMutableNotificationContent()
        content.setTitle("Lembrete de tarefa")
        content.setBody(title)
        content.setSound(UNNotificationSound.defaultSound)
        // NSDate(timeIntervalSince1970=) não existe nestes bindings:
        // monta os componentes a partir do instante via kotlinx-datetime.
        val ldt = Instant.fromEpochMilliseconds(triggerAtMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val components = NSDateComponents()
        components.setYear(ldt.year.toLong())
        components.setMonth(ldt.monthNumber.toLong())
        components.setDay(ldt.dayOfMonth.toLong())
        components.setHour(ldt.hour.toLong())
        components.setMinute(ldt.minute.toLong())
        val trigger = UNCalendarNotificationTrigger
            .triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = taskIdentifier(reminderId),
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, null)
    }

    actual fun cancelTaskReminder(reminderId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(
                listOf(taskIdentifier(reminderId)),
            )
    }

    actual fun scheduleHabitReminder(
        reminderId: String,
        habitName: String,
        hour: Int,
        minute: Int,
    ) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        requestAuthorization(center)
        val content = UNMutableNotificationContent()
        content.setTitle("Hora do hábito")
        content.setBody(habitName)
        content.setSound(UNNotificationSound.defaultSound)
        val components = NSDateComponents()
        components.setHour(hour.toLong())
        components.setMinute(minute.toLong())
        val trigger = UNCalendarNotificationTrigger
            .triggerWithDateMatchingComponents(components, repeats = true)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = habitIdentifier(reminderId),
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, null)
    }

    actual fun cancelHabitReminder(reminderId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(
                listOf(habitIdentifier(reminderId)),
            )
    }

    actual fun notifyMissedReminders(count: Int) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        requestAuthorization(center)
        val content = UNMutableNotificationContent()
        content.setTitle(
            if (count == 1) {
                "Você perdeu 1 lembrete"
            } else {
                "Você perdeu $count lembretes"
            },
        )
        content.setBody(
            "Alguns lembretes não dispararam. Abra o Constância " +
                "para rever suas tarefas.",
        )
        content.setSound(UNNotificationSound.defaultSound)
        val trigger = UNTimeIntervalNotificationTrigger
            .triggerWithTimeInterval(1.0, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = MISSED_ID,
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, null)
    }

    actual fun scheduleDailyReview(hour: Int, minute: Int) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        requestAuthorization(center)
        val content = UNMutableNotificationContent()
        content.setTitle("Seu dia no Constância")
        content.setBody("Abra o app e veja como está o seu dia.")
        content.setSound(UNNotificationSound.defaultSound)
        val components = NSDateComponents()
        components.setHour(hour.toLong())
        components.setMinute(minute.toLong())
        val trigger = UNCalendarNotificationTrigger
            .triggerWithDateMatchingComponents(components, repeats = true)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = DAILY_REVIEW_ID,
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, null)
    }

    actual fun cancelDailyReview() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(
                listOf(DAILY_REVIEW_ID),
            )
    }

    private fun requestAuthorization(center: UNUserNotificationCenter) {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }

    private fun taskIdentifier(reminderId: String): String =
        "task-reminder-$reminderId"

    private fun habitIdentifier(reminderId: String): String =
        "habit-reminder-$reminderId"

    private companion object {
        const val DAILY_REVIEW_ID = "daily-review"
        const val MISSED_ID = "missed-summary"
    }
}

private const val KEY_MISSED_LAST = "missed_summary_last_instant"

/**
 * R6.3 no iOS — equivalente ao catch-up do boot/abertura do Android:
 * chamado na abertura do app (MainViewController), lê o banco local e,
 * se houve lembrete de tarefa pendente vencido há menos de 24 h, posta
 * o resumo uma única vez por lote (o maior instante fica guardado no
 * NSUserDefaults, como no SharedPreferences do Android).
 */
fun runMissedReminderCatchUpIos() {
    CoroutineScope(Dispatchers.Default).launch {
        runCatching {
            val db = appDatabase(DatabaseDriverFactory())
            val q = db.constanciaDbQueries
            val tasks = q.selectAllTasks().executeAsList().map { row ->
                TaskItem(
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
            val now = Clock.System.now().toEpochMilliseconds()
            val missed = missedReminders(tasks, reminders, now)
            if (missed.isEmpty()) return@runCatching
            val latest = missed.maxOf { it.instantMillis }
            val defaults = NSUserDefaults.standardUserDefaults
            val already = defaults.doubleForKey(KEY_MISSED_LAST).toLong()
            if (latest > already) {
                NotificationScheduler().notifyMissedReminders(missed.size)
                defaults.setDouble(latest.toDouble(), KEY_MISSED_LAST)
            }
        }
    }
}
