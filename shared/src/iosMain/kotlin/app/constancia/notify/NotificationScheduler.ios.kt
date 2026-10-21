package app.constancia.notify

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS: UNUserNotificationCenter. Diferença conhecida em relação ao
 * Android: o corpo da revisão diária é fixo (o iOS não executa código
 * nosso na hora do disparo para montar o resumo a partir do banco);
 * os lembretes de tarefa carregam o título normalmente.
 */
actual class NotificationScheduler {

    actual fun scheduleTaskReminder(
        taskId: String,
        title: String,
        triggerAtMillis: Long,
    ) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        requestAuthorization(center)
        val content = UNMutableNotificationContent()
        content.setTitle("Lembrete de tarefa")
        content.setBody(title)
        content.setSound(UNNotificationSound.defaultSound())
        val date = NSDate(timeIntervalSince1970 = triggerAtMillis.toDouble() / 1000.0)
        val components = NSCalendar.currentCalendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute,
            date,
        )
        val trigger = UNCalendarNotificationTrigger
            .triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = taskIdentifier(taskId),
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, null)
    }

    actual fun cancelTaskReminder(taskId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(
                listOf(taskIdentifier(taskId)),
            )
    }

    actual fun scheduleDailyReview(hour: Int, minute: Int) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        requestAuthorization(center)
        val content = UNMutableNotificationContent()
        content.setTitle("Seu dia no Constância")
        content.setBody("Abra o app e veja como está o seu dia.")
        content.setSound(UNNotificationSound.defaultSound())
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

    private fun taskIdentifier(taskId: String): String = "task-$taskId"

    private companion object {
        const val DAILY_REVIEW_ID = "daily-review"
    }
}
