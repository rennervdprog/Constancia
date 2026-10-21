package app.constancia.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.data.SyncState
import app.constancia.domain.buildExportCsv
import app.constancia.domain.buildExportJson
import app.constancia.export.shareTextFile
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Aba Mais (protótipo "Roxo Profundo" aprovado): cartão de perfil com a
 * sequência em chama, o cartão Pro em gradiente roxo com preço e o menu
 * "Atalhos" em botões com tiles suaves — Diário, Relatórios detalhados,
 * Desafios, Sincronização e Ajustes (que expande tema e exportação).
 */
@Composable
fun MoreScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
    onOpenSection: (app.constancia.ui.navigation.MaisSection) -> Unit = {},
) {
    val today = remember { todayYmd() }
    val scope = rememberCoroutineScope()
    val extra = MaterialTheme.constanciaExtra
    val scheme = MaterialTheme.colorScheme
    val darkTheme by deps.prefs.darkThemeFlow().collectAsState(initial = false)
    val isPro by deps.prefs.isProFlow().collectAsState(initial = false)
    val syncStatus by deps.sync.statusFlow().collectAsState(initial = null)
    val statuses by deps.habits.todayStatusFlow().collectAsState(initial = emptyList())
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(true) }

    val bestStreak = statuses.maxOfOrNull { it.currentStreak } ?: 0
    val syncSubtitle = if (syncStatus?.state == SyncState.CLOUD_SOON) {
        "em breve"
    } else {
        syncStatus?.lastSyncLabel ?: "em breve"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("mais_list"),
        contentPadding = PaddingValues(
            start = 20.dp, top = 10.dp, end = 20.dp, bottom = 118.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Column {
                Text("Mais", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tudo o resto do Constância",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
            }
        }

        // Cartão de perfil do protótipo.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surface)
                    .border(1.dp, extra.lineSoft, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF5B21B6),
                                    Color(0xFF38106E),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(27.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Conta Constância",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (isPro) "Plano Pro" else "Plano gratuito",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (bestStreak > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(extra.tintFlame)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = scheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = bestStreak.toString() +
                                if (bestStreak == 1) " dia" else " dias",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = scheme.secondary,
                        )
                    }
                }
            }
        }

        // Cartão Pro em gradiente do protótipo.
        item {
            ProCard(isPro = isPro, onClick = onOpenPaywall)
        }

        item {
            Text(
                text = "Atalhos",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp),
            )
        }
        item {
            MenuButton(
                icon = Icons.Filled.MenuBook,
                tint = extra.tintSun,
                iconColor = extra.sunColor,
                title = "Diário",
                subtitle = "Humor, energia, sono e a pergunta do dia",
                tag = "mais_diario",
                onClick = {
                    onOpenSection(app.constancia.ui.navigation.MaisSection.DIARIO)
                },
            )
        }
        item {
            MenuButton(
                icon = Icons.Filled.BarChart,
                tint = scheme.primaryContainer,
                iconColor = scheme.primary,
                title = "Relatórios detalhados",
                subtitle = "Padrões, raio-X e ano em revista",
                tag = "mais_progresso",
                onClick = {
                    onOpenSection(app.constancia.ui.navigation.MaisSection.PROGRESSO)
                },
            )
        }
        item {
            MenuButton(
                icon = Icons.Filled.EmojiEvents,
                tint = extra.tintFlame,
                iconColor = scheme.secondary,
                title = "Desafios",
                subtitle = "21 e 30 dias, no seu ritmo",
                tag = "mais_desafios",
                onClick = {
                    onOpenSection(app.constancia.ui.navigation.MaisSection.DESAFIOS)
                },
            )
        }
        item {
            Column {
                MenuButton(
                    icon = Icons.Filled.Sync,
                    tint = extra.tintAqua,
                    iconColor = extra.aquaColor,
                    title = "Sincronização",
                    subtitle = syncSubtitle,
                    tag = "mais_sync",
                    onClick = { syncMsg = "Disponível em breve" },
                )
                if (syncMsg != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        syncMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        item {
            MenuButton(
                icon = Icons.Filled.Settings,
                tint = extra.tintBlue,
                iconColor = extra.blueColor,
                title = "Ajustes",
                subtitle = "Tema, dados e conta",
                tag = "mais_ajustes",
                onClick = { showSettings = !showSettings },
            )
        }

        if (showSettings) {
            item {
                SettingRow {
                    IconTile(
                        icon = Icons.Filled.Palette,
                        tint = extra.tintBlue,
                        iconColor = extra.blueColor,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aparência", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tema escuro",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = {
                            scope.launch { deps.prefs.setDarkTheme(it) }
                        },
                    )
                }
            }
            item {
                StackCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = Icons.Filled.Download,
                            tint = extra.tintAqua,
                            iconColor = extra.aquaColor,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Exportar meus dados",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Seus hábitos e seu diário são seus — leve quando quiser.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = "Exportar JSON",
                            onClick = {
                                scope.launch {
                                    exportMsg = doExport(deps, today, csv = false)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Exportar CSV",
                            onClick = {
                                scope.launch {
                                    exportMsg = doExport(deps, today, csv = true)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (exportMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            exportMsg ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Cartão Pro da aba Mais (.pro-card do protótipo): gradiente roxo,
 *  círculo decorativo, selo, título, texto e a linha de preço com o
 *  botão branco "Conhecer". */
@Composable
private fun ProCard(isPro: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF4B1D96), Color(0xFF2A0A54)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(360f, 300f),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-60).dp)
                .size(150.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFC4A6F5).copy(alpha = 0.18f)),
        )
        Column(modifier = Modifier.padding(19.dp)) {
            Text(
                text = "CONSTÂNCIA PRO",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.12.em,
                ),
                color = Color(0xFFC4A6F5),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isPro) {
                    "Você já é Pro"
                } else {
                    "Relatórios sem limite de tempo"
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp),
                color = Color.White,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Padrões cruzados, raio-X de atrasos e o ano em " +
                    "revista — sem recorte de 30 dias.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = Color(0xFFDCCFFB),
                modifier = Modifier.widthIn(max = 270.dp),
            )
            if (!isPro) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "R$ 119,90/ano",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "R$ 191,88",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                        ),
                        color = Color(0xFFA78BDB),
                        textDecoration = TextDecoration.LineThrough,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .widthIn(min = 88.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Conhecer",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = Color(0xFF2A0A54),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Tile de ícone suave 40dp raio 13 (menu e ajustes). */
@Composable
private fun IconTile(icon: ImageVector, tint: Color, iconColor: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Botão do menu "Atalhos" do protótipo: surface raio 18, tile de ícone
 *  suave, título, subtítulo e a seta em lavanda. */
@Composable
private fun MenuButton(
    icon: ImageVector,
    tint: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surface)
            .border(1.dp, extra.lineSoft, RoundedCornerShape(18.dp))
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, tint = tint, iconColor = iconColor)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFA08FC0),
            modifier = Modifier.size(20.dp),
        )
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

/** Linha de ajuste: surface, borda line-soft, raio 18, padding 15/16. */
@Composable
private fun SettingRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.constanciaExtra.lineSoft,
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Cartão empilhado dos ajustes: raio 24, padding 16. */
@Composable
private fun StackCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.constanciaExtra.lineSoft,
                RoundedCornerShape(24.dp),
            )
            .padding(16.dp),
        content = content,
    )
}

/** Botão secundário: borda 1.5dp, raio 12dp, fundo bg, texto ink 13/700. */
@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
