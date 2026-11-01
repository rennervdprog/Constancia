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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import app.constancia.domain.canCreateTask
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
import app.constancia.domain.sortSmart
import app.constancia.domain.taskCounts
import app.constancia.domain.taskMatchesQuery
import app.constancia.domain.todayTasks
import app.constancia.domain.upcomingGroupTitle
import app.constancia.domain.upcomingGroups
import app.constancia.domain.withOccurrenceHistory
import app.constancia.model.CompletionReversal
import app.constancia.model.SavedView
import app.constancia.model.Session
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
import kotlinx.datetime.toLocalDateTime

/** "terça" a partir da data (nome do dia sem o "-feira"). */
private fun weekdayShortPt(date: LocalDate): String =
    WEEKDAYS_ISO_PT[date.isoDayNumberCompat() - 1].substringBefore("-")

/** Rótulo do grupo de hoje: "Hoje · terça, 20 de out". */
private fun todayGroupLabel(today: LocalDate): String =
    "Hoje · " + weekdayShortPt(today) + ", " + today.dayOfMonth +
        " de " + MONTHS[today.monthNumber - 1]

/**
 * Aba Tarefas (redesenho aprovado): cabeçalho enxuto com busca e
 * ajustes, resumo dinâmico, captura rápida fina e um controle
 * segmentado para as visões. Ordenar, agrupar, modelos, nova vista e a
 * legenda de cores vivem na folha "Ajustes da lista". A visão Hoje
 * destaca as atrasadas numa faixa própria, agrupa as pendências (por
 * sessão, no padrão) e recolhe as Feitas hoje no fim.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
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
    // O2 (auditoria de tarefas): busca global por título/anotação.
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searching = searchOpen && searchQuery.isNotBlank()
    val searchResults = if (searching) {
        sortSmart(tasks.filter { taskMatchesQuery(it, searchQuery) }, todayDate)
    } else {
        emptyList()
    }
    // Item 4: ordenação/agrupamento das listas. Sem opção persistida,
    // o padrão é agrupar por sessão (TaskViewOptions.groupBy).
    var sortMode by remember { mutableStateOf(TaskSortMode.SMART) }
    var groupBy by remember { mutableStateOf(TaskGroupBy.SESSION) }
    var showFilters by remember { mutableStateOf(false) }
    var doneTodayExpanded by remember { mutableStateOf(false) }
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

    // Teto do grátis (2026-11-01): vale para captura rápida e modelos.
    // No teto, o paywall abre como convite e o texto digitado FICA —
    // nada se perde nem trava.
    val isPro by deps.entitlement.isProFlow.collectAsState(initial = false)
    fun taskCapReached(): Boolean =
        !canCreateTask(tasks.count { !it.done }, isPro)

    /** Captura rápida com o parser de linguagem natural (item 6): o que o parser
     *  reconheceu vira campo da tarefa; o resto segue o padrão da visão. */
    fun quickAddParsed(parse: QuickAddParse) {
        val title = parse.title.trim()
        if (title.isEmpty()) return
        if (taskCapReached()) {
            onOpenPaywall()
            return
        }
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
                    // Sessão (T1/T5, auditoria de tarefas): o
                    // horário reconhecido manda; sem horário, a
                    // parte do dia do texto; sem nada, a hora atual.
                    // (Antes era sempre a hora atual — "amanhã às
                    // 15h" digitado de manhã caía na Manhã.)
                    session = app.constancia.domain.resolveQuickSession(
                        dueTime = parse.dueTime,
                        daypart = parse.session,
                        nowHour = kotlinx.datetime.Clock.System.now()
                            .toLocalDateTime(
                                kotlinx.datetime.TimeZone.currentSystemDefault(),
                            ).hour,
                    ),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Tarefas",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    HeaderIconButton(
                        icon = Icons.Filled.Search,
                        contentDescription = if (searchOpen) {
                            "Fechar busca"
                        } else {
                            "Buscar tarefas"
                        },
                        tag = "tasks_search_btn",
                        active = searchOpen,
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    HeaderIconButton(
                        icon = Icons.Filled.Tune,
                        contentDescription = "Ajustes da lista",
                        tag = "tasks_filters_btn",
                        active = showFilters,
                        onClick = { showFilters = true },
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildAnnotatedString {
                        append(dueTodayCount.toString())
                        append(" para hoje · ")
                        withStyle(
                            SpanStyle(
                                color = if (overdue.isNotEmpty()) {
                                    MaterialTheme.constanciaExtra.red
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (overdue.isNotEmpty()) {
                                    FontWeight.ExtraBold
                                } else {
                                    FontWeight.SemiBold
                                },
                            ),
                        ) {
                            append(overdue.size.toString())
                            append(
                                if (overdue.size == 1) " atrasada" else " atrasadas",
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(13.dp))
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
                    .padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
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

        item {
            // Uma linha de controle: as quatro visões num segmentado.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 13.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.constanciaExtra.pageChip)
                    .padding(4.dp)
                    .testTag("view_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ViewSegment(
                    label = "Hoje",
                    count = counts.hoje,
                    selected = savedViewId == null && view == TaskView.HOJE,
                    tag = "view_chip_HOJE",
                    onClick = { savedViewId = null; view = TaskView.HOJE },
                    modifier = Modifier.weight(1f),
                )
                ViewSegment(
                    label = "Próximos",
                    count = counts.proximos,
                    selected = savedViewId == null && view == TaskView.PROXIMOS,
                    tag = "view_chip_PROXIMOS",
                    onClick = { savedViewId = null; view = TaskView.PROXIMOS },
                    modifier = Modifier.weight(1f),
                )
                ViewSegment(
                    label = "Entrada",
                    count = counts.entrada,
                    selected = savedViewId == null && view == TaskView.ENTRADA,
                    tag = "view_chip_ENTRADA",
                    onClick = { savedViewId = null; view = TaskView.ENTRADA },
                    modifier = Modifier.weight(1f),
                )
                ViewSegment(
                    label = "Feitas",
                    count = counts.feitas,
                    selected = savedViewId == null && view == TaskView.FEITAS,
                    tag = "view_chip_FEITAS",
                    onClick = { savedViewId = null; view = TaskView.FEITAS },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Vistas salvas só ocupam a tela de quem as criou; criar uma
        // nova vive na folha de ajustes.
        if (savedViews.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
        }

        // O2: campo de busca (resultados substituem a visão atual
        // enquanto houver texto — a busca é global, todas as listas).
        if (searchOpen) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por título ou anotação…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .testTag("task_search_field"),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }

        if (searching) {
            item {
                GroupLabel(
                    label = "Resultados da busca",
                    count = searchResults.size,
                )
            }
            if (searchResults.isEmpty()) {
                item {
                    EmptyNote("Nenhuma tarefa encontrada para a busca.")
                }
            } else {
                item {
                    RowsCard {
                        searchResults.forEachIndexed { index, task ->
                            if (index > 0) RowDivider()
                            rowFor(task)
                        }
                    }
                }
            }
        } else if (activeView != null) {
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
                    // Faixa própria: quantas são, quais são e a ação
                    // de empurrar todas para hoje (item 1).
                    item {
                        val extra = MaterialTheme.constanciaExtra
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(extra.redSoft)
                                .border(
                                    1.dp,
                                    extra.red.copy(alpha = 0.22f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = extra.red,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(18.dp),
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = overdue.size.toString() + if (
                                        overdue.size == 1
                                    ) {
                                        " atrasada"
                                    } else {
                                        " atrasadas"
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    ),
                                    color = extra.red,
                                )
                                Text(
                                    text = overdueSorted.joinToString(" · ") {
                                        it.title
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                    ),
                                    color = extra.red.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        extra.red.copy(alpha = 0.25f),
                                        RoundedCornerShape(99.dp),
                                    )
                                    .testTag("reschedule_overdue")
                                    .clickable {
                                        scope.launch {
                                            deps.tasks.rescheduleAllOverdue(
                                                today, today,
                                            )
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "Reagendar",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    ),
                                    color = extra.red,
                                )
                            }
                        }
                    }
                    item {
                        Box(modifier = Modifier.padding(top = 8.dp)) {
                            RowsCard {
                                overdueSorted.forEachIndexed { index, task ->
                                    if (index > 0) RowDivider()
                                    rowFor(task)
                                }
                            }
                        }
                    }
                }
                if (tod.isEmpty()) {
                    item {
                        GroupLabel(
                            label = todayGroupLabel(todayDate),
                            count = 0,
                        )
                    }
                    item {
                        EmptyNote("Nada para hoje. Aproveite o respiro — ou adiante algo de amanhã.")
                    }
                } else if (groupBy == TaskGroupBy.NONE) {
                    item {
                        GroupLabel(
                            label = todayGroupLabel(todayDate),
                            count = tod.size,
                        )
                    }
                    item {
                        RowsCard {
                            tod.forEachIndexed { index, task ->
                                if (index > 0) RowDivider()
                                rowFor(task)
                            }
                        }
                    }
                } else {
                    groupTasks(tod, groupBy, todayDate).forEach { group ->
                        item(key = "today-head-" + group.title) {
                            if (groupBy == TaskGroupBy.SESSION) {
                                val session = group.tasks.firstOrNull()?.session
                                val doneInSession = if (session == null) {
                                    0
                                } else {
                                    tasks.count {
                                        it.done && it.dueDate == today &&
                                            it.session == session
                                    }
                                }
                                SessionGroupHeader(
                                    label = group.title,
                                    session = session,
                                    doneCount = doneInSession,
                                    totalCount = doneInSession + group.tasks.size,
                                )
                            } else {
                                GroupLabel(
                                    label = group.title,
                                    count = group.tasks.size,
                                )
                            }
                        }
                        item(key = "today-" + group.title + "-" + group.tasks.size) {
                            RowsCard {
                                group.tasks.forEachIndexed { index, task ->
                                    if (index > 0) RowDivider()
                                    rowFor(task)
                                }
                            }
                        }
                    }
                }
                if (doneT.isNotEmpty()) {
                    // Feitas hoje recolhidas: a linha resume e expande
                    // no toque (redesenho, item 5 do protótipo).
                    item {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 15.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.constanciaExtra.lineSoft,
                                        RoundedCornerShape(16.dp),
                                    )
                                    .testTag("done_today_toggle")
                                    .clickable {
                                        doneTodayExpanded = !doneTodayExpanded
                                    }
                                    .padding(horizontal = 13.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Feitas hoje",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 13.5.sp,
                                    ),
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = doneT.size.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        ),
                                        color = MaterialTheme.colorScheme
                                            .onPrimaryContainer,
                                    )
                                }
                                if (!doneTodayExpanded) {
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        text = doneT.first().title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                        ),
                                        color = MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                        textDecoration = TextDecoration.LineThrough,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Box(modifier = Modifier.weight(1f))
                                }
                                Icon(
                                    if (doneTodayExpanded) {
                                        Icons.Filled.ExpandLess
                                    } else {
                                        Icons.Filled.ExpandMore
                                    },
                                    contentDescription = if (doneTodayExpanded) {
                                        "Recolher feitas de hoje"
                                    } else {
                                        "Expandir feitas de hoje"
                                    },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            if (doneTodayExpanded) {
                                Spacer(Modifier.height(8.dp))
                                RowsCard {
                                    doneT.forEachIndexed { index, task ->
                                        if (index > 0) RowDivider()
                                        rowFor(task)
                                    }
                                }
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
                if (taskCapReached()) {
                    showTemplates = false
                    onOpenPaywall()
                } else {
                    scope.launch { deps.tasks.instantiateTemplate(templateId, today) }
                    showTemplates = false
                }
            },
            onSaveFromProject = { name, items ->
                scope.launch { deps.tasks.saveTemplate(name, items) }
            },
            onDelete = { templateId ->
                scope.launch { deps.tasks.deleteTemplate(templateId) }
            },
        )
    }
    if (showFilters) {
        TasksFilterSheet(
            sortMode = sortMode,
            groupBy = groupBy,
            onDismiss = { showFilters = false },
            onSortModeChange = { mode ->
                sortMode = mode
                persistViewOptions(newSortMode = mode)
                showFilters = false
            },
            onGroupByChange = { newGroup ->
                groupBy = newGroup
                persistViewOptions(newGroupBy = newGroup)
                showFilters = false
            },
            onShowTemplates = {
                showFilters = false
                showTemplates = true
            },
            onNewView = {
                showFilters = false
                showNewView = true
            },
        )
    }
}

/** Botão circular do cabeçalho (busca / ajustes da lista). */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    tag: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) scheme.primaryContainer else scheme.surface)
            .border(1.dp, extra.lineSoft, CircleShape)
            .testTag(tag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) scheme.primary else extra.ink2,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Segmento de visão: selecionado em roxo da marca, com o contador. */
@Composable
private fun ViewSegment(
    label: String,
    count: Int,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) scheme.primary else Color.Transparent)
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = if (selected) scheme.onPrimary else extra.ink2,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = if (selected) {
                scheme.onPrimary.copy(alpha = 0.72f)
            } else {
                scheme.onSurfaceVariant
            },
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Cabeçalho de sessão da visão Hoje (mesmas cores/ícones da aba Hoje). */
@Composable
private fun SessionGroupHeader(
    label: String,
    session: Session?,
    doneCount: Int,
    totalCount: Int,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val (tile, tint, icon) = when (session) {
        Session.MANHA -> Triple(extra.tintAmber, extra.amberStrong, Icons.Filled.WbSunny)
        Session.TARDE -> Triple(extra.tintFlame, scheme.secondary, Icons.Filled.WbSunny)
        Session.NOITE -> Triple(extra.tintSun, scheme.primary, Icons.Filled.NightsStay)
        null -> Triple(extra.pageChip, extra.ink3, Icons.Filled.Schedule)
    }
    val range = when (session) {
        Session.MANHA -> "até 12h"
        Session.TARDE -> "12h – 18h"
        Session.NOITE -> "depois das 18h"
        null -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 20.dp, end = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
        )
        if (range.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = range,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.weight(1f))
        Text(
            text = doneCount.toString() + " de " + totalCount.toString(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = scheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TasksFilterSheet(
    sortMode: TaskSortMode,
    groupBy: TaskGroupBy,
    onDismiss: () -> Unit,
    onSortModeChange: (TaskSortMode) -> Unit,
    onGroupByChange: (TaskGroupBy) -> Unit,
    onShowTemplates: () -> Unit,
    onNewView: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ajustes da lista",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant)
                        .testTag("filters_close")
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Fechar ajustes",
                        tint = extra.ink2,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            FilterSectionLabel("ORDENAR POR")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterOptionChip(
                    label = "Inteligente",
                    selected = sortMode == TaskSortMode.SMART,
                    tag = "sort_SMART",
                    onClick = { onSortModeChange(TaskSortMode.SMART) },
                )
                FilterOptionChip(
                    label = "Prioridade",
                    selected = sortMode == TaskSortMode.PRIORITY,
                    tag = "sort_PRIORITY",
                    onClick = { onSortModeChange(TaskSortMode.PRIORITY) },
                )
                FilterOptionChip(
                    label = "Prazo",
                    selected = sortMode == TaskSortMode.DUE,
                    tag = "sort_DUE",
                    onClick = { onSortModeChange(TaskSortMode.DUE) },
                )
            }

            FilterSectionLabel("AGRUPAR POR")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterOptionChip(
                    label = "Sessão",
                    selected = groupBy == TaskGroupBy.SESSION,
                    tag = "group_SESSION",
                    onClick = { onGroupByChange(TaskGroupBy.SESSION) },
                )
                FilterOptionChip(
                    label = "Projeto",
                    selected = groupBy == TaskGroupBy.PROJECT,
                    tag = "group_PROJECT",
                    onClick = { onGroupByChange(TaskGroupBy.PROJECT) },
                )
                FilterOptionChip(
                    label = "Prioridade",
                    selected = groupBy == TaskGroupBy.PRIORITY,
                    tag = "group_PRIORITY",
                    onClick = { onGroupByChange(TaskGroupBy.PRIORITY) },
                )
                FilterOptionChip(
                    label = "Data",
                    selected = groupBy == TaskGroupBy.DATE,
                    tag = "group_DATE",
                    onClick = { onGroupByChange(TaskGroupBy.DATE) },
                )
                FilterOptionChip(
                    label = "Nenhum",
                    selected = groupBy == TaskGroupBy.NONE,
                    tag = "group_NONE",
                    onClick = { onGroupByChange(TaskGroupBy.NONE) },
                )
            }

            Spacer(Modifier.height(18.dp))
            FilterLinkRow(
                text = "Modelos de lista",
                tag = "filters_templates",
                onClick = onShowTemplates,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(extra.lineSoft),
            )
            FilterLinkRow(
                text = "Nova vista salva",
                tag = "filters_new_view",
                onClick = onNewView,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(extra.lineSoft),
            )

            FilterSectionLabel("CORES")
            Legend(includeTopPadding = false)
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun FilterOptionChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(
                1.5.dp,
                if (selected) scheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(99.dp),
            )
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = if (selected) scheme.primary else extra.ink2,
        )
    }
}

@Composable
private fun FilterLinkRow(text: String, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Legenda das cores: pontinhos de prioridade e de projeto, numa
 *  linha só (quebra se faltar espaço). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(includeTopPadding: Boolean = true) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 2.dp,
                top = if (includeTopPadding) 14.dp else 0.dp,
                end = 2.dp,
            ),
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
