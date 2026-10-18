package app.constancia

import androidx.compose.ui.window.ComposeUIViewController
import app.constancia.data.DatabaseDriverFactory

fun MainViewController() = ComposeUIViewController {
    App(DatabaseDriverFactory())
}
