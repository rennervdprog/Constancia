package app.constancia

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.constancia.billing.initBillingContext
import app.constancia.data.DatabaseDriverFactory
import app.constancia.export.initShareContext
import app.constancia.notify.initNotificationContext
import app.constancia.notify.rescheduleAllNotifications

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        initShareContext(applicationContext)
        initNotificationContext(applicationContext)
        // Fase F: contexto p/ o Google Play Billing (gateway sem
        // contexto até aqui ignora as chamadas silenciosamente).
        initBillingContext(applicationContext)
        // Reagenda lembretes/revisão (alarmes não sobrevivem a reboot
        // nem a atualizações do app) e pede a permissão de notificar.
        rescheduleAllNotifications(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        val driverFactory = DatabaseDriverFactory(applicationContext)
        setContent {
            App(driverFactory)
        }
    }
}
