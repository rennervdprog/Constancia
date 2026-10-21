package app.constancia.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.domain.MONTHS
import app.constancia.domain.TaskView
import app.constancia.domain.WEEKDAYS_ISO_PT
import app.constancia.domain.doneTasks
import app.constancia.domain.doneTodayTasks
import app.constancia.domain.entradaTasks
import app.constancia.domain.overdueTasks
import app.constancia.domain.taskCounts
import app.constancia.domain.todayTasks
import app.constancia.domain.upcomingGroupTitle
import app.constancia.domain.upcomingGroups
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** "terça" a partir da data (nome do dia sem o "-feira"). */
private fun weekdayShortPt(date: LocalDate): String =
    WEEKDAYS_ISO_PT[date.isoDayNumberCompat() - 1].substringBefore("-")

/** Rótulo do grupo de hoje: "Hoje · terça, 20 de out". */
private fun todayGroupLabel(today: LocalDate): String =
    "Hoje · " + weekdayShortPt(today) + ", " + today.dayOfMonth +
        " de " + MONTHS[today.monthNumber - 1]

/**
 * Aba Tarefas (protótipo "Roxo Profundo" aprovado): título com resumo
 * dinâmico, captura rápida em pílula, legenda de prioridades/projetos,
 * chips de visão (selecionado em ink) e as listas agrupadas em cartões
 * únicos por seção — Atrasadas em vermelho, Hoje, Feitas hoje, grupos de
 * Próximos e a Caixa de entrada.
 */
@Composable
fun TasksScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
) {
    val today = remember { todayYmd() }
    val todayDate = remember { today.toLocalDate() }
    val scope = rememberCoroutineScope()
    val tasks by deps.tasks.allTasksFlow().collectAsState(initial = emptyList())
    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val habitNames = habits.associate { it.id to it.name }

    var view by remember { mutableStateOf(TaskView.HOJE) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var quickText by remember { mutableStateOf("") }

    val counts = taskCounts(tasks, todayDate)
    val overdue = overdueTasks(tasks, todayDate)
    val dueTodayCount = tasks.count {
        !it.done && it.dueDate == today
    }

    fun quickAdd() {
        val title = quickText.trim()
        if (title.isEmpty()) return
        quickText = ""
        val due = when (view) {
            TaskView.ENTRADA -> null
            TaskView.PROXIMOS -> todayDate.plus(DatePeriod(days = 1)).toString()
            else -> today
        }
        scope.launch {
            deps.tasks.quickAdd(title, dueDate = due, project = TaskProject.ENTRADA)
        }
    }

    @Composable
    fun rowFor(task: TaskItem) {
        TodoRowWithActions(
            task = task,
            today = todayDate,
            habitName = task.habitId?.let { habitNames[it] },
            expanded = expandedId == task.id,
            onToggleExpand = {
                expandedId = if (expandedId == task.id) null else task.id
            },
            deps = deps,
            scope = scope,
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("tasks_list"),
        contentPadding = PaddingValues(
            start = 20.dp, top = 10.dp, end = 20.dp, bottom = 118.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column {
                Text(
                    "Tarefas",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(overdue.size)
                        append(if (overdue.size == 1) " atrasada · " else " atrasadas · ")
                        append(dueTodayCount)
                        append(" para hoje")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        item {
            QuickAddBar(
                text = quickText,
                onTextChange = { if (it.length <= 80) quickText = it },
                onAdd = { quickAdd() },
            )
        }

        item { Legend() }

        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp)
                    .testTag("view_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ViewChip(
                        label = "Entrada",
                        count = counts.entrada,
                        selected = view == TaskView.ENTRADA,
                        tag = "view_chip_ENTRADA",
                        onClick = { view = TaskView.ENTRADA },
                    )
                }
                item {
                    ViewChip(
                        label = "Hoje",
                        count = counts.hoje,
                        selected = view == TaskView.HOJE,
                        tag = "view_chip_HOJE",
                        onClick = { view = TaskView.HOJE },
                    )
                }
                item {
                    ViewChip(
                        label = "Próximos",
                        count = counts.proximos,
                        selected = view == TaskView.PROXIMOS,
                        tag = "view_chip_PROXIMOS",
                        onClick = { view = TaskView.PROXIMOS },
                    )
                }
                item {
                    ViewChip(
                        label = "Feitas",
                        count = counts.feitas,
                        selected = view == TaskView.FEITAS,
                        tag = "view_chip_FEITAS",
                        onClick = { view = TaskView.FEITAS },
                    )
                }
            }
        }

        when (view) {
            TaskView.ENTRADA -> {
                val list = entradaTasks(tasks)
                item {
                    GroupLabel(
                        label = "Caixa de entrada",
                        rightText = "captura rápida, organize depois",
                    )
                }
                if (list.isEmpty()) {
                    item {
                        EmptyNote(
                            "Caixa vazia: o que você capturar sem data cai aqui, " +
                                "sem pressa.",
                        )
                    }
                } else {
                    item {
                        RowsCard {
                            list.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                }
            }

            TaskView.HOJE -> {
                val tod = todayTasks(tasks, todayDate)
                val doneT = doneTodayTasks(tasks, todayDate)
                if (overdue.isNotEmpty()) {
                    item {
                        GroupLabel(
                            label = "Atrasadas",
                            count = overdue.size,
                            danger = true,
                        )
                    }
                    item {
                        RowsCard {
                            overdue.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                }
                item {
                    GroupLabel(
                        label = todayGroupLabel(todayDate),
                        count = tod.size,
                    )
                }
                if (tod.isEmpty()) {
                    item {
                        EmptyNote("Nada para hoje. Aproveite o respiro — ou adiante algo de amanhã.")
                    }
                } else {
                    item {
                        RowsCard {
                            tod.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "Toque numa tarefa para ver subtarefas, " +
                            "prioridade e reagendamento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, top = 12.dp),
                    )
                }
                if (doneT.isNotEmpty()) {
                    item {
                        GroupLabel(label = "Feitas hoje", count = doneT.size)
                    }
                    item {
                        RowsCard {
                            doneT.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                }
            }

            TaskView.PROXIMOS -> {
                val groups = upcomingGroups(tasks, todayDate)
                if (groups.isEmpty()) {
                    item {
                        GroupLabel(label = "Próximos dias", count = 0)
                    }
                    item {
                        EmptyNote("Sem tarefas nos próximos 7 dias. Planeje a semana na captura acima.")
                    }
                } else {
                    groups.forEach { (day, dayTasks) ->
                        item(key = "prox-head-" + day.toString()) {
                            GroupLabel(
                                label = upcomingGroupTitle(day, todayDate),
                                count = dayTasks.size,
                            )
                        }
                        item(key = "prox-" + day.toString()) {
                            RowsCard {
                                dayTasks.forEachIndexed { index, task ->
                                    if (index > 0) RowDivider()
                                    rowFor(task)
                                }
                            }
                        }
                    }
                }
            }

            TaskView.FEITAS -> {
                val list = doneTasks(tasks)
                item {
                    GroupLabel(label = "Feitas", count = list.size)
                }
                if (list.isEmpty()) {
                    item {
                        EmptyNote("Ainda nada por aqui — conclua a primeira tarefa do dia.")
                    }
                } else {
                    item {
                        RowsCard {
                            list.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "Histórico completo de 90 dias no Constância Pro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, top = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Captura rápida do protótipo: pílula surface com borda 1.5dp, "+" em
 * chama, campo sem caixa e o botão roxo "Adicionar".
 */
@Composable
private fun QuickAddBar(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.5.dp, scheme.outline, RoundedCornerShape(16.dp))
            .padding(start = 15.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = scheme.secondary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = scheme.onSurface,
            ),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            text = "Capturar uma tarefa…",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                            ),
                            color = extra.placeholder,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f).testTag("tasks_quick_input"),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(scheme.primary)
                .testTag("tasks_quick_add")
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                text = "Adicionar",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = scheme.onPrimary,
            )
        }
    }
}

/** Legenda do protótipo: pontinhos de prioridade e de projeto, numa
 *  linha só (quebra se faltar espaço). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 14.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LegendDot(color = Color(0xFFD92D20), label = "P1 alta")
        LegendDot(color = Color(0xFFE86A17), label = "P2 média")
        LegendDot(color = Color(0xFF2563EB), label = "P3 baixa")
        TaskProject.entries.filter { it != TaskProject.ENTRADA }.forEach { project ->
            LegendDot(color = Color(project.colorHex), label = project.label)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Chip de visão do protótipo: pílula surface com borda; selecionado vira
 * ink (fundo escuro, texto branco) e o contador vai num selo próprio.
 */
@Composable
private fun ViewChip(
    label: String,
    count: Int,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) extra.chipInk else scheme.surface)
            .border(
                1.5.dp,
                if (selected) extra.chipInk else scheme.outline,
                RoundedCornerShape(99.dp),
            )
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = if (selected) extra.chipInkText else extra.ink2,
        )
        Spacer(Modifier.width(7.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(
                    if (selected) {
                        extra.chipInkText.copy(alpha = 0.16f)
                    } else {
                        scheme.background
                    },
                )
                .padding(horizontal = 7.dp, vertical = 1.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = if (selected) extra.chipInkText else scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Rótulo de grupo do protótipo: Sora 13/700, contador discreto à
 * direita; variante de perigo com relógio e texto vermelho (Atrasadas).
 */
@Composable
private fun GroupLabel(
    label: String,
    count: Int? = null,
    danger: Boolean = false,
    rightText: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 20.dp, end = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (danger) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = extra.red,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (danger) extra.red else scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (rightText != null) {
            Text(
                text = rightText,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        } else if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Cartão que agrupa as linhas de uma seção (.rows do protótipo). */
@Composable
private fun RowsCard(content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0xFF26104A).copy(alpha = 0.16f),
                ambientColor = Color(0xFF26104A).copy(alpha = 0.16f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surface)
            .border(1.dp, extra.lineSoft, RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.constanciaExtra.lineSoft),
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp, end = 2.dp),
    )
}
