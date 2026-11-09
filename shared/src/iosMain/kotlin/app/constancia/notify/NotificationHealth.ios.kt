package app.constancia.notify

import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS: a autorização é pedida pelo próprio agendador no primeiro
 * lembrete (requestAuthorization) e o sistema não expõe a leitura
 * síncrona do estado — o cartão de saúde trata o iOS como liberado e
 * os botões de configurações são no-op nesta plataforma (a frente iOS
 * é posterior; o Android é onde o bloqueio silencioso acontecia).
 */
actual fun notificationsGranted(): Boolean = true

actual fun exactAlarmsGranted(): Boolean = true

// iOS não tem o conceito de otimização de bateria por app: isento.
actual fun batteryOptimizationExempt(): Boolean = true

actual fun openBatteryOptimizationSettings() = Unit

actual fun openNotificationSettings() = Unit

actual fun openExactAlarmSettings() = Unit

actual fun postTestNotification() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val content = UNMutableNotificationContent()
    content.setTitle("Teste do Constância")
    content.setBody(
        "Suas notificações estão funcionando — é assim que os seus " +
            "lembretes vão aparecer.",
    )
    content.setSound(UNNotificationSound.defaultSound)
    val trigger = UNTimeIntervalNotificationTrigger
        .triggerWithTimeInterval(1.0, repeats = false)
    val request = UNNotificationRequest.requestWithIdentifier(
        "constancia-test", content, trigger,
    )
    center.addNotificationRequest(request, null)
}
