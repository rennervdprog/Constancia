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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.domain.MONTHS
import app.constancia.domain.QuickAddParse
import app.constancia.domain.TaskGroupBy
import app.constancia.domain.TaskSortMode
import app.constancia.domain.TaskView
import app.constancia.domain.WEEKDAYS_ISO_PT
import app.constancia.domain.applySort
import app.constancia.domain.doneTodayTasks
import app.constancia.domain.encodeRepeatSpec
import app.constancia.domain.entradaTasks
import app.constancia.domain.feitasTasks
import app.constancia.domain.groupByOf
import app.constancia.domain.groupByValue
import app.constancia.domain.groupTasks
import app.constancia.domain.overdueTasks
import app.constancia.domain.repeatLabel
import app.constancia.domain.savedViewTasks
import app.constancia.domain.sortModeOf
import app.constancia.domain.taskCounts
import app.constancia.domain.todayTasks
import app.constancia.domain.upcomingGroupTitle
import app.constancia.domain.upcomingGroups
import app.constancia.domain.withOccurrenceHistory
import app.constancia.model.CompletionReversal
import app.constancia.model.SavedView
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
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
@OptIn(ExperimentalLayoutApi::class)
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
    // Item 4: ordenação/agrupamento das listas.
    var sortMode by remember { mutableStateOf(TaskSortMode.SMART) }
    var groupBy by remember { mutableStateOf(TaskGroupBy.NONE) }
    // Itens 10/11: vistas salvas e modelos.
    var savedViewId by remember { mutableStateOf<String?>(null) }
    var showNewView by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    val savedViews by deps.tasks.savedViewsFlow().collectAsState(initial = emptyList())
    val templates by deps.tasks.templatesFlow().collectAsState(initial = emptyList())
    val activeView = savedViews.firstOrNull { it.id == savedViewId }

    // Fase B (R1.1): histórico de ocorrências concluídas das séries —
    // completa a visão Feitas e o contador dela.
    val history by deps.tasks.occurrenceHistoryFlow()
        .collectAsState(initial = emptyList())

    // Fase B (R8.1): opções de vista persistidas POR VISÃO. A chave é a
    // visão ativa; o que está gravado volta aos chips ao abrir a tela,
    // ao girar o celular e ao trocar de aba (antes era só remember).
    val viewKey = if (activeView != null) {
        "saved:" + activeView.id
    } else {
        "view:" + view.name
    }
    val storedOptions by deps.tasks.viewOptionsFlow(viewKey)
        .collectAsState(initial = null)
    LaunchedEffect(viewKey, storedOptions) {
        val stored = storedOptions ?: return@LaunchedEffect
        if (stored.viewKey != viewKey) return@LaunchedEffect
        sortMode = sortModeOf(stored.sortMode)
        // Vista salva agrupa pela própria definição; as demais, pelo chip.
        if (activeView == null) groupBy = groupByOf(stored.groupBy)
    }

    fun persistViewOptions(
        newSortMode: TaskSortMode = sortMode,
        newGroupBy: TaskGroupBy = groupBy,
    ) {
        scope.launch {
            deps.tasks.setViewOptions(
                viewKey, groupByValue(newGroupBy), newSortMode.name,
            )
        }
    }

    // Fase B (R2.2): desfazer conclusão — snackbar com ação (~8s) e o
    // botão "Desfazer conclusão" na visão Feitas enquanto houver pacote.
    val snackbarHostState = remember { SnackbarHostState() }
    var lastReversal by remember { mutableStateOf<CompletionReversal?>(null) }

    fun onTaskCompleted(reversal: CompletionReversal?) {
        if (reversal == null) return
        lastReversal = reversal
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Tarefa concluída",
                actionLabel = "Desfazer",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                deps.tasks.undoCompletion(reversal)
                if (lastReversal == reversal) lastReversal = null
            }
        }
    }

    fun undoLastCompletion() {
        val reversal = lastReversal ?: return
        lastReversal = null
        scope.launch {
            deps.tasks.undoCompletion(reversal)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    val counts = taskCounts(withOccurrenceHistory(tasks, history), todayDate)
    val overdue = overdueTasks(tasks, todayDate)
    val dueTodayCount = tasks.count {
        !it.done && it.dueDate == today
    }

    /** Captura rápida com o parser de linguagem natural (item 6): o que o parser
     *  reconheceu vira campo da tarefa; o resto segue o padrão da visão. */
    fun quickAddParsed(parse: QuickAddParse) {
        val title = parse.title.trim()
        if (title.isEmpty()) return
        quickText = ""
        val defaultDue = when (view) {
            TaskView.ENTRADA -> null
            TaskView.PROXIMOS -> todayDate.plus(DatePeriod(days = 1)).toString()
            else -> today
        }
        scope.launch {
            deps.tasks.add(
                app.constancia.model.NewTask(
                    title = title,
                    project = parse.project ?: TaskProject.ENTRADA,
                    priority = parse.priority ?: 2,
                    dueDate = parse.dueDate?.toYmd() ?: defaultDue,
                    dueTime = parse.dueTime,
                    repeatRule = parse.repeat?.let { repeatLabel(it) } ?: "",
                    repeatSpec = parse.repeat?.let { encodeRepeatSpec(it) } ?: "",
                    // R3.3 (Fase C): "!30m" vira lembrete; "por 30min"
                    // vira a duração estimada da tarefa criada.
                    reminderMinutesBefore = parse.reminderMinutes,
                    durationMinutes = parse.durationMinutes,
                ),
            )
        }
    }

    @Composable
    fun rowFor(task: TaskItem) {
        if (task.id.startsWith("hist:")) {
            // R1.1: ocorrência do histórico sem linha viva — registro
            // consolidado, somente leitura (sem ações nem expansão).
            TodoRow(
                task = task,
                today = todayDate,
                habitName = null,
                expanded = false,
                onToggleExpand = {},
                onToggleDone = {},
                onSubtaskToggle = { _, _ -> },
                onMoveToday = {},
                onPostpone = {},
                onCyclePriority = {},
                onDelete = {},
                onReschedule = {},
            )
            return
        }
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
            onCompleted = ::onTaskCompleted,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("tasks_list"),
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
            // Captura rápida em pílula (item 6: com destaque dos
            // trechos reconhecidos e chips de desfazer).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(start = 15.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            ) {
                QuickAddField(
                    text = quickText,
                    onTextChange = { if (it.length <= 80) quickText = it },
                    onAdd = { quickAddParsed(it) },
                    today = todayDate,
                    inputTag = "tasks_quick_input",
                    addTag = "tasks_quick_add",
                    placeholder = "Capturar uma tarefa…",
                )
            }
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
                        selected = savedViewId == null && view == TaskView.ENTRADA,
                        tag = "view_chip_ENTRADA",
                        onClick = { savedViewId = null; view = TaskView.ENTRADA },
                    )
                }
                item {
                    ViewChip(
                        label = "Hoje",
                        count = counts.hoje,
                        selected = savedViewId == null && view == TaskView.HOJE,
                        tag = "view_chip_HOJE",
                        onClick = { savedViewId = null; view = TaskView.HOJE },
                    )
                }
                item {
                    ViewChip(
                        label = "Próximos",
                        count = counts.proximos,
                        selected = savedViewId == null && view == TaskView.PROXIMOS,
                        tag = "view_chip_PROXIMOS",
                        onClick = { savedViewId = null; view = TaskView.PROXIMOS },
                    )
                }
                item {
                    ViewChip(
                        label = "Feitas",
                        count = counts.feitas,
                        selected = savedViewId == null && view == TaskView.FEITAS,
                        tag = "view_chip_FEITAS",
                        onClick = { savedViewId = null; view = TaskView.FEITAS },
                    )
                }
                savedViews.forEach { saved ->
                    item(key = "saved_chip_" + saved.id) {
                        SavedViewChip(
                            view = saved,
                            selected = savedViewId == saved.id,
                            onClick = { savedViewId = saved.id },
                        )
                    }
                }
                item {
                    NewViewChip(onClick = { showNewView = true })
                }
            }
        }

        // Barra de opções (item 4 + atalho dos modelos, item 11).
        item {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionChip(
                    label = "Ordenar: " + when (sortMode) {
                        TaskSortMode.SMART -> "Inteligente"
                        TaskSortMode.PRIORITY -> "Prioridade"
                        TaskSortMode.DUE -> "Prazo"
                    },
                    onClick = {
                        val newMode = when (sortMode) {
                            TaskSortMode.SMART -> TaskSortMode.PRIORITY
                            TaskSortMode.PRIORITY -> TaskSortMode.DUE
                            TaskSortMode.DUE -> TaskSortMode.SMART
                        }
                        sortMode = newMode
                        persistViewOptions(newSortMode = newMode)
                    },
                )
                OptionChip(
                    label = "Agrupar: " + when (groupBy) {
                        TaskGroupBy.NONE -> "Nenhum"
                        TaskGroupBy.PROJECT -> "Projeto"
                        TaskGroupBy.PRIORITY -> "Prioridade"
                        TaskGroupBy.DATE -> "Data"
                    },
                    onClick = {
                        val newGroup = when (groupBy) {
                            TaskGroupBy.NONE -> TaskGroupBy.PROJECT
                            TaskGroupBy.PROJECT -> TaskGroupBy.PRIORITY
                            TaskGroupBy.PRIORITY -> TaskGroupBy.DATE
                            TaskGroupBy.DATE -> TaskGroupBy.NONE
                        }
                        groupBy = newGroup
                        persistViewOptions(newGroupBy = newGroup)
                    },
                )
                OptionChip(
                    label = "Modelos de lista",
                    onClick = { showTemplates = true },
                )
            }
        }

        if (activeView != null) {
            val viewTasks = applySort(
                savedViewTasks(tasks, activeView, todayDate), sortMode, todayDate,
            )
            val viewGroups = groupTasks(
                viewTasks, groupByOf(activeView.groupBy), todayDate,
            )
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, top = 20.dp, end = 2.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(activeView.colorHex)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = activeView.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 15.sp,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable {
                                scope.launch {
                                    deps.tasks.deleteSavedView(activeView.id)
                                }
                                savedViewId = null
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "Excluir vista",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.constanciaExtra.red,
                        )
                    }
                }
            }
            if (viewTasks.isEmpty()) {
                item {
                    EmptyNote("Nada nesta vista com os filtros atuais.")
                }
            } else {
                viewGroups.forEach { group ->
                    if (group.title.isNotBlank()) {
                        item(key = "sv-head-" + group.title) {
                            GroupLabel(label = group.title, count = group.tasks.size)
                        }
                    }
                    item(key = "sv-" + group.title + "-" + group.tasks.size) {
                        RowsCard {
                            group.tasks.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                }
            }
        } else when (view) {
            TaskView.ENTRADA -> {
                val list = applySort(entradaTasks(tasks), sortMode, todayDate)
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
                } else if (groupBy == TaskGroupBy.NONE) {
                    item {
                        RowsCard {
                            list.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                } else {
                    groupTasks(list, groupBy, todayDate).forEach { group ->
                        item(key = "ent-head-" + group.title) {
                            GroupLabel(label = group.title, count = group.tasks.size)
                        }
                        item(key = "ent-" + group.title) {
                            RowsCard {
                                group.tasks.forEachIndexed { index, task ->
                                    if (index > 0) RowDivider()
                                    rowFor(task)
                                }
                            }
                        }
                    }
                }
            }

            TaskView.HOJE -> {
                val tod = applySort(todayTasks(tasks, todayDate), sortMode, todayDate)
                val doneT = doneTodayTasks(tasks, todayDate)
                val overdueSorted = applySort(overdue, sortMode, todayDate)
                if (overdue.isNotEmpty()) {
                    item {
                        GroupLabel(
                            label = "Atrasadas",
                            count = overdue.size,
                            danger = true,
                        )
                    }
                    // Item 1: empurra todas as atrasadas para hoje.
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                )
                                .testTag("reschedule_overdue")
                                .clickable {
                                    scope.launch {
                                        deps.tasks.rescheduleAllOverdue(
                                            today, today,
                                        )
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Reagendar " +
                                    if (overdue.size == 1) {
                                        "1 atrasada"
                                    } else {
                                        overdue.size.toString() + " atrasadas"
                                    } + " para hoje",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme
                                    .onPrimaryContainer,
                            )
                        }
                    }
                    item {
                        RowsCard {
                            overdueSorted.forEachIndexed { index, task ->
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
                                applySort(dayTasks, sortMode, todayDate)
                                    .forEachIndexed { index, task ->
                                        if (index > 0) RowDivider()
                                        rowFor(task)
                                    }
                            }
                        }
                    }
                }
            }

            TaskView.FEITAS -> {
                // R1.1: feitas vivas + ocorrências órfãs do histórico.
                val list = applySort(
                    feitasTasks(tasks, history), sortMode, todayDate,
                )
                item {
                    GroupLabel(label = "Feitas", count = list.size)
                }
                // R2.2: desfazer a última conclusão, direto da Feitas.
                if (lastReversal != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                )
                                .testTag("undo_completion")
                                .clickable { undoLastCompletion() }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Desfazer conclusão",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme
                                    .onPrimaryContainer,
                            )
                        }
                    }
                }
                if (list.isEmpty()) {
                    item {
                        EmptyNote("Ainda nada por aqui — conclua a primeira tarefa do dia.")
                    }
                } else if (groupBy == TaskGroupBy.NONE) {
                    item {
                        RowsCard {
                            list.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                } else {
                    groupTasks(list, groupBy, todayDate).forEach { group ->
                        item(key = "done-head-" + group.title) {
                            GroupLabel(label = group.title, count = group.tasks.size)
                        }
                        item(key = "done-" + group.title) {
                            RowsCard {
                                group.tasks.forEachIndexed { index, task ->
                                    if (index > 0) RowDivider()
                                    rowFor(task)
                                }
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

        // R2.2: snackbar do desfazer, flutuando acima da barra inferior.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 118.dp),
        )
    }

    if (showNewView) {
        NewSavedViewSheet(
            onDismiss = { showNewView = false },
            onSave = { saved ->
                scope.launch { deps.tasks.addSavedView(saved) }
                savedViewId = saved.id
                showNewView = false
            },
        )
    }
    if (showTemplates) {
        TemplatesSheet(
            templates = templates,
            tasks = tasks,
            onDismiss = { showTemplates = false },
            onInstantiate = { templateId ->
                scope.launch { deps.tasks.instantiateTemplate(templateId, today) }
                showTemplates = false
            },
            onSaveFromProject = { name, items ->
                scope.launch { deps.tasks.saveTemplate(name, items) }
            },
            onDelete = { templateId ->
                scope.launch { deps.tasks.deleteTemplate(templateId) }
            },
        )
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

/** Chip de vista salva (item 10): pílula com o ponto da cor da vista. */
@Composable
private fun SavedViewChip(view: SavedView, selected: Boolean, onClick: () -> Unit) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(view.colorHex)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = view.name,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (selected) scheme.surface else scheme.onSurface,
        )
    }
}

/** Chip "+ Vista" (item 10): contorno tracejado pelo fundo suave. */
@Composable
private fun NewViewChip(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(scheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "+ Vista",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = scheme.onPrimaryContainer,
        )
    }
}

/** Chip pequeno da barra de opções (itens 4 e 11). */
@Composable
private fun OptionChip(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(scheme.surface)
            .border(1.5.dp, scheme.outline, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            color = scheme.onSurfaceVariant,
        )
    }
}
