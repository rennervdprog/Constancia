package app.constancia

import androidx.compose.ui.window.ComposeUIViewController
import app.constancia.data.DatabaseDriverFactory
import app.constancia.notify.runMissedReminderCatchUpIos

fun MainViewController() = ComposeUIViewController {
    // R6.3 (Fase C): catch-up de lembretes perdidos na abertura do app
    // (o iOS não tem boot receiver como o Android).
    runMissedReminderCatchUpIos()
    App(DatabaseDriverFactory())
}
