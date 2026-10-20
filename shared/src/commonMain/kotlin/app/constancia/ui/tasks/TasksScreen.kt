package app.constancia.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.domain.TaskView
import app.constancia.domain.doneTasks
import app.constancia.domain.doneTodayTasks
import app.constancia.domain.entradaTasks
import app.constancia.domain.overdueTasks
import app.constancia.domain.projectPendingCount
import app.constancia.domain.projectTasks
import app.constancia.domain.taskCounts
import app.constancia.domain.todayTasks
import app.constancia.domain.upcomingGroupTitle
import app.constancia.domain.upcomingGroups
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.SecondaryButton
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/**
 * Aba Tarefas estilo Todoist (protótipo aprovado): captura rápida, chips de
 * visão (Entrada/Hoje/Próximos/Feitas) com contadores, chips de projeto com
 * cor, seções Atrasadas/Para hoje/Feitas e grupos por dia em Próximos.
 */
@Composable
fun TasksScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onNewTask: () -> Unit,
) {
    val today = remember { todayYmd() }
    val todayDate = remember { today.toLocalDate() }
    val scope = rememberCoroutineScope()
    val tasks by deps.tasks.allTasksFlow().collectAsState(initial = emptyList())
    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val habitNames = habits.associate { it.id to it.name }

    var view by remember { mutableStateOf(TaskView.HOJE) }
    var projectFilter by remember { mutableStateOf<TaskProject?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var quickText by remember { mutableStateOf("") }

    val counts = taskCounts(tasks, todayDate)

    fun quickAdd() {
        val title = quickText.trim()
        if (title.isEmpty()) return
        quickText = ""
        val project = projectFilter ?: TaskProject.ENTRADA
        val due = when {
            projectFilter != null && view == TaskView.ENTRADA -> null
            view == TaskView.ENTRADA -> null
            view == TaskView.PROXIMOS ->
                todayDate.plus(DatePeriod(days = 1)).toString()
            else -> today
        }
        scope.launch { deps.tasks.quickAdd(title, dueDate = due, project = project) }
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
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column {
                Text("Tarefas", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Capture rápido, priorize e risque — no ritmo do Todoist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // Captura rápida.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = quickText,
                    onValueChange = { if (it.length <= 60) quickText = it },
                    placeholder = { Text("Adicionar tarefa…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { quickAdd() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).testTag("tasks_quick_input"),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("tasks_quick_add")
                        .clickable { quickAdd() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Adicionar tarefa",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Chips de visão.
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ViewChip(
                        label = "Entrada · " + counts.entrada,
                        selected = view == TaskView.ENTRADA && projectFilter == null,
                        tag = "view_chip_ENTRADA",
                        onClick = { view = TaskView.ENTRADA; projectFilter = null },
                    )
                }
                item {
                    ViewChip(
                        label = "Hoje · " + counts.hoje,
                        selected = view == TaskView.HOJE && projectFilter == null,
                        tag = "view_chip_HOJE",
                        onClick = { view = TaskView.HOJE; projectFilter = null },
                    )
                }
                item {
                    ViewChip(
                        label = "Próximos · " + counts.proximos,
                        selected = view == TaskView.PROXIMOS && projectFilter == null,
                        tag = "view_chip_PROXIMOS",
                        onClick = { view = TaskView.PROXIMOS; projectFilter = null },
                    )
                }
                item {
                    ViewChip(
                        label = "Feitas · " + counts.feitas,
                        selected = view == TaskView.FEITAS && projectFilter == null,
                        tag = "view_chip_FEITAS",
                        onClick = { view = TaskView.FEITAS; projectFilter = null },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Chips de projeto.
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ViewChip(
                        label = "Todos os projetos",
                        selected = projectFilter == null,
                        tag = "proj_chip_ALL",
                        onClick = { projectFilter = null },
                    )
                }
                items(TaskProject.entries.filter { it != TaskProject.ENTRADA }) { project ->
                    ViewChip(
                        label = project.label + " · " +
                            projectPendingCount(tasks, project),
                        selected = projectFilter == project,
                        tag = "proj_chip_" + project.name,
                        dotColor = Color(project.colorHex),
                        onClick = { projectFilter = project },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        val filter = projectFilter
        if (filter != null) {
            val mine = projectTasks(tasks, filter)
            val pending = mine.filter { !it.done }
            val done = mine.filter { it.done }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                ) {
                    Box(
                        modifier = Modifier.size(11.dp)
                            .background(Color(filter.colorHex), CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(filter.label, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (pending.isEmpty()) {
                item { EmptyNote("Nenhuma tarefa pendente neste projeto.") }
            } else {
                items(pending, key = { it.id }) { rowFor(it) }
            }
            if (done.isNotEmpty()) {
                item {
                    SectionHead(
                        icon = Icons.Filled.Check,
                        label = "Feitas",
                        count = done.size.toString(),
                    )
                }
                items(done, key = { it.id }) { rowFor(it) }
            }
        } else {
            when (view) {
                TaskView.ENTRADA -> {
                    item { ViewTitle("Caixa de Entrada") }
                    item {
                        Text(
                            "Capturou, caiu aqui. Organize quando der: toque numa " +
                                "tarefa para agendar, priorizar e distribuir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    val list = entradaTasks(tasks)
                    if (list.isEmpty()) {
                        item { EmptyNote("Entrada vazia — cabeça limpa.") }
                    } else {
                        items(list, key = { it.id }) { rowFor(it) }
                    }
                }
                TaskView.HOJE -> {
                    item { ViewTitle("Hoje") }
                    val over = overdueTasks(tasks, todayDate)
                    val tod = todayTasks(tasks, todayDate)
                    val doneT = doneTodayTasks(tasks, todayDate)
                    if (over.isNotEmpty()) {
                        item {
                            SectionHead(
                                icon = Icons.Filled.Schedule,
                                label = "Atrasadas",
                                count = over.size.toString() + " pendente" +
                                    if (over.size > 1) "s" else "",
                                danger = true,
                            )
                        }
                        items(over, key = { it.id }) { rowFor(it) }
                    }
                    if (tod.isNotEmpty()) {
                        item {
                            SectionHead(
                                icon = Icons.Filled.WbSunny,
                                label = "Para hoje",
                                count = tod.size.toString() + " pendente" +
                                    if (tod.size > 1) "s" else "",
                            )
                        }
                        items(tod, key = { it.id }) { rowFor(it) }
                    }
                    if (over.isEmpty() && tod.isEmpty()) {
                        item { EmptyNote("Nada pendente para hoje. Dia limpo!") }
                    }
                    if (doneT.isNotEmpty()) {
                        item {
                            SectionHead(
                                icon = Icons.Filled.Check,
                                label = "Feitas hoje",
                                count = doneT.size.toString(),
                            )
                        }
                        items(doneT, key = { it.id }) { rowFor(it) }
                    }
                }
                TaskView.PROXIMOS -> {
                    item { ViewTitle("Próximos dias") }
                    val groups = upcomingGroups(tasks, todayDate)
                    if (groups.isEmpty()) {
                        item { EmptyNote("Nada agendado para os próximos dias.") }
                    } else {
                        groups.forEach { (day, list) ->
                            item(key = "grp-" + day) {
                                SectionHead(
                                    icon = Icons.Filled.CalendarMonth,
                                    label = upcomingGroupTitle(day, todayDate),
                                    count = list.size.toString(),
                                )
                            }
                            items(list, key = { it.id }) { rowFor(it) }
                        }
                    }
                }
                TaskView.FEITAS -> {
                    item { ViewTitle("Feitas") }
                    val list = doneTasks(tasks)
                    if (list.isEmpty()) {
                        item { EmptyNote("Nenhuma tarefa concluída ainda.") }
                    } else {
                        items(list, key = { it.id }) { rowFor(it) }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            SecondaryButton(
                text = "+ Nova tarefa completa",
                onClick = onNewTask,
                modifier = Modifier.fillMaxWidth(),
                tag = "open_task_sheet",
            )
        }
    }
}

/** Chip de filtro (DESIGN_TOKENS.md): selecionado = fundo primary-soft,
 *  texto primary, borda primary; senão surface com borda line e texto muted. */
@Composable
private fun ViewChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String? = null,
    dotColor: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(
                BorderStroke(
                    1.5.dp,
                    if (selected) scheme.primary else scheme.outline,
                ),
                RoundedCornerShape(999.dp),
            )
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ViewTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
}

@Composable
private fun SectionHead(
    icon: ImageVector,
    label: String,
    count: String,
    danger: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val titleColor = if (danger) {
        MaterialTheme.constanciaExtra.red
    } else {
        scheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 2.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = titleColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
