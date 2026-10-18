package app.constancia.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.data.SyncState
import app.constancia.domain.buildExportCsv
import app.constancia.domain.buildExportJson
import app.constancia.export.shareTextFile
import app.constancia.model.todayYmd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(deps: AppDeps, modifier: Modifier = Modifier, onOpenPaywall: () -> Unit = {}) {
    val today = remember { todayYmd() }
    val scope = rememberCoroutineScope()
    val darkTheme by deps.prefs.darkThemeFlow().collectAsState(initial = false)
    val isPro by deps.prefs.isProFlow().collectAsState(initial = false)
    val syncStatus by deps.sync.statusFlow().collectAsState(initial = null)
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var exportMsg by remember { mutableStateOf<String?>(null) }

    val syncSubtitle = if (syncStatus?.state == SyncState.CLOUD_SOON) {
        "em breve"
    } else {
        syncStatus?.lastSyncLabel ?: "em breve"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Mais", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Conta, nuvem, saúde do celular e seus dados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Conta Constância", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "plano " + if (isPro) "Pro" else "grátis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sincronização na nuvem", style = MaterialTheme.typography.titleMedium)
                            Text(
                                syncSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hábitos e diário continuarão salvos neste aparelho até a nuvem chegar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { syncMsg = "Disponível em breve" },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sincronizar agora")
                    }
                    if (syncMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            syncMsg ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aparência", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tema escuro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = { scope.launch { deps.prefs.setDarkTheme(it) } },
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exportar meus dados", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Seus hábitos e seu diário são seus — leve quando quiser.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    exportMsg = doExport(deps, today, csv = false)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Exportar JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    exportMsg = doExport(deps, today, csv = true)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Exportar CSV")
                        }
                    }
                    if (exportMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            exportMsg ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            ProBanner(onClick = onOpenPaywall)
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private suspend fun doExport(deps: AppDeps, today: String, csv: Boolean): String {
    return try {
        val habits = deps.habits.habitsFlow().first()
        val checkins = deps.checkins.checkinsInRangeFlow(today.take(4) + "-01-01", today).first()
        val tasks = deps.tasks.tasksForDateFlow(today).first()
        val diary = deps.diary.recentFlow(365).first()
        val (fileName, content, mime) = if (csv) {
            Triple("constancia.csv", buildExportCsv(habits, checkins), "text/csv")
        } else {
            Triple("constancia.json", buildExportJson(habits, checkins, tasks, diary), "application/json")
        }
        shareTextFile(fileName, content, mime)
        "Pronto — escolha onde salvar ou enviar"
    } catch (t: Throwable) {
        "Não foi possível exportar agora"
    }
}

@Composable
private fun ProBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Constância Pro", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Relatórios avançados, desafios e nuvem",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
