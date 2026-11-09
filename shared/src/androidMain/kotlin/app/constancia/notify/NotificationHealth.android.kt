package app.constancia.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

private const val TEST_NOTIFICATION_ID = 900103

actual fun notificationsGranted(): Boolean {
    val context = NotificationPlatform.appContext ?: return false
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
        as? NotificationManager ?: return false
    return nm.areNotificationsEnabled()
}

actual fun exactAlarmsGranted(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val context = NotificationPlatform.appContext ?: return true
    val am = context.getSystemService(Context.ALARM_SERVICE)
        as? AlarmManager ?: return true
    return am.canScheduleExactAlarms()
}

actual fun batteryOptimizationExempt(): Boolean {
    val context = NotificationPlatform.appContext ?: return true
    val pm = context.getSystemService(Context.POWER_SERVICE)
        as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

actual fun openBatteryOptimizationSettings() {
    val context = NotificationPlatform.appContext ?: return
    runCatching {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            val intent = Intent(
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

actual fun openNotificationSettings() {
    val context = NotificationPlatform.appContext ?: return
    runCatching {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

actual fun openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = NotificationPlatform.appContext ?: return
    runCatching {
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

actual fun postTestNotification() {
    val context = NotificationPlatform.appContext ?: return
    postNotification(
        context,
        TEST_NOTIFICATION_ID,
        "Teste do Constância",
        "Suas notificações estão funcionando — é assim que os seus " +
            "lembretes vão aparecer.",
    )
}
