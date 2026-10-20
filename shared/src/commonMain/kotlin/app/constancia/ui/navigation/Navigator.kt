package app.constancia.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Abas principais do app (protótipo turbinado aprovado):
 * Hoje · Tarefas · (+) · Hábitos · Mais.
 * O botão central de criar NÃO é uma aba — abre a folha "O que você quer
 * criar?". Diário, Progresso (relatórios) e Desafios moram na aba Mais.
 */
enum class AppTab(val label: String, val icon: ImageVector) {
    HOJE("Hoje", Icons.Outlined.CheckCircle),
    TAREFAS("Tarefas", Icons.Outlined.FormatListBulleted),
    HABITOS("Hábitos", Icons.Outlined.Whatshot),
    MAIS("Mais", Icons.Outlined.MoreHoriz),
}

/** Seções acessíveis a partir da aba Mais. */
enum class MaisSection { DIARIO, PROGRESSO, DESAFIOS }

class AppNavigator {
    var tab by mutableStateOf(AppTab.HOJE)
    var maisSection by mutableStateOf<MaisSection?>(null)
    var showPaywall by mutableStateOf(false)
    var showCreate by mutableStateOf(false)
    var showNewHabit by mutableStateOf(false)
    var showNewTask by mutableStateOf(false)
    /** Dia a abrir no editor do diário (vindo do cartão da Hoje). */
    var diaryDate by mutableStateOf<String?>(null)

    fun navigateTo(tab: AppTab) {
        // Tocar em "Mais" já estando numa seção da Mais volta à raiz dela.
        if (tab == AppTab.MAIS && this.tab == AppTab.MAIS) {
            maisSection = null
            return
        }
        this.tab = tab
        if (tab != AppTab.MAIS) maisSection = null
    }

    fun openMaisSection(section: MaisSection) {
        tab = AppTab.MAIS
        maisSection = section
    }

    fun openDiary(date: String? = null) {
        diaryDate = date
        openMaisSection(MaisSection.DIARIO)
    }
}

@Composable
fun rememberAppNavigator(): AppNavigator = remember { AppNavigator() }
