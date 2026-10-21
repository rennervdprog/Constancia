package app.constancia.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.constancia.AppDeps
import app.constancia.domain.MOOD_LABELS
import app.constancia.domain.SleepLagPattern
import app.constancia.domain.dateLinePt
import app.constancia.domain.dayFlowTasks
import app.constancia.domain.diaryStreak
import app.constancia.domain.fmt1
import app.constancia.domain.greetingFor
import app.constancia.domain.isDueOn
import app.constancia.domain.sleepLagPattern
import app.constancia.domain.timeSortKey
import app.constancia.domain.weekFractions
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.HabitStatus
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.model.todayYmd
import app.constancia.model.toLocalDate
import app.constancia.model.isoDayNumberCompat
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.MoodFace
import app.constancia.ui.common.habitIcon
import app.constancia.ui.common.moodColor
import app.constancia.ui.diary.DiaryPhotoImage
import app.constancia.ui.tasks.TodoRowWithActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private fun sessionTitle(session: Session): String = when (session) {
    Session.MANHA -> "Manhã"
    Session.TARDE -> "Tarde"
    Session.NOITE -> "Noite"
}

private fun sessionIcon(session: Session) = when (session) {
    Session.MANHA -> Icons.Filled.WbSunny
    Session.TARDE -> Icons.Filled.WbSunny
    Session.NOITE -> Icons.Filled.NightsStay
}

// Formata quantidade sem casas decimais quando for inteira.
private fun fmtQty(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}

private fun createdFrom(habit: Habit) =
    runCatching { habit.createdAt.take(10).toLocalDate() }.getOrNull()

/**
 * Tela Hoje (protótipo turbinado aprovado): progresso do dia (só hábitos
 * devidos), cartão "Sua semana", cartão do diário e o fluxo "Seu dia" —
 * hábitos e tarefas misturados por sessão e horário.
 */
@Composable
fun HomeScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onOpenDiary: () -> Unit = {},
    onOpenReports: () -> Unit = {},
) {
    val today = remember { todayYmd() }
    val todayDate = remember { today.toLocalDate() }
    var selectedDate by remember { mutableStateOf(todayDate) }
    val selYmd = selectedDate.toString()
    val isToday = selectedDate == todayDate
    val scope = rememberCoroutineScope()
    var timerTarget by remember { mutableStateOf<HabitStatus?>(null) }
    var expandedTaskId by remember { mutableStateOf<String?>(null) }
    var quickText by remember { mutableStateOf("") }

    val statusFlow = remember(selectedDate) {
        if (isToday) deps.habits.todayStatusFlow()
        else deps.habits.statusForDateFlow(selYmd)
    }
    val statuses by statusFlow.collectAsState(initial = emptyList())
    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val allTasks by deps.tasks.allTasksFlow().collectAsState(initial = emptyList())
    val rangeFrom = remember { todayDate.minus(DatePeriod(days = 35)).toString() }
    val checkins by deps.checkins.checkinsInRangeFlow(rangeFrom, today)
        .collectAsState(initial = emptyList())
    val selEntry by remember(selectedDate) { deps.diary.entryForDateFlow(selYmd) }
        .collectAsState(initial = null)
    val diaryEntries by deps.diary.allEntriesFlow().collectAsState(initial = emptyList())
    val habitNames = habits.associate { it.id to it.name }

    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = greetingFor(hour)
    val dateLine = dateLinePt(todayDate)

    val active = statuses.filter { !it.habit.paused }
    val paused = statuses.filter { it.habit.paused }
    val dueToday = active.filter { isDueOn(it.habit, selectedDate, createdFrom(it.habit)) }
    val doneCount = dueToday.count { it.isDoneToday }
    val bestStreak = statuses.maxOfOrNull { it.currentStreak } ?: 0

    val dayTasks = dayFlowTasks(allTasks, selectedDate)
    val tasksDone = dayTasks.count { it.done }
    val dayDone = doneCount + tasksDone
    val dayTotal = dueToday.size + dayTasks.size

    val fractions = weekFractions(habits, checkins, todayDate)
    val streakDays = diaryStreak(diaryEntries.map { it.date }.toSet(), todayDate)
    val sleepPattern = sleepLagPattern(diaryEntries, allTasks, todayDate)

    fun quickAdd() {
        val title = quickText.trim()
        if (title.isEmpty()) return
        quickText = ""
        scope.launch { deps.tasks.quickAdd(title, dueDate = today) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = if (isToday) dateLine else "Você está vendo outro dia",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (isToday) greeting else dateLinePt(selectedDate),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }

            item {
                DayRingPanel(
                    habitsDone = doneCount,
                    habitsTotal = dueToday.size,
                    tasksDone = tasksDone,
                    tasksTotal = dayTasks.size,
                    diaryDone = selEntry != null,
                    bestStreak = bestStreak,
                    isToday = isToday,
                )
            }

            item {
                WeekBarsCard(
                    fractions = fractions,
                    todayDate = todayDate,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it },
                )
            }

            if (!isToday) {
                item(key = "back-today") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { selectedDate = todayDate }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Voltar para hoje",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                DiaryCard(
                    title = if (isToday) "Diário de hoje" else "Diário do dia",
                    hasEntry = selEntry != null,
                    mood = selEntry?.mood,
                    energy = selEntry?.energy,
                    note = selEntry?.note.orEmpty(),
                    photoUri = selEntry?.photoUri,
                    streak = streakDays,
                    onOpenDiary = onOpenDiary,
                )
            }

            if (habits.isEmpty() && dayTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(22.dp),
                            )
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                RoundedCornerShape(22.dp),
                            ),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = "Vamos começar?",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Você ainda não tem hábitos. Toque no botão + para criar o " +
                                    "primeiro — comece pequeno, um hábito que caiba no seu pior dia.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item(key = "dayflow-head") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, top = 16.dp, end = 2.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Seu dia",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = dayDone.toString() + " de " + dayTotal.toString() + " feitos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Session.entries.forEach { session ->
                val sessionHabits = dueToday.filter { it.habit.session == session }
                val sessionTasks = dayTasks.filter { it.session == session }
                if (sessionHabits.isNotEmpty() || sessionTasks.isNotEmpty()) {
                    val sessionDone = sessionHabits.count { it.isDoneToday } +
                        sessionTasks.count { it.done }
                    val sessionTotal = sessionHabits.size + sessionTasks.size
                    item(key = "sec-" + session.name) {
                        SessionHeader(
                            session = session,
                            done = sessionDone,
                            total = sessionTotal,
                        )
                    }
                    // Fluxo misto ordenado por horário (sem horário vai para o fim).
                    val flow: List<FlowItem> = (
                        sessionHabits.map { FlowItem.HabitItem(it) } +
                            sessionTasks.map { FlowItem.TaskFlowItem(it) }
                        ).sortedBy { it.sortKey }
                    flow.forEach { flowItem ->
                        when (flowItem) {
                            is FlowItem.HabitItem -> item(key = "habit-" + flowItem.status.habit.id) {
                                HabitCard(
                                    status = flowItem.status,
                                    today = selYmd,
                                    deps = deps,
                                    scope = scope,
                                    onOpenTimer = { timerTarget = flowItem.status },
                                )
                            }
                            is FlowItem.TaskFlowItem -> item(key = "task-" + flowItem.task.id) {
                                TodoRowWithActions(
                                    task = flowItem.task,
                                    today = selectedDate,
                                    habitName = flowItem.task.habitId?.let { habitNames[it] },
                                    expanded = expandedTaskId == flowItem.task.id,
                                    onToggleExpand = {
                                        expandedTaskId = if (expandedTaskId == flowItem.task.id) {
                                            null
                                        } else {
                                            flowItem.task.id
                                        }
                                    },
                                    deps = deps,
                                    scope = scope,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "quickadd") {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = quickText,
                            onValueChange = { if (it.length <= 60) quickText = it },
                            placeholder = { Text("Adicionar tarefa rápida…") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { quickAdd() }),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).testTag("task_input"),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .testTag("task_add")
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
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Hábitos e tarefas do dia, misturados por sessão e horário. " +
                            "A lista completa, estilo Todoist, fica na aba Tarefas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (paused.isNotEmpty()) {
                item(key = "paused") {
                    PausedSection(paused = paused, deps = deps, scope = scope)
                }
            }

            if (sleepPattern != null) {
                item(key = "pattern-detected") {
                    PatternDetectedCard(
                        pattern = sleepPattern,
                        onOpenReports = onOpenReports,
                    )
                }
            }
        }

        timerTarget?.let { target ->
            FocusTimerDialog(
                status = target,
                today = selYmd,
                deps = deps,
                onDismiss = { timerTarget = null },
            )
        }
    }
}

/** Item do fluxo misto do dia (hábito ou tarefa), ordenado por horário. */
private sealed interface FlowItem {
    val sortKey: String

    data class HabitItem(val status: HabitStatus) : FlowItem {
        override val sortKey: String
            get() = timeSortKey(
                if (status.habit.reminderEnabled) status.habit.reminderTime else null,
            ) + "0"
    }

    data class TaskFlowItem(val task: TaskItem) : FlowItem {
        override val sortKey: String
            get() = timeSortKey(task.dueTime) + "1"
    }
}

@Composable
private fun WeekBarsCard(
    fractions: List<Double>,
    todayDate: kotlinx.datetime.LocalDate,
    selectedDate: kotlinx.datetime.LocalDate,
    onSelectDate: (kotlinx.datetime.LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val labels = listOf("S", "T", "Q", "Q", "S", "S", "D")
    val todayIdx = todayDate.isoDayNumberCompat() - 1 // seg=0
    val monday = todayDate.minus(DatePeriod(days = todayIdx))
    val selectedIdx = selectedDate.toEpochDays() - monday.toEpochDays()
    val valid = fractions.filter { it >= 0.0 }
    val pct = if (valid.isEmpty()) null else (valid.average() * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(scheme.surface, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sua semana",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (pct != null) pct.toString() + "%" else "—",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = scheme.primary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            fractions.forEachIndexed { idx, frac ->
                val isToday = idx == todayIdx
                val isSelected = idx == selectedIdx
                val dayDate = monday.plus(DatePeriod(days = idx))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.surfaceVariant),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (frac >= 0.0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((7 + frac * 41).dp)
                                    .background(
                                        if (isToday) scheme.secondary else scheme.primary,
                                        RoundedCornerShape(8.dp),
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) scheme.primary else Color.Transparent,
                            )
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    when {
                                        isSelected -> scheme.primary
                                        isToday -> scheme.primary
                                        else -> Color.Transparent
                                    },
                                ),
                                CircleShape,
                            )
                            .clickable { onSelectDate(dayDate) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayDate.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = if (isSelected) scheme.onPrimary
                            else scheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = labels[idx],
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isToday) FontWeight.ExtraBold
                            else FontWeight.Normal,
                        ),
                        color = if (isToday) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (pct != null) {
                pct.toString() + "% da semana até aqui"
            } else {
                "Sem hábitos devidos nesta semana"
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiaryCard(
    title: String = "Diário de hoje",
    hasEntry: Boolean,
    mood: Int?,
    energy: Int?,
    note: String,
    photoUri: String?,
    streak: Int,
    onOpenDiary: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(scheme.surface, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Abrir diário ›",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .testTag("open_diary")
                    .clickable(onClick = onOpenDiary)
                    .padding(4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (hasEntry && mood != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .background(moodColor(mood).copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    MoodFace(mood = mood, size = 26.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Humor: " + MOOD_LABELS[(mood - 1).coerceIn(0, 4)] +
                            (energy?.let { " · Energia " + it + "/5" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (note.isNotBlank()) {
                        Text(
                            text = if (note.length > 88) note.take(88) + "…" else note,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                if (streak > 0) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.constanciaExtra.amberSoft,
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = streak.toString() + "d",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = scheme.secondary,
                        )
                    }
                }
            }
            if (photoUri != null) {
                Spacer(Modifier.height(12.dp))
                DiaryPhotoImage(photoUri = photoUri)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, scheme.outline, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenDiary)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Editar registro",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                )
            }
        } else {
            Text(
                text = "Como foi o seu dia?",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Humor, energia, sono e uma foto — um minuto e o dia fica registrado.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(scheme.primary)
                    .testTag("diary_write")
                    .clickable(onClick = onOpenDiary)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Escrever agora",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun DayProgressCard(doneCount: Int, total: Int, bestStreak: Int) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val progress = if (total == 0) 0f else doneCount.toFloat() / total.toFloat()
    val pct = (progress * 100).toInt()

    val bigText = when {
        total == 0 -> "Vamos começar?"
        doneCount == total -> "Dia concluído!"
        else -> "Bom ritmo!"
    }
    val subText = when {
        total == 0 -> "Crie seu primeiro hábito tocando no botão +."
        doneCount == total -> "Todos os hábitos de hoje foram concluídos."
        else -> "Toque no círculo de um hábito para marcar o dia."
    }

    // Cartão INVERTIDO: fundo ink, conteúdo bg. Raio 24dp, padding 18dp,
    // gap 16dp, margin-bottom 14dp (DESIGN_TOKENS.md).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(extra.progressCardBg, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(84.dp)) {
                val stroke = 9.dp.toPx()
                drawArc(
                    color = extra.progressCardContent.copy(alpha = 0.16f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = scheme.secondary,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = pct.toString() + "%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = extra.progressCardContent,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = doneCount.toString() + " de " + total,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = extra.progressCardContent.copy(alpha = 0.65f),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bigText,
                style = MaterialTheme.typography.titleLarge,
                color = extra.progressCardContent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = extra.progressCardContent.copy(alpha = 0.72f),
            )
            if (bestStreak > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(scheme.secondary.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Whatshot,
                        contentDescription = null,
                        tint = scheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = bestStreak.toString() + " dias seguidos",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = scheme.secondary,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SessionHeader(session: Session, done: Int, total: Int) {
    val scheme = MaterialTheme.colorScheme
    // Cabeçalho de sessão: margem 16/2/10dp (DESIGN_TOKENS.md).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 16.dp, end = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            sessionIcon(session),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = sessionTitle(session),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = done.toString() + " de " + total + " feitos",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HabitCard(
    status: HabitStatus,
    today: String,
    deps: AppDeps,
    scope: CoroutineScope,
    onOpenTimer: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val habit = status.habit
    val habitColor = Color(habit.color)
    val isQuantity = habit.goalType == GoalType.QUANTITY
    val currentQty = status.todayCheckin?.quantity ?: 0.0

    // habit-card: surface, borda 1dp line, raio 22dp, padding 14dp, gap 13dp,
    // margin-bottom 11dp (DESIGN_TOKENS.md). O stepper fica numa linha própria
    // abaixo (largura total) para nunca estourar em telas estreitas.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 11.dp)
            .background(scheme.surface, RoundedCornerShape(22.dp))
            .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            // Ícone do hábito: caixa 48dp, raio 15dp, ícone 24dp explícito.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(habitColor.copy(alpha = 0.16f), RoundedCornerShape(15.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    habitIcon(habit.icon),
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status.currentStreak > 0) {
                        Icon(
                            Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = scheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = status.currentStreak.toString() +
                                if (status.currentStreak == 1) " dia" else " dias",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = scheme.secondary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (habit.frequencyType == FrequencyType.WEEKLY_X) {
                        Text(
                            text = status.weekDone.toString() + "/" +
                                habit.weeklyTarget + " na semana",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onOpenTimer) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = "Timer de foco",
                    tint = scheme.onSurfaceVariant,
                )
            }
            CheckCircleButton(
                done = status.isDoneToday,
                onToggle = { scope.launch { deps.checkins.toggle(habit.id, today) } },
            )
        }
        if (isQuantity) {
            Spacer(Modifier.height(10.dp))
            QuantityStepper(
                current = currentQty,
                target = habit.targetQuantity,
                unit = habit.quantityUnit,
                onDecrease = {
                    val next = (currentQty - 1.0).coerceAtLeast(0.0)
                    scope.launch { deps.checkins.setQuantity(habit.id, today, next) }
                },
                onIncrease = {
                    scope.launch {
                        deps.checkins.setQuantity(habit.id, today, currentQty + 1.0)
                    }
                },
            )
        }
    }
}

@Composable
private fun QuantityStepper(
    current: Double,
    target: Double,
    unit: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Stepper do protótipo: pílula surface com borda 1.5dp line, raio 999dp,
    // padding 3dp; botões 30dp surface-2 com glifo 16sp/800; valor min 46dp
    // centralizado 13sp/800. Tamanhos fixos e simétricos — sem overflow.
    Row(
        modifier = Modifier
            .background(scheme.surface, RoundedCornerShape(999.dp))
            .border(BorderStroke(1.5.dp, scheme.outline), RoundedCornerShape(999.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(text = "−", onClick = onDecrease, tag = "qty_dec")
        Text(
            text = fmtQty(current) + "/" + fmtQty(target) + " " + unit,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            modifier = Modifier
                .widthIn(min = 46.dp)
                .padding(horizontal = 8.dp),
            maxLines = 1,
        )
        StepperButton(text = "+", onClick = onIncrease, tag = "qty_inc")
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit, tag: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun CheckCircleButton(done: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Micro-interação: pulso rápido ao concluir (e ao desmarcar).
    val pulse = remember { Animatable(1f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(done) {
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        pulse.snapTo(0.82f)
        pulse.animateTo(1.12f, tween(durationMillis = 140))
        pulse.animateTo(1f, tween(durationMillis = 200))
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .scale(pulse.value)
            .clip(CircleShape)
            .testTag("habit_check")
            .background(if (done) scheme.primary else Color.Transparent)
            .then(
                if (done) Modifier
                else Modifier.border(2.5.dp, scheme.outline, CircleShape),
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Concluído",
                tint = scheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PausedSection(
    paused: List<HabitStatus>,
    deps: AppDeps,
    scope: CoroutineScope,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(scheme.surface, RoundedCornerShape(22.dp))
            .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(22.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Em pausa",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "sequência protegida",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (expanded) "−" else "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                paused.forEach { status ->
                    val habit = status.habit
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    Color(habit.color).copy(alpha = 0.16f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                habitIcon(habit.icon),
                                contentDescription = null,
                                tint = Color(habit.color),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Retomar",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                            modifier = Modifier.clickable {
                                scope.launch { deps.habits.setPaused(habit.id, false) }
                            },
                        )
                    }
                }
            }
        }
    }
}

private val FOCUS_PRESETS = listOf(5, 10, 15, 25)

@Composable
private fun FocusTimerDialog(
    status: HabitStatus,
    today: String,
    deps: AppDeps,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val habit = status.habit
    var selectedMinutes by remember(habit.id) { mutableStateOf(25) }
    var remaining by remember(habit.id) { mutableStateOf(25 * 60) }
    var running by remember(habit.id) { mutableStateOf(false) }
    var finished by remember(habit.id) { mutableStateOf(false) }

    fun pickPreset(minutes: Int) {
        selectedMinutes = minutes
        remaining = minutes * 60
        running = false
        finished = false
    }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000L)
            remaining--
        }
        if (running && remaining == 0) {
            running = false
            finished = true
        }
    }

    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        if (!status.isDoneToday) {
            if (habit.goalType == GoalType.BINARY) {
                deps.checkins.toggle(habit.id, today)
            } else {
                deps.checkins.setQuantity(
                    habit.id,
                    today,
                    (status.todayCheckin?.quantity ?: 0.0) + 1.0,
                )
            }
        }
        onDismiss()
    }

    val minutes = (remaining / 60).toString().padStart(2, '0')
    val seconds = (remaining % 60).toString().padStart(2, '0')
    val stateText = when {
        finished -> "Concluído!"
        running -> "Focando…"
        remaining < selectedMinutes * 60 -> "Pausado"
        else -> "Pronto para começar"
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surface, RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Timer de foco",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = habit.name + " · ao terminar, o hábito é marcado sozinho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                // Segmentado (DESIGN_TOKENS.md): botão flex-1, padding 9/6dp, raio 10dp;
                // selecionado fundo surface com sombra leve e texto ink.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FOCUS_PRESETS.forEach { preset ->
                        val selected = preset == selectedMinutes
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (selected) {
                                        Modifier.shadow(
                                            elevation = 2.dp,
                                            shape = RoundedCornerShape(10.dp),
                                            spotColor = Color.Black.copy(alpha = 0.14f),
                                            ambientColor = Color.Black.copy(alpha = 0.14f),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .background(
                                    if (selected) scheme.surface else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { pickPreset(preset) }
                                .padding(horizontal = 6.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = preset.toString() + " min",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) scheme.onSurface
                                else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = minutes + ":" + seconds,
                    style = MaterialTheme.typography.displayLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                // CTA: largura total, raio 18dp, padding vertical 16dp,
                // fundo primary, texto onPrimary Sora 16/700.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.primary, RoundedCornerShape(18.dp))
                        .clickable { running = !running }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                running -> "Pausar"
                                remaining < selectedMinutes * 60 -> "Retomar foco"
                                else -> "Iniciar foco"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancelar",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Painel "Seu dia" (UI/UX elevada do protótipo aprovado): anel animado que
 * reage aos checks, contando hábitos devidos + tarefas do dia + diário.
 * Ao fechar 100%, o cartão vira celebração (fundo roxo profundo, chama
 * pulsando).
 */
@Composable
private fun DayRingPanel(
    habitsDone: Int,
    habitsTotal: Int,
    tasksDone: Int,
    tasksTotal: Int,
    diaryDone: Boolean,
    bestStreak: Int,
    isToday: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val total = habitsTotal + tasksTotal + 1
    val done = habitsDone + tasksDone + if (diaryDone) 1 else 0
    val frac = if (total > 0) done.toFloat() / total else 0f
    val celebrated = total > 0 && done >= total
    val animFrac by animateFloatAsState(
        targetValue = frac,
        animationSpec = tween(durationMillis = 700),
        label = "dayRing",
    )
    val title = when {
        celebrated -> "Dia fechado. Constância pura."
        frac >= 0.5f -> "Quase lá"
        frac > 0f -> "Bom começo"
        else -> "Vamos começar"
    }
    val missing = total - done
    val subtitle = when {
        celebrated ->
            if (isToday) "Tudo feito por hoje. Descanse." else "Tudo feito naquele dia."
        missing == 1 ->
            if (isToday) "Falta 1 item pra fechar o dia" else "Faltou 1 item naquele dia"
        else ->
            if (isToday) "Faltam $missing itens pra fechar o dia"
            else "Faltaram $missing itens naquele dia"
    }
    val content = if (celebrated) extra.progressCardContent else scheme.onSurface
    val subContent = if (celebrated) extra.progressCardContent.copy(alpha = 0.75f)
    else scheme.onSurfaceVariant
    val trackColor = if (celebrated) extra.progressCardContent.copy(alpha = 0.22f)
    else extra.heat0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(
                if (celebrated) extra.progressCardBg else scheme.surface,
                RoundedCornerShape(26.dp),
            )
            .border(
                BorderStroke(1.dp, if (celebrated) extra.progressCardBg else scheme.outline),
                RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(94.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(94.dp)) {
                drawCircle(
                    color = trackColor,
                    style = Stroke(width = 9.dp.toPx()),
                )
                if (animFrac > 0f) {
                    drawArc(
                        color = if (celebrated) scheme.secondary else scheme.primary,
                        startAngle = -90f,
                        sweepAngle = 360f * animFrac,
                        useCenter = false,
                        style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = (animFrac * 100).roundToInt().toString() + "%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = content,
                )
                Text(
                    text = "do dia",
                    style = MaterialTheme.typography.labelSmall,
                    color = subContent,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                ),
                color = content,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subContent,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = "Hábitos $habitsDone de $habitsTotal · Tarefas $tasksDone de " +
                    "$tasksTotal · Diário " + if (diaryDone) "feito" else "por fazer",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = content,
            )
            if (bestStreak > 0) {
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (celebrated) scheme.secondary.copy(alpha = 0.18f)
                            else extra.amber.copy(alpha = 0.14f),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val flameScale = if (celebrated) {
                        val transition = rememberInfiniteTransition(label = "flame")
                        val s by transition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.22f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 620),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "flameScale",
                        )
                        s
                    } else {
                        1f
                    }
                    Icon(
                        Icons.Filled.Whatshot,
                        contentDescription = null,
                        tint = scheme.secondary,
                        modifier = Modifier.size(15.dp).scale(flameScale),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = bestStreak.toString() +
                            if (bestStreak == 1) " dia de sequência" else " dias de sequência",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = if (celebrated) content else extra.amber,
                    )
                }
            }
            if (celebrated && isToday) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Você fechou tudo que se propôs hoje.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = content,
                )
            }
        }
    }
}

/** Cartão "Padrão detectado" no fim da Hoje — ponte para os Relatórios. */
@Composable
private fun PatternDetectedCard(pattern: SleepLagPattern, onOpenReports: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 14.dp)
            .background(scheme.surface, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.NightsStay,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Padrão detectado",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sono × dia seguinte",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        Spacer(Modifier.height(5.dp))
        val body = buildString {
            pattern.overdueRatio?.let { r ->
                append("Em noites com menos de 6h30 de sono, você atrasa ")
                append(fmt1(r) + "× mais tarefas no dia seguinte. ")
            }
            val ms = pattern.moodAfterShort
            val me = pattern.moodAfterEnough
            if (ms != null && me != null) {
                append("Humor médio: " + fmt1(ms) + " após noite curta × ")
                append(fmt1(me) + " após noite boa.")
            }
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = extra.ink2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Base: ${pattern.shortNights} noites curtas × " +
                "${pattern.enoughNights} noites boas · associação, não causa.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenReports)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ver relatórios",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
