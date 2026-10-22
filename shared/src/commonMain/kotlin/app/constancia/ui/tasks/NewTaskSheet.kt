package app.constancia.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.domain.RepeatBase
import app.constancia.domain.RepeatFreq
import app.constancia.domain.RepeatRule
import app.constancia.domain.WEEKDAYS_ISO_PT
import app.constancia.domain.encodeRepeatSpec
import app.constancia.domain.repeatLabel
import app.constancia.model.NewTask
import app.constancia.model.Session
import app.constancia.model.TaskProject
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.ui.common.CtaButton
import app.constancia.ui.common.SegmentedRow
import app.constancia.ui.common.SheetSectionLabel
import app.constancia.ui.common.TimeTextField
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/**
 * Formulário completo de nova tarefa (protótipo aprovado): nome, lista com
 * cor, prioridade, quando, sessão, horário, repetir, subtarefas e anotação.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTaskSheet(deps: AppDeps, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val today = remember { todayYmd() }

    var name by remember { mutableStateOf("") }
    var project by remember { mutableStateOf(TaskProject.ENTRADA) }
    var priority by remember { mutableStateOf(2) } // 1 alta, 2 média, 3 baixa
    var dueChoice by remember { mutableStateOf(1) } // 0 sem data, 1 hoje, 2 amanhã
    var session by remember { mutableStateOf(Session.MANHA) }
    var timeText by remember { mutableStateOf("") }
    // Item 7: construtor de recorrência estruturada.
    var repeatOn by remember { mutableStateOf(false) }
    var freq by remember { mutableStateOf(RepeatFreq.DAILY) }
    var interval by remember { mutableStateOf(1) }
    var weekdays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var monthlyMode by remember { mutableStateOf(0) } // 0 = no dia, 1 = na Nª semana
    var monthWeek by remember { mutableStateOf(1) }
    var repeatBase by remember { mutableStateOf(RepeatBase.DUE_DATE) }
    var endsMonths by remember { mutableStateOf<Int?>(null) } // null = nunca
    // Itens 8 e 9: prazo final, duração estimada e lembrete.
    var deadlineChoice by remember { mutableStateOf(0) } // 0 sem, 1 hoje, 2 amanhã, 3 +7d, 4 +30d
    var duration by remember { mutableStateOf<Int?>(null) }
    // Fase C (R6.1): o lembrete virou LISTA (relativos + absolutos).
    var reminderSpecs by remember {
        mutableStateOf<List<app.constancia.model.ReminderSpec>>(emptyList())
    }
    // Fase A (desvio vínculo): hábito vinculado — concluir a tarefa
    // marca o hábito do dia. Até aqui habitId só existia no modelo.
    val habits by deps.habits.habitsFlow()
        .collectAsState(initial = emptyList())
    var habitId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var subs by remember { mutableStateOf<List<String>>(emptyList()) }
    var subText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val todayDate = remember { today.toLocalDate() }
    val anchorDate = when (dueChoice) {
        2 -> todayDate.plus(DatePeriod(days = 1))
        else -> todayDate
    }

    fun currentRule(): RepeatRule? {
        if (!repeatOn) return null
        return RepeatRule(
            freq = freq,
            interval = interval.coerceIn(1, 12),
            weekdays = if (freq == RepeatFreq.WEEKLY) {
                weekdays.ifEmpty { setOf(anchorDate.isoDayNumberCompat()) }
            } else {
                emptySet()
            },
            monthDay = if (freq == RepeatFreq.MONTHLY && monthlyMode == 0) {
                anchorDate.dayOfMonth
            } else {
                null
            },
            monthWeek = if (freq == RepeatFreq.MONTHLY && monthlyMode == 1) {
                monthWeek
            } else {
                null
            },
            monthWeekday = if (freq == RepeatFreq.MONTHLY && monthlyMode == 1) {
                anchorDate.isoDayNumberCompat()
            } else {
                null
            },
            month = if (freq == RepeatFreq.YEARLY) {
                anchorDate.monthNumber
            } else {
                null
            },
            base = repeatBase,
            endsOn = endsMonths?.let {
                anchorDate.plus(DatePeriod(months = it))
            },
        )
    }

    fun addSub() {
        val v = subText.trim()
        if (v.isEmpty()) return
        subs = subs + v
        subText = ""
    }

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
                .padding(start = 22.dp, top = 12.dp, end = 22.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nova tarefa",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            Text(
                text = "Tudo o que a tarefa precisa — o resto é opcional.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )

            SheetSectionLabel("O que precisa ser feito?")
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                placeholder = { Text("Ex.: Ligar para o cliente") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("task_name_field"),
                shape = RoundedCornerShape(16.dp),
            )

            SheetSectionLabel("Lista")
            // Chips das listas com a cor de cada projeto.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskProject.entries.chunked(3).forEach { rowProjects ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowProjects.forEach { p ->
                            ProjectChip(
                                project = p,
                                selected = project == p,
                                onClick = { project = p },
                            )
                        }
                    }
                }
            }

            SheetSectionLabel("Prioridade")
            SegmentedRow(
                options = listOf("Baixa", "Média", "Alta"),
                selectedIndex = when (priority) {
                    3 -> 0
                    2 -> 1
                    else -> 2
                },
                onSelect = { priority = when (it) { 0 -> 3; 1 -> 2; else -> 1 } },
                tagPrefix = "task_prio",
            )

            SheetSectionLabel("Quando")
            SegmentedRow(
                options = listOf("Sem data", "Hoje", "Amanhã"),
                selectedIndex = dueChoice,
                onSelect = { dueChoice = it },
                tagPrefix = "task_due",
            )

            SheetSectionLabel("Sessão e horário")
            SegmentedRow(
                options = listOf("Manhã", "Tarde", "Noite"),
                selectedIndex = session.ordinal,
                onSelect = { session = Session.entries[it] },
                tagPrefix = "task_session",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Horário", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Opcional — vazio é “qualquer hora”",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                TimeTextField(
                    value = timeText,
                    onValueChange = { v -> timeText = v },
                    modifier = Modifier.width(110.dp).testTag("task_time_field"),
                )
            }

            SheetSectionLabel("Repetir")
            SegmentedRow(
                options = listOf("Não repete", "Repete"),
                selectedIndex = if (repeatOn) 1 else 0,
                onSelect = { repeatOn = it == 1 },
                tagPrefix = "task_repeat",
            )
            if (repeatOn) {
                // Atalhos dos padrões mais usados (itens 5 e 7).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PickChip("Todo dia", freq == RepeatFreq.DAILY && interval == 1) {
                        freq = RepeatFreq.DAILY; interval = 1
                    }
                    PickChip(
                        "Seg a Sex",
                        freq == RepeatFreq.WEEKLY && weekdays == setOf(1, 2, 3, 4, 5),
                    ) {
                        freq = RepeatFreq.WEEKLY; interval = 1
                        weekdays = setOf(1, 2, 3, 4, 5)
                    }
                    PickChip("Semanal", freq == RepeatFreq.WEEKLY && interval == 1 && weekdays.size <= 1) {
                        freq = RepeatFreq.WEEKLY; interval = 1; weekdays = emptySet()
                    }
                    PickChip("A cada 2 semanas", freq == RepeatFreq.WEEKLY && interval == 2) {
                        freq = RepeatFreq.WEEKLY; interval = 2; weekdays = emptySet()
                    }
                    PickChip("Mensal", freq == RepeatFreq.MONTHLY) {
                        freq = RepeatFreq.MONTHLY; interval = 1
                    }
                    PickChip("Anual", freq == RepeatFreq.YEARLY) {
                        freq = RepeatFreq.YEARLY; interval = 1
                    }
                }
                SheetSectionLabel("Frequência")
                SegmentedRow(
                    options = listOf("Diária", "Semanal", "Mensal", "Anual"),
                    selectedIndex = freq.ordinal,
                    onSelect = { freq = RepeatFreq.entries[it] },
                    tagPrefix = "task_freq",
                )
                // Intervalo: "a cada N".
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "A cada",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    StepperButton("−", enabled = interval > 1) {
                        interval = (interval - 1).coerceAtLeast(1)
                    }
                    Text(
                        text = interval.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    StepperButton("+", enabled = interval < 12) {
                        interval = (interval + 1).coerceAtMost(12)
                    }
                    Text(
                        text = when (freq) {
                            RepeatFreq.DAILY -> if (interval == 1) "dia" else "dias"
                            RepeatFreq.WEEKLY -> if (interval == 1) "semana" else "semanas"
                            RepeatFreq.MONTHLY -> if (interval == 1) "mês" else "meses"
                            RepeatFreq.YEARLY -> if (interval == 1) "ano" else "anos"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                if (freq == RepeatFreq.WEEKLY) {
                    Text(
                        text = "Nos dias",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (1..7).forEach { day ->
                            val short = WEEKDAYS_ISO_PT[day - 1].take(3)
                            PickChip(
                                short,
                                day in weekdays ||
                                    (weekdays.isEmpty() &&
                                        day == anchorDate.isoDayNumberCompat()),
                            ) {
                                weekdays = if (day in weekdays) {
                                    weekdays - day
                                } else {
                                    weekdays + day
                                }
                            }
                        }
                    }
                }
                if (freq == RepeatFreq.MONTHLY) {
                    SegmentedRow(
                        options = listOf(
                            "No dia " + anchorDate.dayOfMonth.toString(),
                            "Na semana do mês",
                        ),
                        selectedIndex = monthlyMode,
                        onSelect = { monthlyMode = it },
                        tagPrefix = "task_monthly_mode",
                    )
                    if (monthlyMode == 1) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                1 to "1ª", 2 to "2ª", 3 to "3ª",
                                4 to "4ª", -1 to "Última",
                            ).forEach { (value, label) ->
                                PickChip(label, monthWeek == value) {
                                    monthWeek = value
                                }
                            }
                        }
                        Text(
                            text = "Na " + when (monthWeek) {
                                -1 -> "última"
                                1 -> "1ª"
                                2 -> "2ª"
                                3 -> "3ª"
                                else -> "4ª"
                            } + " " + WEEKDAYS_ISO_PT[
                                anchorDate.isoDayNumberCompat() - 1
                            ].lowercase() + " do mês (o dia da semana " +
                                "vem da data da tarefa).",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                SheetSectionLabel("Contar a próxima a partir de")
                SegmentedRow(
                    options = listOf("Data marcada", "Ao concluir"),
                    selectedIndex = if (repeatBase == RepeatBase.DUE_DATE) 0 else 1,
                    onSelect = {
                        repeatBase = if (it == 0) {
                            RepeatBase.DUE_DATE
                        } else {
                            RepeatBase.COMPLETION
                        }
                    },
                    tagPrefix = "task_repeat_base",
                )
                SheetSectionLabel("Termina")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PickChip("Nunca", endsMonths == null) { endsMonths = null }
                    listOf(1 to "+1 mês", 3 to "+3 meses", 6 to "+6 meses", 12 to "+1 ano")
                        .forEach { (value, label) ->
                            PickChip(label, endsMonths == value) {
                                endsMonths = value
                            }
                        }
                }
                currentRule()?.let { rule ->
                    Text(
                        text = repeatLabel(rule),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (dueChoice == 0) {
                    Text(
                        text = "Sem data escolhida: a série começa hoje.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            SheetSectionLabel("Prazo final")
            SegmentedRow(
                options = listOf("Sem prazo", "Hoje", "Amanhã", "+7 dias", "+30 dias"),
                selectedIndex = deadlineChoice,
                onSelect = { deadlineChoice = it },
                tagPrefix = "task_deadline",
                allowWrap = true,
            )

            SheetSectionLabel("Duração estimada")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickChip("Nenhuma", duration == null) { duration = null }
                listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                    PickChip(
                        app.constancia.domain.durationLabel(minutes),
                        duration == minutes,
                    ) { duration = minutes }
                }
            }

            SheetSectionLabel("Lembretes")
            ReminderListEditor(
                specs = reminderSpecs,
                onChange = { reminderSpecs = it },
                // Sem data só há âncora absoluta se a série repetir
                // (a primeira ocorrência ancora em hoje, como no salvar).
                absoluteDate = when (dueChoice) {
                    0 -> if (repeatOn) todayDate else null
                    else -> anchorDate
                },
            )

            SheetSectionLabel("Hábito vinculado")
            if (habits.isEmpty()) {
                Text(
                    text = "Você ainda não tem hábitos — crie um na aba " +
                        "Hábitos para poder vincular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PickChip("Nenhum", habitId == null) { habitId = null }
                    habits.forEach { habit ->
                        PickChip(habit.name, habitId == habit.id) {
                            habitId = habit.id
                        }
                    }
                }
                if (habitId != null) {
                    Text(
                        text = "Concluir a tarefa marca esse hábito no " +
                            "dia do prazo (hoje ou ontem).",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            SheetSectionLabel("Subtarefas")
            subs.forEachIndexed { index, sub ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { subs = subs.filterIndexed { i, _ -> i != index } }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remover subtarefa",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = subText,
                    onValueChange = { if (it.length <= 50) subText = it },
                    placeholder = { Text("Adicionar subtarefa…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addSub() }),
                    modifier = Modifier.weight(1f).testTag("task_sub_input"),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(48.dp)
                        .background(scheme.primary, CircleShape)
                        .testTag("task_sub_add")
                        .clickable { addSub() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Adicionar subtarefa",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            SheetSectionLabel("Anotação")
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 90) note = it },
                placeholder = { Text("Detalhe rápido (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            if (errorMsg != null) {
                Text(
                    text = errorMsg ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD92D20),
                )
            }

            CtaButton(
                text = "Adicionar tarefa",
                tag = "task_save",
                onClick = {
                    val title = name.trim()
                    if (title.isEmpty()) {
                        errorMsg = "Dê um nome à tarefa"
                        return@CtaButton
                    }
                    val rule = currentRule()
                    val dueDate = when (dueChoice) {
                        // Repetição sem data: a série ancora em hoje.
                        0 -> if (rule != null) today else null
                        1 -> today
                        else -> today.toLocalDate()
                            .plus(DatePeriod(days = 1))
                            .toString()
                    }
                    val deadline = when (deadlineChoice) {
                        1 -> today
                        2 -> todayDate.plus(DatePeriod(days = 1)).toString()
                        3 -> todayDate.plus(DatePeriod(days = 7)).toString()
                        4 -> todayDate.plus(DatePeriod(days = 30)).toString()
                        else -> null
                    }
                    val time = timeText.trim().takeIf {
                        it.matches(Regex("^\\d{1,2}:\\d{2}$"))
                    }
                    scope.launch {
                        deps.tasks.add(
                            NewTask(
                                title = title,
                                note = note.trim(),
                                project = project,
                                priority = priority,
                                dueDate = dueDate,
                                session = session,
                                dueTime = time,
                                repeatRule = rule?.let { repeatLabel(it) } ?: "",
                                repeatSpec = rule?.let {
                                    encodeRepeatSpec(it)
                                } ?: "",
                                deadlineDate = deadline,
                                durationMinutes = duration,
                                reminderMinutesBefore = reminderSpecs
                                    .firstOrNull {
                                        it.type ==
                                            app.constancia.model.ReminderType.RELATIVE
                                    }?.minuteOffset,
                                reminders = reminderSpecs,
                                habitId = habitId,
                                subtaskTitles = subs,
                            ),
                        )
                        onDismiss()
                    }
                },
            )
            Box(
                modifier = Modifier.fillMaxWidth()
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
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ProjectChip(
    project: TaskProject,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(
                androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (selected) scheme.primary else scheme.outline,
                ),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(8.dp)
                .background(Color(project.colorHex), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = project.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

/** Botão − / + do stepper de intervalo da recorrência. */
@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (enabled) scheme.primaryContainer else scheme.surfaceVariant,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                scheme.onPrimaryContainer
            } else {
                scheme.onSurfaceVariant
            },
        )
    }
}
