package app.constancia.ui.diary

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.domain.DIARY_PROMPTS
import app.constancia.domain.DIARY_TAGS
import app.constancia.domain.ENERGY_LABELS
import app.constancia.domain.MOOD_LABELS
import app.constancia.domain.computeDiaryPattern
import app.constancia.domain.diaryDayLabel
import app.constancia.domain.dateLinePt
import app.constancia.domain.diaryMatchesQuery
import app.constancia.domain.diaryStreak
import app.constancia.domain.promptIndexFor
import app.constancia.model.DiaryEntry
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.CtaButton
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

    val history = entries
        .filter { diaryMatchesQuery(it, query, todayDate) }
        .sortedByDescending { it.date }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Diário", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Humor, energia, sono e padrões — o dia registrado em um minuto",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        // Editor do dia.
        item {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(scheme.surface, RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(24.dp))
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            diaryDayLabel(formDate.toLocalDate(), todayDate),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            dateLinePt(formDate.toLocalDate())
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (streak > 0 && formDate == today) {
                        Box(
                            modifier = Modifier
                                .background(extra.amberSoft, RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = streak.toString() + "d seguidos",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = scheme.secondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                SectionT("Como está o seu humor?")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    (1..5).forEach { m ->
                        val selected = mood == m
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) {
                                        moodColor(m).copy(alpha = 0.16f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .border(
                                    BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) moodColor(m) else scheme.outline,
                                    ),
                                    RoundedCornerShape(16.dp),
                                )
                                .testTag("mood_" + m)
                                .clickable { mood = m }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            MoodFace(mood = m, size = 30.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = MOOD_LABELS[m - 1],
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                ),
                                color = if (selected) scheme.onSurface
                                    else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                SectionT("Energia hoje")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..5).forEach { e ->
                        val selected = energy == e
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) scheme.primaryContainer
                                    else Color.Transparent,
                                )
                                .border(
                                    BorderStroke(
                                        1.5.dp,
                                        if (selected) scheme.primary else scheme.outline,
                                    ),
                                    RoundedCornerShape(12.dp),
                                )
                                .testTag("energy_" + e)
                                .clickable { energy = e }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = e.toString(),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                    ),
                                    color = if (selected) scheme.primary
                                        else scheme.onSurface,
                                )
                                Text(
                                    text = ENERGY_LABELS[e - 1],
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                    ),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                SectionT("Sono da noite passada")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatSleep(sleep),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    StepBtn("−", "sleep_minus") {
                        sleep = (sleep - 0.5).coerceAtLeast(0.0)
                    }
                    Spacer(Modifier.width(8.dp))
                    StepBtn("+", "sleep_plus") {
                        sleep = (sleep + 0.5).coerceAtMost(14.0)
                    }
                }
                Spacer(Modifier.height(14.dp))

                SectionT("Etiquetas do dia")
                Spacer(Modifier.height(8.dp))
                TagChips(
                    selected = tags,
                    onToggle = { tag ->
                        tags = if (tag in tags) tags - tag else tags + tag
                    },
                )
                Spacer(Modifier.height(14.dp))

                // Pergunta do dia.
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(scheme.primaryContainer, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Pergunta do dia",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = scheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    promptIdx = (promptIdx + 1) % DIARY_PROMPTS.size
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Outra",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = scheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Escreva livremente — só você lê…") },
                    minLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("diary_note_field"),
                )
                Spacer(Modifier.height(12.dp))

                DiaryPhotoSection(
                    photoUri = photoUri,
                    onPicked = { photoUri = it },
                )
                Spacer(Modifier.height(14.dp))

                CtaButton(
                    text = if (editing != null) "Salvar alterações" else "Salvar o dia",
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
                                tint = Color(0xFFC2402A),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Excluir registro",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFC2402A),
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
                            BorderStroke(1.dp, scheme.outline),
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

        // Calendário do mês pintado pelo humor.
        item {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(scheme.surface, RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(24.dp))
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = monthTitle(viewYear, viewMonth),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    StepBtn("‹", "cal_prev") { monthOffset -= 1 }
                    Spacer(Modifier.width(8.dp))
                    StepBtn("›", "cal_next") {
                        if (monthOffset < 0) monthOffset += 1
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { h ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = h,
                                style = MaterialTheme.typography.labelSmall,
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
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { dayNum ->
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (dayNum > 0) {
                                    val entry = monthEntries[dayNum]
                                    val dayDate = LocalDate(viewYear, viewMonth, dayNum)
                                    val isToday = dayDate == todayDate
                                    val isFuture = dayDate > todayDate
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .then(
                                                if (isToday) {
                                                    Modifier.border(
                                                        BorderStroke(2.dp, scheme.primary),
                                                        CircleShape,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable(enabled = !isFuture) {
                                                formDate = dayDate.toString()
                                            }
                                            .padding(4.dp),
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isToday) {
                                                    FontWeight.ExtraBold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                            ),
                                            color = if (isFuture) {
                                                scheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            } else {
                                                scheme.onSurface
                                            },
                                        )
                                        Box(
                                            modifier = Modifier.size(6.dp)
                                                .background(
                                                    if (entry != null) moodColor(entry.mood ?: 3)
                                                    else Color.Transparent,
                                                    CircleShape,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "O ponto é a cor do humor. Toque num dia para reler o registro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
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
                "Histórico",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
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
            items(history, key = { it.id }) { entry ->
                HistoryRow(
                    entry = entry,
                    todayDate = todayDate,
                    onClick = { formDate = entry.date },
                )
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

/** Botão redondo de passo (sono e navegação do calendário). */
@Composable
private fun StepBtn(label: String, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(38.dp)
            .background(scheme.surfaceVariant, CircleShape)
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

@Composable
private fun TagChips(selected: List<String>, onToggle: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DIARY_TAGS.chunked(4).forEach { rowTags ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTags.forEach { tag ->
                    val isSel = tag in selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSel) scheme.primaryContainer
                                else Color.Transparent,
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
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSel) scheme.primary
                                else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
            .background(scheme.surface, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(44.dp)
                .background(moodColor(entry.mood ?: 3).copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MoodFace(mood = entry.mood ?: 3, size = 26.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = diaryDayLabel(date, todayDate),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Energia " + entry.energy + "/5 · Sono " +
                        formatSleep(entry.sleepHours ?: 0.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (entry.note.length > 130) {
                        entry.note.take(130) + "…"
                    } else {
                        entry.note
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (entry.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tags.take(4).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(
                                    scheme.primaryContainer,
                                    RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.primary,
                            )
                        }
                    }
                }
            }
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
        SectionT("Foto do dia")
        Spacer(Modifier.height(8.dp))
        if (photoUri == null) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .border(
                        BorderStroke(1.5.dp, scheme.outline),
                        RoundedCornerShape(16.dp),
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

/** Formata as horas de sono como no protótipo: 7h, 7,5h. */
fun formatSleep(hours: Double): String {
    val whole = hours.toInt()
    val half = (hours - whole) >= 0.5
    return whole.toString() + (if (half) ",5" else "") + "h"
}

private fun monthTitle(year: Int, month: Int): String {
    val names = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )
    return names[(month - 1).coerceIn(0, 11)] + " de " + year
}
