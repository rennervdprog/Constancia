package app.constancia.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/** Abas principais do app (espelha a bottom bar do protótipo v3). */
enum class AppTab(val label: String, val icon: ImageVector) {
    HOJE("Hoje", Icons.Filled.CheckCircle),
    RELATORIOS("Relatórios", Icons.Filled.BarChart),
    DIARIO("Diário", Icons.Filled.MenuBook),
    DESAFIOS("Desafios", Icons.Filled.EmojiEvents),
    MAIS("Mais", Icons.Filled.MoreHoriz),
}

class AppNavigator {
    var tab by mutableStateOf(AppTab.HOJE)
    var showPaywall by mutableStateOf(false)

    fun navigateTo(tab: AppTab) {
        this.tab = tab
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator = remember { AppNavigator() }
