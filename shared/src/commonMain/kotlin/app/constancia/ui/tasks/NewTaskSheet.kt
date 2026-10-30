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
import app.constancia.domain.dueDateLabel
import app.constancia.domain.effectiveRepeatSpec
import app.constancia.domain.encodeRepeatSpec
import app.constancia.domain.normalizedTimeOrNull
import app.constancia.domain.repeatLabel
import app.constancia.domain.sessionForHour
import app.constancia.model.NewTask
import app.constancia.model.Session
import app.constancia.model.Subtask
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.ui.common.CtaButton
import app.constancia.ui.common.SegmentedRow
import app.constancia.ui.common.SheetSectionLabel
import app.constancia.ui.common.TimeTextField
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Formulário completo de tarefa (protótipo aprovado): nome, lista com
 * cor, prioridade, quando, sessão, horário, repetir, subtarefas e
 * anotação. Sem [existing], cria; com [existing], edita a tarefa
 * (mesma tela, estados preenchidos — auditoria de 2026-10-30: antes
 * não havia edição, só excluir e recriar).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTaskSheet(
    deps: AppDeps,
    onDismiss: () -> Unit,
    existing: TaskItem? = null,
) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val today = remember { todayYmd() }
    val todayDate = remember { today.toLocalDate() }
    val editing = existing != null

    // Datas fora das 3 opções rápidas (edição de tarefa com outra
    // data, ex.: daqui a 5 dias ou atrasada) ficam guardadas à parte
    // e valem enquanto a escolha for a "personalizada" (3 no quando,
    // 5 no prazo).
    val existingDue = remember { existing?.dueDate?.toLocalDate() }
    val existingDeadline = remember { existing?.deadlineDate?.toLocalDate() }
    val existingRule = remember {
        existing?.let {
            effectiveRepeatSpec(
                it.repeatSpec, it.repeatRule, existingDue ?: todayDate,
            )
        }
    }

    var name by remember { mutableStateOf(existing?.title ?: "") }
    var project by remember {
        mutableStateOf(existing?.project ?: TaskProject.ENTRADA)
    }
    var priority by remember { mutableStateOf(existing?.priority ?: 2) } // 1 alta, 2 média, 3 baixa
    var customDue by remember {
        mutableStateOf(
            existingDue?.takeIf {
                it != todayDate && it != todayDate.plus(DatePeriod(days = 1))
            },
        )
    }
    var dueChoice by remember {
        // 0 sem data, 1 hoje, 2 amanhã, 3 = data atual (edição)
        mutableStateOf(
            when {
                existingDue == null -> if (editing) 0 else 1
                existingDue == todayDate -> 1
                existingDue == todayDate.plus(DatePeriod(days = 1)) -> 2
                else -> 3
            },
        )
    }
    var session by remember {
        // Padrão = sessão da hora atual (antes o formulário fixava
        // Manhã e a captura rápida caía sempre na Tarde).
        mutableStateOf(
            existing?.session ?: sessionForHour(
                Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).hour,
            ),
        )
    }
    var timeText by remember { mutableStateOf(existing?.dueTime ?: "") }
    // Item 7: construtor de recorrência estruturada.
    var repeatOn by remember { mutableStateOf(existingRule != null) }
    var freq by remember {
        mutableStateOf(existingRule?.freq ?: RepeatFreq.DAILY)
    }
    var interval by remember { mutableStateOf(existingRule?.interval ?: 1) }
    var weekdays by remember {
        mutableStateOf(existingRule?.weekdays ?: emptySet())
    }
    var monthlyMode by remember {
        // 0 = no dia, 1 = na Nª semana
        mutableStateOf(if (existingRule?.monthWeek != null) 1 else 0)
    }
    var monthWeek by remember { mutableStateOf(existingRule?.monthWeek ?: 1) }
    var repeatBase by remember {
        mutableStateOf(existingRule?.base ?: RepeatBase.DUE_DATE)
    }
    var endsMonths by remember {
        // null = nunca; na edição, término que não é +1/+3/+6/+12
        // meses da âncora fica como data personalizada.
        mutableStateOf(
            existingRule?.endsOn?.let { ends ->
                val anchor = existingDue ?: todayDate
                listOf(1, 3, 6, 12).firstOrNull {
                    anchor.plus(DatePeriod(months = it)) == ends
                }
            },
        )
    }
    var customEndsOn by remember {
        mutableStateOf(
            existingRule?.endsOn?.takeIf { ends ->
                val anchor = existingDue ?: todayDate
                listOf(1, 3, 6, 12).none {
                    anchor.plus(DatePeriod(months = it)) == ends
                }
            },
        )
    }
    // Itens 8 e 9: prazo final, duração estimada e lembrete.
    var customDeadline by remember {
        mutableStateOf(
            existingDeadline?.takeIf { dl ->
                dl != todayDate &&
                    dl != todayDate.plus(DatePeriod(days = 1)) &&
                    dl != todayDate.plus(DatePeriod(days = 7)) &&
                    dl != todayDate.plus(DatePeriod(days = 30))
            },
        )
    }
    var deadlineChoice by remember {
        // 0 sem, 1 hoje, 2 amanhã, 3 +7d, 4 +30d, 5 = data atual (edição)
        mutableStateOf(
            when (existingDeadline) {
                null -> 0
                todayDate -> 1
                todayDate.plus(DatePeriod(days = 1)) -> 2
                todayDate.plus(DatePeriod(days = 7)) -> 3
                todayDate.plus(DatePeriod(days = 30)) -> 4
                else -> 5
            },
        )
    }
    var duration by remember { mutableStateOf(existing?.durationMinutes) }
    // Fase C (R6.1): o lembrete virou LISTA (relativos + absolutos).
    var reminderSpecs by remember {
        mutableStateOf<List<app.constancia.model.ReminderSpec>>(
            existing?.reminders?.map {
                app.constancia.model.ReminderSpec(
                    it.type, it.minuteOffset, it.atMillis,
                )
            } ?: emptyList(),
        )
    }
    // Fase A (desvio vínculo): hábito vinculado — concluir a tarefa
    // marca o hábito do dia. Até aqui habitId só existia no modelo.
    val habits by deps.habits.habitsFlow()
        .collectAsState(initial = emptyList())
    var habitId by remember { mutableStateOf(existing?.habitId) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var subs by remember {
        mutableStateOf<List<Subtask>>(existing?.subtasks ?: emptyList())
    }
    var subText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val anchorDate = when (dueChoice) {
        2 -> todayDate.plus(DatePeriod(days = 1))
        3 -> customDue ?: todayDate
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
            } ?: customEndsOn,
        )
    }

    fun addSub() {
        val v = subText.trim()
        if (v.isEmpty()) return
        subs = subs + Subtask(
            id = "",
            taskId = existing?.id ?: "",
            title = v,
            done = false,
            sortOrder = subs.size,
        )
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
                    text = if (editing) "Editar tarefa" else "Nova tarefa",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            Text(
                text = if (editing) {
                    "Altere o que precisar — o resto fica como está."
                } else {
                    "Tudo o que a tarefa precisa — o resto é opcional."
                },
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
            if (dueChoice == 3 && customDue != null) {
                // Edição de tarefa com data fora das opções rápidas:
                // a data atual fica visível e vale até trocar acima.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PickChip(dueDateLabel(customDue!!, todayDate), true) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "data atual da tarefa",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

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
                    PickChip(
                        "Nunca",
                        endsMonths == null && customEndsOn == null,
                    ) {
                        endsMonths = null
                        customEndsOn = null
                    }
                    listOf(1 to "+1 mês", 3 to "+3 meses", 6 to "+6 meses", 12 to "+1 ano")
                        .forEach { (value, label) ->
                            PickChip(label, endsMonths == value) {
                                endsMonths = value
                                customEndsOn = null
                            }
                        }
                    if (customEndsOn != null && endsMonths == null) {
                        PickChip(
                            "Em " + dueDateLabel(customEndsOn!!, todayDate),
                            true,
                        ) {}
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
            if (deadlineChoice == 5 && customDeadline != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PickChip(dueDateLabel(customDeadline!!, todayDate), true) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "prazo atual da tarefa",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

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
            if (dueChoice == 0 && !repeatOn) {
                // Sem data e sem repetição não há âncora: lembrete
                // nenhum dispararia (auditoria 2026-10-30 — antes os
                // chips aceitavam a configuração em silêncio).
                Text(
                    text = "Escolha uma data acima (ou ative Repetir) " +
                        "para poder adicionar lembretes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                ReminderListEditor(
                    specs = reminderSpecs,
                    onChange = { reminderSpecs = it },
                    // Sem data só há âncora absoluta se a série repetir
                    // (a primeira ocorrência ancora em hoje, como no salvar).
                    absoluteDate = anchorDate,
                )
                if (repeatOn && reminderSpecs.any {
                        it.type == app.constancia.model.ReminderType.ABSOLUTE
                    }
                ) {
                    Text(
                        text = "O horário fixo vale só para a primeira " +
                            "ocorrência; os lembretes relativos " +
                            "acompanham a série.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

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
                        // Texto = comportamento real (auditoria
                        // 2026-10-30): o check-in é sempre no dia da
                        // conclusão, mesmo se a tarefa era de ontem.
                        text = "Concluir a tarefa marca esse hábito no " +
                            "dia da conclusão.",
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
                        text = sub.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sub.done) {
                            scheme.onSurfaceVariant
                        } else {
                            scheme.onSurface
                        },
                        textDecoration = if (sub.done) {
                            androidx.compose.ui.text.style.TextDecoration.LineThrough
                        } else {
                            null
                        },
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
                text = if (editing) "Salvar alterações" else "Adicionar tarefa",
                tag = "task_save",
                onClick = {
                    val title = name.trim()
                    if (title.isEmpty()) {
                        errorMsg = "Dê um nome à tarefa"
                        return@CtaButton
                    }
                    // Horário: valida de verdade (auditoria 2026-10-30).
                    // O campo aceita dígitos fora da faixa de propósito;
                    // aqui, texto que não é um horário válido vira erro
                    // visível — antes "29:61" salvava cru e o lembrete
                    // caía em silêncio para as 09:00.
                    val time: String? = if (timeText.isBlank()) {
                        null
                    } else {
                        val normalized = normalizedTimeOrNull(timeText.trim())
                        if (normalized == null) {
                            errorMsg = "Horário inválido — use HH:mm " +
                                "(00:00 a 23:59) ou apague o campo."
                            return@CtaButton
                        }
                        normalized
                    }
                    val rule = currentRule()
                    val dueDate = when (dueChoice) {
                        // Repetição sem data: a série ancora em hoje.
                        0 -> if (rule != null) today else null
                        1 -> today
                        2 -> todayDate.plus(DatePeriod(days = 1)).toString()
                        else -> customDue?.toString()
                    }
                    val deadline = when (deadlineChoice) {
                        1 -> today
                        2 -> todayDate.plus(DatePeriod(days = 1)).toString()
                        3 -> todayDate.plus(DatePeriod(days = 7)).toString()
                        4 -> todayDate.plus(DatePeriod(days = 30)).toString()
                        5 -> customDeadline?.toString()
                        else -> null
                    }
                    // Prazo final antes da data da tarefa não faz
                    // sentido (a tarefa já nasceria com o prazo
                    // estourado) — auditoria 2026-10-30.
                    if (deadline != null && dueDate != null && deadline < dueDate) {
                        errorMsg = "O prazo final não pode ser antes " +
                            "da data da tarefa."
                        return@CtaButton
                    }
                    errorMsg = null
                    val newTask = NewTask(
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
                        subtaskTitles = if (editing) {
                            emptyList()
                        } else {
                            subs.map { it.title }
                        },
                    )
                    scope.launch {
                        if (editing) {
                            deps.tasks.update(existing!!.id, newTask, subs)
                        } else {
                            deps.tasks.add(newTask)
                        }
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
