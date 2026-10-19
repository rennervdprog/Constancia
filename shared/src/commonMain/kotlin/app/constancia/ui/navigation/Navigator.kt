package app.constancia.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Abas principais do app. Ícones outline (traço), como no protótipo —
 * os filled deixavam a barra com cara de M3 padrão.
 */
enum class AppTab(val label: String, val icon: ImageVector) {
    HOJE("Hoje", Icons.Outlined.CheckCircle),
    RELATORIOS("Relatórios", Icons.Outlined.BarChart),
    DIARIO("Diário", Icons.Outlined.MenuBook),
    DESAFIOS("Desafios", Icons.Outlined.EmojiEvents),
    MAIS("Mais", Icons.Outlined.MoreHoriz),
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
