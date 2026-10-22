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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.constancia.AppDeps
import app.constancia.domain.DIARY_PROMPTS
import app.constancia.domain.MOOD_LABELS
import app.constancia.domain.SleepLagPattern
import app.constancia.domain.dateLinePt
import app.constancia.domain.dayFlowTasks
import app.constancia.domain.diaryStreak
import app.constancia.domain.dueLabel
import app.constancia.domain.fmt1
import app.constancia.domain.greetingFor
import app.constancia.domain.isDueOn
import app.constancia.domain.prioName
import app.constancia.domain.promptIndexFor
import app.constancia.domain.sleepLagPattern
import app.constancia.domain.subtaskProgress
import app.constancia.domain.timeSortKey
import app.constancia.domain.weekFractions
import app.constancia.domain.withOccurrenceHistory
import app.constancia.model.CheckinStatus
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.habitActiveFrom
import app.constancia.model.HabitStatus
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.todayYmd
import app.constancia.model.toLocalDate
import app.constancia.model.isoDayNumberCompat
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.habitColorOf
import app.constancia.ui.common.habitIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private fun sessionTitle(session: Session): String = when (session) {
    Session.MANHA -> "Manhã"
    Session.TARDE -> "Tarde"
    Session.NOITE -> "Noite"
}

private fun sessionRange(session: Session): String = when (session) {
    Session.MANHA -> "até 12h"
    Session.TARDE -> "12h – 18h"
    Session.NOITE -> "depois das 18h"
}

// Formata quantidade sem casas decimais quando for inteira.
private fun fmtQty(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}

private fun createdFrom(habit: Habit) = habitActiveFrom(habit.createdAt)

/** Frequência curta da linha do hábito ("todo dia", "seg, qua e sex"). */
private fun freqShort(habit: Habit, weekDone: Int): String = when (habit.frequencyType) {
    FrequencyType.DAILY -> "todo dia"
    FrequencyType.WEEKLY_X -> habit.weeklyTarget.toString() + "x por semana"
    FrequencyType.DAYS_OF_WEEK -> habit.daysOfWeek
        .mapNotNull {
            listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb").getOrNull(it - 1)
        }
        .joinToString(", ")
        .ifBlank { "dias fixos" }
}

/**
 * Tela Hoje (protótipo "Roxo Profundo" aprovado): saudação com avatar e
 * sino, painel-herói roxo "Seu dia" com anel e breakdown, semana em
 * pílulas, sessões (Manhã/Tarde/Noite) com linhas em cartão único —
 * hábitos, tarefas e a linha do diário na Noite — e o cartão de padrão
 * detectado no fim. A navegação por dia é pelas pílulas da semana.
 */
@Composable
fun HomeScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onOpenDiary: (String) -> Unit = {},
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

    val statusFlow = remember(selectedDate) {
        if (isToday) deps.habits.todayStatusFlow()
        else deps.habits.statusForDateFlow(selYmd)
    }
    val statuses by statusFlow.collectAsState(initial = emptyList())
    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val allTasks by deps.tasks.allTasksFlow().collectAsState(initial = emptyList())
    // Fase B (R1.1): as contagens de conclusão leem também o histórico
    // de ocorrências (conclusões preservadas sem a linha viva).
    val taskHistory by deps.tasks.occurrenceHistoryFlow()
        .collectAsState(initial = emptyList())
    val rangeFrom = remember { todayDate.minus(DatePeriod(days = 35)).toString() }
    val checkins by deps.checkins.checkinsInRangeFlow(rangeFrom, today)
        .collectAsState(initial = emptyList())
    val selEntry by remember(selectedDate) { deps.diary.entryForDateFlow(selYmd) }
        .collectAsState(initial = null)
    val diaryEntries by deps.diary.allEntriesFlow().collectAsState(initial = emptyList())
    val habitNames = habits.associate { it.id to it.name }
    // Item 2: meta diária de tarefas + pausas (cartão sob o herói).
    val dailyGoal by deps.prefs.taskDailyGoalFlow()
        .collectAsState(initial = 0)
    val goalPauses by deps.tasks.goalPausesFlow()
        .collectAsState(initial = emptyList())
    // Fase D (R10.1): dias de descanso fixos da meta (ISO 1..7).
    val goalIgnoreWeekdays by deps.prefs.goalIgnoreWeekdaysFlow()
        .collectAsState(initial = emptySet())

    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = greetingFor(hour)

    val active = statuses.filter { !it.habit.paused }
    val paused = statuses.filter { it.habit.paused }
    // TickTick item 4: hábito com opt-in "sempre no Hoje" aparece no
    // fluxo do dia mesmo fora dos dias devidos (marcável direto daqui).
    val visibleHabits = active.filter {
        it.habit.showInToday || isDueOn(it.habit, selectedDate, createdFrom(it.habit))
    }
    val doneCount = visibleHabits.count { it.isDoneToday }
    val bestStreak = statuses.maxOfOrNull { it.currentStreak } ?: 0

    val dayTasks = dayFlowTasks(allTasks, selectedDate)
    val tasksDone = dayTasks.count { it.done }

    val fractions = weekFractions(habits, checkins, todayDate)
    val sleepPattern = sleepLagPattern(diaryEntries, allTasks, todayDate)
    val diaryPrompt = DIARY_PROMPTS[
        promptIndexFor(selectedDate) % DIARY_PROMPTS.size
    ]

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("home_list"),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 10.dp, bottom = 118.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                GreetHeader(
                    dateLine = if (isToday) {
                        dateLinePt(todayDate)
                    } else {
                        dateLinePt(selectedDate)
                    },
                    greeting = if (isToday) greeting else dateLinePt(selectedDate)
                        .replaceFirstChar { it.uppercase() },
                    hasPattern = sleepPattern != null,
                    onBellClick = onOpenReports,
                )
            }

            item {
                HeroPanel(
                    habitsDone = doneCount,
                    habitsTotal = visibleHabits.size,
                    tasksDone = tasksDone,
                    tasksTotal = dayTasks.size,
                    diaryDone = selEntry != null,
                    bestStreak = bestStreak,
                    isToday = isToday,
                    onOpenDiary = { onOpenDiary(selYmd) },
                )
            }

            if (isToday && dailyGoal > 0) {
                item {
                    val goalCounts =
                        app.constancia.domain.completionCountsByDay(
                            withOccurrenceHistory(allTasks, taskHistory),
                        )
                    val metDates = app.constancia.domain.dailyGoalMetDates(
                        goalCounts, dailyGoal,
                    )
                    // R10.1: pausas + dias de descanso fixos perdoam.
                    val forgiven =
                        app.constancia.domain.goalForgivenDates(
                            goalPauses, today, goalIgnoreWeekdays,
                        )
                    // "Em pausa hoje" é só a pausa de verdade; o dia de
                    // descanso tem texto próprio no cartão.
                    val pauseForgiven =
                        app.constancia.domain.goalForgivenDates(
                            goalPauses, today,
                        )
                    // R10.2: a sequência da meta como objeto (c/ recorde).
                    val goalInfo = app.constancia.domain.dailyGoalStreakInfo(
                        metDates, forgiven, today,
                    )
                    TaskGoalCard(
                        doneToday = goalCounts[today] ?: 0,
                        goal = dailyGoal,
                        streak = goalInfo.count,
                        streakCaption = app.constancia.domain
                            .streakCaption(goalInfo),
                        pausedToday = pauseForgiven.contains(today),
                        restToday = todayDate.isoDayNumberCompat() in
                            goalIgnoreWeekdays,
                    )
                }
            }

            item {
                WeekPills(
                    fractions = fractions,
                    todayDate = todayDate,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it },
                )
            }

            Session.entries.forEach { session ->
                val sessionHabits = visibleHabits.filter { it.habit.session == session }
                val sessionTasks = dayTasks.filter { it.session == session }
                val showDiaryRow = session == Session.NOITE
                if (sessionHabits.isNotEmpty() || sessionTasks.isNotEmpty() || showDiaryRow) {
                    val sessionDone = sessionHabits.count { it.isDoneToday } +
                        sessionTasks.count { it.done } +
                        if (showDiaryRow && selEntry != null) 1 else 0
                    val sessionTotal = sessionHabits.size + sessionTasks.size +
                        if (showDiaryRow) 1 else 0
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
                    item(key = "card-" + session.name) {
                        SessionRowsCard {
                            flow.forEachIndexed { index, flowItem ->
                                if (index > 0) RowDivider()
                                when (flowItem) {
                                    is FlowItem.HabitItem -> HabitSessionRow(
                                        status = flowItem.status,
                                        dateYmd = selYmd,
                                        deps = deps,
                                        scope = scope,
                                        onOpenTimer = { timerTarget = flowItem.status },
                                    )
                                    is FlowItem.TaskFlowItem -> TaskSessionRow(
                                        task = flowItem.task,
                                        today = selectedDate,
                                        habitName = flowItem.task.habitId
                                            ?.let { habitNames[it] },
                                        expanded = expandedTaskId == flowItem.task.id,
                                        onToggleExpand = {
                                            expandedTaskId =
                                                if (expandedTaskId == flowItem.task.id) {
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
                            if (showDiaryRow) {
                                if (flow.isNotEmpty()) RowDivider()
                                DiarySessionRow(
                                    hasEntry = selEntry != null,
                                    mood = selEntry?.mood,
                                    energy = selEntry?.energy,
                                    prompt = diaryPrompt,
                                    onOpen = { onOpenDiary(selYmd) },
                                )
                            }
                        }
                    }
                }
            }

            if (paused.isNotEmpty()) {
                item(key = "paused") {
                    PausedSection(paused = paused, deps = deps, scope = scope)
                }
            }

            if (sleepPattern != null) {
                item(key = "pattern-detected") {
                    InsightCard(
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

/**
 * Cabeçalho da Hoje (.greet do protótipo): avatar em gradiente roxo
 * 46dp raio 16, data em muted capitalizada + saudação Sora 19/700 e o
 * botão de sino 42dp à direita (o ponto chama aparece quando há um
 * padrão detectado; tocar abre os relatórios).
 */
@Composable
private fun GreetHeader(
    dateLine: String,
    greeting: String,
    hasPattern: Boolean,
    onBellClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_header")
            .padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = Color(0xFF38066E).copy(alpha = 0.4f),
                    ambientColor = Color(0xFF38066E).copy(alpha = 0.4f),
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF5B21B6), Color(0xFF38106E)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLine.split(" ").joinToString(" ") {
                    it.replaceFirstChar { c -> c.uppercase() }
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = greeting,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 19.sp),
                color = scheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = Color(0xFF26104A).copy(alpha = 0.14f),
                    ambientColor = Color(0xFF26104A).copy(alpha = 0.14f),
                )
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surface)
                .border(1.dp, extra.lineSoft, RoundedCornerShape(14.dp))
                .clickable(onClick = onBellClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "Padrões e relatórios",
                tint = extra.ink2,
                modifier = Modifier.size(20.dp),
            )
            if (hasPattern) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-9).dp, y = 9.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(scheme.secondary)
                        .border(2.dp, scheme.surface, CircleShape),
                )
            }
        }
    }
}

/**
 * Painel-herói "Seu dia" do protótipo: gradiente roxo profundo
 * (#4B1D96 → #38106E → #1E0740) com círculos decorativos translúcidos,
 * chip de sequência em vidro, anel de 104dp em lavanda (#D3BDFB) com
 * "% DO DIA", breakdown em tiles translúcidos e o botão branco
 * "Registrar o dia de hoje". Conta hábitos devidos + tarefas + diário.
 */
@Composable
private fun HeroPanel(
    habitsDone: Int,
    habitsTotal: Int,
    tasksDone: Int,
    tasksTotal: Int,
    diaryDone: Boolean,
    bestStreak: Int,
    isToday: Boolean,
    onOpenDiary: () -> Unit,
) {
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
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = shape,
                spotColor = Color(0xFF2A0A54).copy(alpha = 0.55f),
                ambientColor = Color(0xFF2A0A54).copy(alpha = 0.55f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4B1D96),
                        Color(0xFF38106E),
                        Color(0xFF1E0740),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(420f, 620f),
                ),
            ),
    ) {
        // Círculos decorativos (anéis translúcidos do protótipo).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-90).dp)
                .size(250.dp)
                .border(34.dp, Color.White.copy(alpha = 0.07f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-26).dp, y = 120.dp)
                .size(190.dp)
                .border(26.dp, Color.White.copy(alpha = 0.05f), CircleShape),
        )
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SEU DIA",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.12.em,
                        ),
                        color = Color(0xFFC9AFF7),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 21.sp,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFFD9C8F8),
                    )
                }
                if (bestStreak > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(99.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                            tint = Color(0xFFFFB37A),
                            modifier = Modifier.size(14.dp).scale(flameScale),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = bestStreak.toString() +
                                if (bestStreak == 1) " dia" else " dias",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = Color(0xFFDECFFB),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Em telas estreitas o anel encolhe p/ o breakdown não apertar
            // os rótulos ("Hábitos" não pode quebrar no meio da palavra).
            BoxWithConstraints {
                val ringSize = if (maxWidth < 300.dp) 88.dp else 104.dp
                Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(ringSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(ringSize)) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            style = Stroke(width = 9.dp.toPx()),
                        )
                        if (animFrac > 0f) {
                            drawArc(
                                color = Color(0xFFD3BDFB),
                                startAngle = -90f,
                                sweepAngle = 360f * animFrac,
                                useCenter = false,
                                style = Stroke(
                                    width = 9.dp.toPx(),
                                    cap = StrokeCap.Round,
                                ),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = (animFrac * 100).roundToInt().toString() + "%",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                        )
                        Text(
                            text = "DO DIA",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.08.em,
                            ),
                            color = Color(0xFFD8C6FB),
                        )
                    }
                }
                Spacer(Modifier.width(if (ringSize < 104.dp) 12.dp else 18.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    HeroStat(
                        icon = Icons.Filled.CheckCircle,
                        label = "Hábitos",
                        value = "$habitsDone de $habitsTotal",
                    )
                    HeroStat(
                        icon = Icons.Filled.FormatListBulleted,
                        label = "Tarefas",
                        value = "$tasksDone de $tasksTotal",
                    )
                    HeroStat(
                        icon = Icons.Filled.MenuBook,
                        label = "Diário",
                        value = if (diaryDone) "feito" else "por fazer",
                    )
                }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable(onClick = onOpenDiary)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        diaryDone && isToday -> "Editar o diário de hoje"
                        diaryDone -> "Editar o diário do dia"
                        isToday -> "Registrar o dia de hoje"
                        else -> "Registrar o dia"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = Color(0xFF2A0A54),
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF2A0A54),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Linha do breakdown do herói: tile translúcido + rótulo + contador. */
@Composable
private fun HeroStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
            color = Color(0xFFF3EDFC),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = Color(0xFFC9AFF7),
        )
    }
}

/**
 * Semana em PÍLULAS do protótipo: dia da semana + número, selecionada em
 * roxo com sombra, pontinho sob os dias com registro (chama quando o dia
 * foi fechado). Sem cartão em volta, sem barras.
 */
@Composable
private fun WeekPills(
    fractions: List<Double>,
    todayDate: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val labels = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
    val todayIdx = todayDate.isoDayNumberCompat() - 1 // seg = 0
    val monday = todayDate.minus(DatePeriod(days = todayIdx))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        fractions.forEachIndexed { idx, frac ->
            val dayDate = monday.plus(DatePeriod(days = idx))
            val isSelected = dayDate == selectedDate
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected) {
                            Modifier.shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = scheme.primary.copy(alpha = 0.5f),
                                ambientColor = scheme.primary.copy(alpha = 0.5f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) scheme.primary else scheme.surface)
                    .then(
                        if (isSelected) {
                            Modifier
                        } else {
                            Modifier.border(
                                1.dp, extra.lineSoft, RoundedCornerShape(16.dp),
                            )
                        },
                    )
                    .clickable { onSelectDate(dayDate) }
                    .padding(top = 9.dp, bottom = 8.dp),
            ) {
                Text(
                    text = labels[idx],
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = if (isSelected) {
                        Color(0xFFD8C6FB)
                    } else {
                        scheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dayDate.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = if (isSelected) Color.White else scheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                frac < 0.0 -> Color.Transparent
                                isSelected -> Color(0xFFD3BDFB)
                                frac >= 1.0 -> scheme.secondary
                                else -> scheme.primary
                            },
                        ),
                )
            }
        }
    }
}

/**
 * Cabeçalho de sessão do protótipo: tile de ícone 30dp na cor suave da
 * sessão (âmbar Manhã, chama Tarde, lavanda Noite), nome Sora, faixa de
 * horário em muted e o contador "N de M" em roxo à direita.
 */
@Composable
private fun SessionHeader(session: Session, done: Int, total: Int) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val (tint, iconColor, icon) = when (session) {
        Session.MANHA -> Triple(extra.tintAmber, extra.amberStrong, Icons.Filled.WbSunny)
        Session.TARDE -> Triple(extra.tintFlame, scheme.secondary, Icons.Filled.WbSunny)
        Session.NOITE -> Triple(extra.tintSun, scheme.primary, Icons.Filled.NightsStay)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 20.dp, end = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = sessionTitle(session),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.5.sp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = sessionRange(session),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = done.toString() + " de " + total.toString(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = scheme.primary,
        )
    }
}

/** Cartão único da sessão (.rows do protótipo): surface, raio 24,
 *  borda line-soft e divisórias finas entre as linhas. */
@Composable
private fun SessionRowsCard(
    content: @Composable ColumnScope.() -> Unit,
) {
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

/** Chip de horário da linha (lembrete do hábito ou hora da tarefa). */
@Composable
private fun TimeChip(time: String) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant)
            .border(1.dp, extra.lineSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = extra.ink2,
        )
    }
}

/**
 * Botão de check circular do protótipo: 34dp, borda 2.5dp #C9BCE0
 * quando pendente; preenchido em roxo com sombra e check branco quando
 * feito (com o pulso rápido ao alternar).
 */
@Composable
private fun CheckCircleButton(
    done: Boolean,
    onToggle: () -> Unit,
    tag: String = "habit_check",
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
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
            .size(34.dp)
            .scale(pulse.value)
            .then(
                if (done) {
                    Modifier.shadow(
                        elevation = 7.dp,
                        shape = CircleShape,
                        spotColor = scheme.primary.copy(alpha = 0.45f),
                        ambientColor = scheme.primary.copy(alpha = 0.45f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .testTag(tag)
            .background(if (done) scheme.primary else Color.Transparent)
            .then(
                if (done) Modifier
                else Modifier.border(2.5.dp, extra.checkBorder, CircleShape),
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Concluído",
                tint = scheme.onPrimary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/**
 * Linha de hábito na sessão (.row do protótipo): tile 42dp na cor
 * própria do hábito, título (riscado quando feito), sublinha com chama
 * da sequência + frequência, chip de horário do lembrete, timer de foco
 * discreto e o check circular. Hábitos de quantidade ganham o stepper
 * logo abaixo da sublinha.
 */
@Composable
private fun HabitSessionRow(
    status: HabitStatus,
    dateYmd: String,
    deps: AppDeps,
    scope: CoroutineScope,
    onOpenTimer: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val habit = status.habit
    val habitColor = habitColorOf(habit.color)
    val isQuantity = habit.goalType == GoalType.QUANTITY
    val currentQty = status.todayCheckin?.quantity ?: 0.0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(habitColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    habitIcon(habit.icon),
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (status.isDoneToday) {
                        scheme.onSurfaceVariant
                    } else {
                        scheme.onSurface
                    },
                    textDecoration = if (status.isDoneToday) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status.currentStreak > 0) {
                        Icon(
                            Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = scheme.secondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = status.currentStreak.toString() +
                                if (status.currentStreak == 1) " dia" else " dias",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = scheme.secondary,
                        )
                        Text(
                            text = " · " + freqShort(habit, status.weekDone),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                            ),
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = freqShort(habit, status.weekDone),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                            ),
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenTimer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = "Timer de foco",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            if (habit.reminderEnabled && habit.reminderTime != null) {
                TimeChip(habit.reminderTime!!)
            }
            CheckCircleButton(
                done = status.isDoneToday,
                onToggle = { scope.launch { deps.checkins.toggle(habit.id, dateYmd) } },
            )
        }
        if (isQuantity) {
            Spacer(Modifier.height(9.dp))
            Box(modifier = Modifier.padding(start = 54.dp)) {
                QuantityStepper(
                    current = currentQty,
                    target = habit.targetQuantity,
                    unit = habit.quantityUnit,
                    onDecrease = {
                        val next = (currentQty - habit.quantityStep).coerceAtLeast(0.0)
                        scope.launch { deps.checkins.setQuantity(habit.id, dateYmd, next) }
                    },
                    onIncrease = {
                        scope.launch {
                            deps.checkins.setQuantity(
                                habit.id, dateYmd, currentQty + habit.quantityStep,
                            )
                        }
                    },
                )
            }
        }
        // TickTick item 4: pular o dia é um registro — não quebra a
        // sequência. Some quando o dia já está feito.
        val skippedDay = status.todayCheckin?.status == CheckinStatus.SKIPPED
        if (skippedDay || !status.isDoneToday) {
            HabitSkipRow(
                skipped = skippedDay,
                onSkip = { scope.launch { deps.checkins.skip(habit.id, dateYmd) } },
                onUnskip = { scope.launch { deps.checkins.unskip(habit.id, dateYmd) } },
            )
        }
    }
}

/**
 * Linha do "pular" na Hoje: chip "Pulado hoje" + Desfazer quando o dia
 * foi pulado; ação discreta "Pular hoje" quando ainda não foi feito.
 */
@Composable
private fun HabitSkipRow(
    skipped: Boolean,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = 54.dp, top = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (skipped) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "Pulado hoje — sequência protegida",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Desfazer",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onUnskip)
                    .padding(4.dp),
            )
        } else {
            Text(
                text = "Pular hoje",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSkip)
                    .padding(4.dp),
            )
        }
    }
}

/** Ícone do tile da tarefa na Hoje, pela área (projeto) dela. */
private fun projectIcon(project: TaskProject) = when (project) {
    TaskProject.TRABALHO -> Icons.Filled.Work
    TaskProject.CASA -> Icons.Filled.Home
    TaskProject.PESSOAL -> Icons.Filled.Person
    TaskProject.SAUDE -> Icons.Filled.Favorite
    TaskProject.ENTRADA -> Icons.Filled.Inbox
}

/**
 * Linha de tarefa na sessão (.row do protótipo): tile na cor do projeto,
 * título, sublinha ("Atrasada · ontem · Trabalho · P1" com o trecho de
 * atraso em vermelho), chip de horário e check circular. Tocar expande
 * subtarefas e ações rápidas, como na aba Tarefas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskSessionRow(
    task: TaskItem,
    today: LocalDate,
    habitName: String?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    deps: AppDeps,
    scope: CoroutineScope,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val projectColor = Color(task.project.colorHex)
    val isOverdue = !task.done && task.dueDate != null && task.dueDate < today.toString()
    val progress = subtaskProgress(task.subtasks)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(projectColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    projectIcon(task.project),
                    contentDescription = null,
                    tint = projectColor,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (task.done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (task.done) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOverdue) {
                        Text(
                            text = "Atrasada · " +
                                (dueLabel(task, today)?.lowercase() ?: ""),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = extra.red,
                        )
                        Text(
                            text = " · " + task.project.label +
                                if (task.priority == 1) " · P1" else "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                            ),
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = task.project.label +
                                (if (task.priority == 1) " · P1" else "") +
                                (habitName?.let { " · hábito $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                            ),
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (task.dueTime != null) {
                TimeChip(task.dueTime!!)
            }
            CheckCircleButton(
                done = task.done,
                onToggle = {
                    scope.launch { deps.tasks.setDone(task.id, !task.done) }
                },
                tag = "task_check",
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(
                    start = 69.dp, end = 15.dp, bottom = 13.dp,
                ),
            ) {
                task.subtasks.forEach { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch {
                                    deps.tasks.setSubtaskDone(sub.id, !sub.done)
                                }
                            }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (sub.done) scheme.primary else Color.Transparent,
                                )
                                .then(
                                    if (sub.done) Modifier
                                    else Modifier.border(
                                        2.dp, extra.checkBorder, CircleShape,
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sub.done) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = scheme.onPrimary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = sub.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (sub.done) {
                                scheme.onSurfaceVariant
                            } else {
                                scheme.onSurface
                            },
                            textDecoration = if (sub.done) {
                                TextDecoration.LineThrough
                            } else {
                                null
                            },
                        )
                    }
                }
                if (progress != null) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(scheme.outline),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.pct / 100f)
                                .height(5.dp)
                                .background(scheme.primary, RoundedCornerShape(99.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!task.done) {
                        MiniAct(text = "Adiar p/ amanhã") {
                            val next = task.dueDate?.toLocalDate()
                                ?.plus(DatePeriod(days = 1))
                                ?: today.plus(DatePeriod(days = 1))
                            scope.launch { deps.tasks.setDueDate(task.id, next.toString()) }
                        }
                        MiniAct(text = "Prioridade: " + prioName(task.priority)) {
                            scope.launch {
                                deps.tasks.setPriority(
                                    task.id,
                                    app.constancia.domain.cyclePriority(task.priority),
                                )
                            }
                        }
                        MiniAct(text = "Excluir", danger = true) {
                            scope.launch { deps.tasks.delete(task.id) }
                        }
                    }
                }
            }
        }
    }
}

/** Açãozinha em pílula (.mini-act do protótipo). */
@Composable
private fun MiniAct(
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (danger) extra.redSoft else scheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = if (danger) extra.red else scheme.primary,
        )
    }
}

/**
 * Linha do diário na sessão Noite (.row do protótipo): tile lavanda com
 * lápis, "Escrever no diário", a pergunta do dia na sublinha e a ação
 * "Abrir". Com registro, vira o resumo do dia escrito.
 */
@Composable
private fun DiarySessionRow(
    hasEntry: Boolean,
    mood: Int?,
    energy: Int?,
    prompt: String,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(extra.tintSun),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                tint = extra.sunColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (hasEntry) "Diário do dia" else "Escrever no diário",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (hasEntry) {
                    "Humor: " + MOOD_LABELS[(mood ?: 3).coerceIn(1, 5) - 1] +
                        (energy?.let { " · energia $it/5" } ?: "")
                } else {
                    "Pergunta do dia: “$prompt”"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = scheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primaryContainer)
                .testTag("open_diary")
                .clickable(onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Abrir",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = scheme.primary,
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
private fun PausedSection(
    paused: List<HabitStatus>,
    deps: AppDeps,
    scope: CoroutineScope,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .background(scheme.surface, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, extra.lineSoft), RoundedCornerShape(24.dp)),
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
                                    habitColorOf(habit.color).copy(alpha = 0.15f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                habitIcon(habit.icon),
                                contentDescription = null,
                                tint = habitColorOf(habit.color),
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
                    (status.todayCheckin?.quantity ?: 0.0) + habit.quantityStep,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.primary, RoundedCornerShape(15.dp))
                        .clickable { running = !running }
                        .padding(vertical = 15.dp),
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
                            style = MaterialTheme.typography.titleMedium,
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
 * Cartão de padrão no fim da Hoje (.insight do protótipo): seção
 * "Padrão detectado" com link pros relatórios e o cartão em gradiente
 * roxo escuro — selo "SONO × DIA SEGUINTE", título e o texto do padrão.
 */
@Composable
private fun InsightCard(pattern: SleepLagPattern, onOpenReports: () -> Unit) {
    val goodDay = (pattern.overdueRatio ?: 1.0) >= 1.3 ||
        (pattern.moodAfterShort != null && pattern.moodAfterEnough != null &&
            (pattern.moodAfterEnough ?: 0.0) - (pattern.moodAfterShort ?: 0.0) > 0.3)
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
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Padrão detectado",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Ver relatórios",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenReports)
                    .padding(4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF3B1478), Color(0xFF22094A)),
                        start = Offset(0f, 0f),
                        end = Offset(380f, 420f),
                    ),
                )
                .clickable(onClick = onOpenReports)
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFB794F0),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SONO × DIA SEGUINTE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.12.em,
                        ),
                        color = Color(0xFFB794F0),
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = if (goodDay) {
                        "Dormir cedo rende um amanhã melhor"
                    } else {
                        "Suas noites e seus dias, lado a lado"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.5.sp,
                    ),
                    color = Color.White,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 21.sp,
                    ),
                    color = Color(0xFFDCCFFB),
                )
            }
        }
    }
}

/**
 * Item 2 — cartão da meta diária de tarefas (sem pontos/Karma): quanto
 * já foi concluído hoje, a barra de progresso, a sequência de dias com
 * a meta batida e o aviso de pausa (pausar não quebra a sequência).
 * Some quando a meta está desligada (ajuste em Mais → Ajustes).
 */
@Composable
private fun TaskGoalCard(
    doneToday: Int,
    goal: Int,
    streak: Int,
    pausedToday: Boolean,
    // Fase D: legenda da sequência como objeto ("desde 3 de out ·
    // recorde 21") e o dia de descanso fixo da meta (R10.1).
    streakCaption: String? = null,
    restToday: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val met = doneToday >= goal
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (met && !pausedToday) {
                    scheme.primaryContainer
                } else {
                    scheme.surface
                },
            )
            .border(
                1.dp,
                if (met && !pausedToday) {
                    scheme.primaryContainer
                } else {
                    scheme.outline
                },
                RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Whatshot,
                contentDescription = null,
                tint = extra.amber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Meta de tarefas",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 15.sp,
                ),
                modifier = Modifier.weight(1f),
            )
            if (streak > 0) {
                Text(
                    text = streak.toString() +
                        if (streak == 1) " dia" else " dias",
                    style = MaterialTheme.typography.labelLarge,
                    color = extra.amber,
                )
            }
        }
        // R10.2: desde quando a sequência vem + o recorde dela.
        if (streakCaption != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = streakCaption,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            pausedToday -> Text(
                text = "Meta em pausa hoje — a sequência está protegida.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            restToday -> Text(
                text = "Hoje é dia de descanso da meta — não conta " +
                    "nem quebra a sequência.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            met -> Text(
                text = "Meta do dia batida: " + doneToday.toString() +
                    " de " + goal.toString() + " tarefas. Mandou bem!",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer,
            )
            else -> {
                Text(
                    text = doneToday.toString() + " de " + goal.toString() +
                        " tarefas concluídas hoje",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(scheme.outline),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                (doneToday.toFloat() / goal)
                                    .coerceIn(0f, 1f),
                            )
                            .height(7.dp)
                            .background(scheme.primary, RoundedCornerShape(99.dp)),
                    )
                }
            }
        }
    }
}
