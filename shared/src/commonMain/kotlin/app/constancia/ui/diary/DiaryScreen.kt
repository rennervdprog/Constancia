package app.constancia.ui.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.domain.DIARY_PROMPTS
import app.constancia.domain.DIARY_TAGS
import app.constancia.domain.DOW_FULL
import app.constancia.domain.MONTHS_PT
import app.constancia.domain.MOOD_LABELS
import app.constancia.domain.computeDiaryPattern
import app.constancia.domain.dateLinePt
import app.constancia.domain.diaryMatchesQuery
import app.constancia.domain.diaryStreak
import app.constancia.domain.doneDatesFor
import app.constancia.domain.donePairs
import app.constancia.domain.promptIndexFor
import app.constancia.model.DiaryEntry
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.BackRow
import app.constancia.ui.common.CtaButton
import app.constancia.ui.common.habitColorOf
import app.constancia.ui.common.moodColor
import app.constancia.ui.common.MoodFace
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Diário turbinado (protótipo aprovado): humor com rostinhos, energia,
 * sono, etiquetas, pergunta do dia, foto do dia, sequência de escrita,
 * cartão Padrões, calendário do mês pintado pelo humor, busca e histórico.
 */
@Composable
fun DiaryScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    initialDate: String? = null,
    onConsumedInitialDate: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val today = remember { todayYmd() }
    val todayDate = remember { today.toLocalDate() }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra

    val entries by deps.diary.allEntriesFlow().collectAsState(initial = emptyList())
    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val checkinRangeFrom = remember {
        todayDate.minus(DatePeriod(days = 400)).toString()
    }
    val checkins by deps.checkins.checkinsInRangeFlow(checkinRangeFrom, today)
        .collectAsState(initial = emptyList())
    val entryByDate = entries.associateBy { it.date }

    // Data exibida no editor (hoje por padrão; calendário/histórico abrem o dia).
    var formDate by remember { mutableStateOf(today) }
    LaunchedEffect(initialDate) {
        val opened = initialDate
        if (opened != null) {
            formDate = opened
            onConsumedInitialDate()
        }
    }
    val editing = entryByDate[formDate]

    // Estado do formulário (reinicia quando muda o dia ou chega o registro).
    var mood by remember(formDate, editing?.id) {
        mutableStateOf(editing?.mood ?: 0)
    }
    var note by remember(formDate, editing?.id) {
        mutableStateOf(editing?.note ?: "")
    }
    var energy by remember(formDate, editing?.id) {
        mutableStateOf(editing?.energy ?: 3)
    }
    var sleep by remember(formDate, editing?.id) {
        mutableStateOf(editing?.sleepHours ?: 7.0)
    }
    var tags by remember(formDate, editing?.id) {
        mutableStateOf(editing?.tags ?: emptyList())
    }
    var photoUri by remember(formDate, editing?.id) {
        mutableStateOf(editing?.photoUri)
    }
    var promptIdx by remember(formDate) {
        mutableStateOf(promptIndexFor(formDate.toLocalDate()))
    }
    var showOk by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var monthOffset by remember { mutableStateOf(0) }

    val prompt = DIARY_PROMPTS[promptIdx % DIARY_PROMPTS.size]
    val streak = diaryStreak(entries.map { it.date }.toSet(), todayDate)
    val pattern = computeDiaryPattern(entries, habits, checkins)

    val viewMonthDate = LocalDate(todayDate.year, todayDate.monthNumber, 1)
        .plus(DatePeriod(months = monthOffset))
    val viewYear = viewMonthDate.year
    val viewMonth = viewMonthDate.monthNumber
    val monthEntries = entries.filter {
        val d = it.date.toLocalDate()
        d.year == viewYear && d.monthNumber == viewMonth
    }.associateBy { it.date.toLocalDate().dayOfMonth }

    // Item 4 (opt-in por hábito): dias feitos de hábitos com
    // "marcar no calendário" viram pontinhos na cor do hábito, sob o
    // número do dia — desligado por padrão, não polui quem não ligou.
    val calendarDots = remember(habits, checkins) {
        val byHabit = checkins.groupBy { it.habitId }
        val map = mutableMapOf<String, List<Long>>()
        habits.filter { it.showInCalendar }.forEach { h ->
            val done = doneDatesFor(
                h,
                donePairs(byHabit[h.id].orEmpty()),
            )
            done.forEach { ymd ->
                map[ymd] = (map[ymd] ?: emptyList()) + h.color
            }
        }
        map
    }

    val history = entries
        .filter { diaryMatchesQuery(it, query, todayDate) }
        .sortedByDescending { it.date }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackRow(
                title = "Diário",
                subtitle = dateLinePt(formDate.toLocalDate()) +
                    if (streak > 0) {
                        " · " + streak.toString() +
                            if (streak == 1) {
                                " dia seguido escrevendo"
                            } else {
                                " dias seguidos escrevendo"
                            }
                    } else {
                        ""
                    },
                onBack = onBack,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // Editor do dia.
        item {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(scheme.surface, RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, extra.lineSoft), RoundedCornerShape(24.dp))
                    .padding(18.dp),
            ) {
                Text(
                    text = "Como foi o seu dia?",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Toque numa carinha — dá pra ajustar depois.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..5).forEach { m ->
                        val selected = mood == m
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (selected) {
                                        Modifier
                                            .offset(y = (-3).dp)
                                            .shadow(
                                                elevation = 5.dp,
                                                shape = RoundedCornerShape(18.dp),
                                                spotColor = moodColor(m)
                                                    .copy(alpha = 0.35f),
                                                ambientColor = moodColor(m)
                                                    .copy(alpha = 0.35f),
                                            )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) scheme.surface
                                    else scheme.surfaceVariant,
                                )
                                .border(
                                    BorderStroke(
                                        1.5.dp,
                                        if (selected) moodColor(m) else extra.lineSoft,
                                    ),
                                    RoundedCornerShape(18.dp),
                                )
                                .testTag("mood_" + m)
                                .clickable { mood = m }
                                .padding(top = 11.dp, bottom = 9.dp),
                        ) {
                            MoodFace(mood = m, size = 30.dp)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = MOOD_LABELS[m - 1],
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Steppers lado a lado (Energia · Sono), como no protótipo.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        StepperLabel("ENERGIA")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StepBtn("−", "energy_minus") {
                                energy = (energy - 1).coerceAtLeast(1)
                            }
                            Text(
                                text = buildAnnotatedString {
                                    append(energy.toString())
                                    withStyle(
                                        SpanStyle(
                                            color = scheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                        ),
                                    ) {
                                        append("/5")
                                    }
                                },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            StepBtn("+", "energy_plus") {
                                energy = (energy + 1).coerceAtMost(5)
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StepperLabel("SONO")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StepBtn("−", "sleep_minus") {
                                sleep = (sleep - 10.0 / 60.0).coerceAtLeast(4.0)
                            }
                            Text(
                                text = formatSleep(sleep),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            StepBtn("+", "sleep_plus") {
                                sleep = (sleep + 10.0 / 60.0).coerceAtMost(10.0)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                TagChips(
                    selected = tags,
                    onToggle = { tag ->
                        tags = if (tag in tags) tags - tag else tags + tag
                    },
                )
                Spacer(Modifier.height(14.dp))

                // Pergunta do dia (.qcard do protótipo): gradiente suave,
                // selo em roxo-sol, pergunta em Sora e a resposta dentro.
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(extra.qGradStart, extra.qGradEnd),
                            ),
                        )
                        .border(
                            BorderStroke(1.dp, extra.qBorder),
                            RoundedCornerShape(24.dp),
                        )
                        .padding(17.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PERGUNTA DO DIA",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.1.em,
                            ),
                            color = extra.sunColor,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    promptIdx = (promptIdx + 1) % DIARY_PROMPTS.size
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = extra.sunColor,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Trocar",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = extra.sunColor,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = extra.qInk,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Escreva livremente — só você lê…") },
                        minLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = extra.qBorder,
                            unfocusedBorderColor = extra.qBorder,
                            focusedContainerColor = scheme.surface.copy(alpha = 0.75f),
                            unfocusedContainerColor = scheme.surface.copy(alpha = 0.75f),
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("diary_note_field"),
                    )
                }
                Spacer(Modifier.height(14.dp))

                DiaryPhotoSection(
                    photoUri = photoUri,
                    onPicked = { photoUri = it },
                )
                Spacer(Modifier.height(14.dp))

                CtaButton(
                    text = when {
                        editing != null -> "Salvar alterações"
                        formDate == today -> "Salvar registro de hoje"
                        else -> "Salvar registro"
                    },
                    tag = "diary_save",
                    onClick = {
                        val m = mood.takeIf { it in 1..5 } ?: return@CtaButton
                        scope.launch {
                            deps.diary.save(
                                dateYmd = formDate,
                                mood = m,
                                note = note.trim(),
                                energy = energy,
                                sleepHours = sleep,
                                tags = tags,
                                photoUri = photoUri,
                                prompt = prompt,
                            )
                            showOk = true
                        }
                    },
                )
                if (mood !in 1..5) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Escolha um humor para salvar",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (showOk) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Dia registrado — " +
                            diaryStreak(entries.map { it.date }.toSet(), todayDate).toString() +
                            " dias seguidos escrevendo",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = scheme.primary,
                        modifier = Modifier.testTag("diary_saved_ok"),
                    )
                }
                if (editing != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        deps.diary.delete(formDate)
                                        if (formDate != today) formDate = today
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = Color(0xFFD92D20),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Excluir registro",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFD92D20),
                            )
                        }
                    }
                }
            }
        }

        // Cartão Padrões (cruza diário × hábitos com dados reais).
        if (pattern != null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(scheme.surface, RoundedCornerShape(24.dp))
                        .border(
                            BorderStroke(1.dp, extra.lineSoft),
                            RoundedCornerShape(24.dp),
                        )
                        .padding(18.dp),
                ) {
                    Text(
                        pattern.headline,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Humor médio " + fmtAvg(pattern.avgWith) +
                            " nos dias com " + pattern.habit.name.lowercase() +
                            " contra " + fmtAvg(pattern.avgWithout) + " sem — em " +
                            pattern.totalEntries + " registros.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Calendário do mês pintado pelo humor (protótipo): cabeçalho
        // "{Mês} pelo seu humor" + média, grade de dias em quadrados
        // arredondados preenchidos com a cor do humor e hoje contornado.
        item {
            val monthMoods = monthEntries.values.mapNotNull { it.mood }
            val monthAvg = if (monthMoods.isEmpty()) {
                null
            } else {
                monthMoods.average()
            }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 2.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = monthNamePt(viewMonth) + " pelo seu humor",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (monthAvg != null) {
                        Text(
                            text = "média " + fmtAvg(monthAvg),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    CalNavBtn("‹", "cal_prev") { monthOffset -= 1 }
                    Spacer(Modifier.width(6.dp))
                    CalNavBtn("›", "cal_next") {
                        if (monthOffset < 0) monthOffset += 1
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(scheme.surface, RoundedCornerShape(24.dp))
                        .border(
                            BorderStroke(1.dp, extra.lineSoft),
                            RoundedCornerShape(24.dp),
                        )
                        .padding(16.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { h ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = h,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    ),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val firstDay = LocalDate(viewYear, viewMonth, 1)
                    val lead = firstDay.dayOfWeek.ordinal // segunda = 0
                    val daysInMonth = firstDay.plus(DatePeriod(months = 1))
                        .toEpochDays() - firstDay.toEpochDays()
                    val cells = List(lead) { 0 } + (1..daysInMonth).toList()
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        cells.chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                week.forEach { dayNum ->
                                    Box(
                                        modifier = Modifier.weight(1f).aspectRatio(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (dayNum > 0) {
                                            val entry = monthEntries[dayNum]
                                            val dayDate = LocalDate(
                                                viewYear, viewMonth, dayNum,
                                            )
                                            val isToday = dayDate == todayDate
                                            val isFuture = dayDate > todayDate
                                            val moodOfDay = entry?.mood
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(11.dp))
                                                    .background(
                                                        if (moodOfDay != null) {
                                                            moodColor(moodOfDay)
                                                        } else {
                                                            scheme.surfaceVariant
                                                        },
                                                    )
                                                    .then(
                                                        if (isToday) {
                                                            Modifier.border(
                                                                BorderStroke(
                                                                    2.5.dp,
                                                                    scheme.primary,
                                                                ),
                                                                RoundedCornerShape(11.dp),
                                                            )
                                                        } else {
                                                            Modifier
                                                        },
                                                    )
                                                    .clickable(enabled = !isFuture) {
                                                        formDate = dayDate.toString()
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                val dotColors = calendarDots[
                                                    dayDate.toString()
                                                ].orEmpty()
                                                Column(
                                                    horizontalAlignment =
                                                        Alignment.CenterHorizontally,
                                                ) {
                                                    Text(
                                                        text = dayNum.toString(),
                                                        style = MaterialTheme.typography
                                                            .bodySmall.copy(
                                                                fontSize = 12.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                            ),
                                                        color = when {
                                                            isFuture -> scheme
                                                                .onSurfaceVariant
                                                                .copy(alpha = 0.45f)
                                                            moodOfDay == 3 -> scheme.onSurface
                                                            moodOfDay != null -> Color.White
                                                            else -> scheme.onSurface
                                                        },
                                                    )
                                                    if (dotColors.isNotEmpty()) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Row(
                                                            horizontalArrangement =
                                                                Arrangement.spacedBy(3.dp),
                                                        ) {
                                                            dotColors.take(3).forEach { c ->
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(5.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                            habitColorOf(c),
                                                                        ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                repeat(7 - week.size) {
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Legenda das cores do humor.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        (1..5).forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(moodColor(m)),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = MOOD_LABELS[m - 1],
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                    ),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Busca + histórico.
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar nos registros…") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("diary_search"),
            )
        }
        item {
            Text(
                "Registros recentes",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp),
            )
        }
        if (history.isEmpty()) {
            item {
                Text(
                    text = if (entries.isEmpty()) {
                        "Nenhum registro ainda — o de hoje pode ser o primeiro."
                    } else {
                        "Nada encontrado para essa busca."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(scheme.surface, RoundedCornerShape(24.dp))
                        .border(
                            BorderStroke(1.dp, extra.lineSoft),
                            RoundedCornerShape(24.dp),
                        ),
                ) {
                    history.forEachIndexed { index, entry ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(extra.lineSoft),
                            )
                        }
                        HistoryRow(
                            entry = entry,
                            todayDate = todayDate,
                            onClick = { formDate = entry.date },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionT(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
    )
}

/** Rótulo dos steppers do protótipo: 12/800 maiúsculas em muted. */
@Composable
private fun StepperLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.06.em,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Botão de passo do protótipo (.step-btn): 38dp, raio 12, fundo
 *  surface-2 com borda line e o glifo em roxo 19/800. */
@Composable
private fun StepBtn(label: String, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant)
            .border(
                BorderStroke(1.5.dp, scheme.outline),
                RoundedCornerShape(12.dp),
            )
            .testTag(tag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = scheme.primary,
        )
    }
}

/** Botão menor de navegação do calendário (mês anterior/próximo). */
@Composable
private fun CalNavBtn(label: String, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Box(
        modifier = Modifier.size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(scheme.surfaceVariant)
            .border(BorderStroke(1.dp, extra.lineSoft), RoundedCornerShape(9.dp))
            .testTag(tag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = scheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(selected: List<String>, onToggle: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DIARY_TAGS.forEach { tag ->
            val isSel = tag in selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSel) scheme.primaryContainer else Color.Transparent,
                    )
                    .border(
                        BorderStroke(
                            1.5.dp,
                            if (isSel) scheme.primary else scheme.outline,
                        ),
                        RoundedCornerShape(999.dp),
                    )
                    .testTag("diary_tag_" + tag)
                    .clickable { onToggle(tag) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (isSel) scheme.onPrimaryContainer else extra.ink2,
                )
            }
        }
    }
}

/** Título do registro no histórico: "Segunda, 19 de outubro". */
private fun historyTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.minus(DatePeriod(days = 1)) -> "Ontem"
    else -> {
        val wd = DOW_FULL[date.isoDayNumberCompat() % 7]
        wd + ", " + date.dayOfMonth + " de " +
            MONTHS_PT[date.monthNumber - 1]
    }
}

@Composable
private fun HistoryRow(
    entry: DiaryEntry,
    todayDate: LocalDate,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val date = entry.date.toLocalDate()
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.photoUri != null) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                DiaryPhotoImage(
                    photoUri = entry.photoUri,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                modifier = Modifier.size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(moodColor(entry.mood ?: 3).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                MoodFace(mood = entry.mood ?: 3, size = 30.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = historyTitle(date, todayDate),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    if (entry.note.isNotBlank()) {
                        append("“")
                        append(entry.note.take(40))
                        if (entry.note.length > 40) append("…")
                        append("” · ")
                    }
                    append("energia ")
                    append(entry.energy)
                    entry.sleepHours?.let {
                        append(" · sono ")
                        append(formatSleep(it))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** Foto do dia: seletor do sistema (Android) ou caixa vazia com convite. */
@Composable
private fun DiaryPhotoSection(
    photoUri: String?,
    onPicked: (String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val launchPicker = rememberDiaryPhotoPicker { uri ->
        if (uri != null) onPicked(uri)
    }
    Column {
        if (photoUri == null) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .border(
                        BorderStroke(1.5.dp, scheme.outline),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Adicionar foto do dia",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "Um retrato, um lugar, um prato — o que marcou o dia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(scheme.primaryContainer)
                        .testTag("diary_photo_pick")
                        .clickable { launchPicker() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = "Escolher foto",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                }
            }
        } else {
            DiaryPhotoImage(photoUri = photoUri)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(scheme.primaryContainer)
                        .clickable { launchPicker() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Trocar foto",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(scheme.surfaceVariant)
                        .clickable { onPicked(null) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Remover",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Média com 1 casa decimal em PT-BR: 3,8. */
private fun fmtAvg(value: Double): String {
    val scaled = (value * 10).roundToInt()
    return (scaled / 10).toString() + "," + (scaled % 10).toString()
}

/** Formata as horas de sono como no protótipo: 7h, 7h10, 7h45. */
fun formatSleep(hours: Double): String {
    var whole = hours.toInt()
    var minutes = ((hours - whole) * 60).roundToInt()
    if (minutes == 60) {
        whole += 1
        minutes = 0
    }
    return if (minutes == 0) {
        whole.toString() + "h"
    } else {
        whole.toString() + "h" + minutes.toString().padStart(2, '0')
    }
}

private fun monthTitle(year: Int, month: Int): String =
    monthNamePt(month) + " de " + year

private fun monthNamePt(month: Int): String {
    val names = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )
    return names[(month - 1).coerceIn(0, 11)]
}
