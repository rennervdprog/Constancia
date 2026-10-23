package app.constancia.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import app.constancia.model.Session
import app.constancia.model.newId
import app.constancia.theme.HabitPalette
import app.constancia.ui.common.HABIT_ICON_KEYS
import app.constancia.ui.common.SegmentedRow
import app.constancia.ui.common.SheetSectionLabel
import app.constancia.ui.common.TimeTextField
import app.constancia.ui.common.argbLongOf
import app.constancia.ui.common.habitIcon
import kotlinx.coroutines.launch

// Nomes dos dias na ordem do app: 1=domingo .. 7=sábado.
private val DAY_LETTERS = listOf("D", "S", "T", "Q", "Q", "S", "S")

// Letras na ordem ISO (1=segunda .. 7=domingo), para os dias de descanso.
private val ISO_DAY_LETTERS = listOf("S", "T", "Q", "Q", "S", "S", "D")

/** Quantidade como texto de formulário: inteiro sem o ".0" ("2", "0,5"→"0.5"). */
private fun fmtQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHabitSheet(
    deps: AppDeps,
    onDismiss: () -> Unit,
    // Reteste 2026-10-21 (bug nº 4): editar hábito não existia. Com
    // [editing] preenchido, a folha abre com os valores do hábito e
    // salvar faz UPDATE no mesmo id (check-ins, sequência, ciclos e
    // histórico ficam intactos — o update do repositório nem toca
    // paused/archived/created_at).
    editing: Habit? = null,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var goalType by remember { mutableStateOf(editing?.goalType ?: GoalType.BINARY) }
    var targetText by remember {
        mutableStateOf(editing?.let { fmtQty(it.targetQuantity) } ?: "8")
    }
    var stepText by remember {
        mutableStateOf(editing?.let { fmtQty(it.quantityStep) } ?: "1")
    }
    var unit by remember { mutableStateOf(editing?.quantityUnit ?: "copos") }
    var showInToday by remember { mutableStateOf(editing?.showInToday ?: false) }
    var showInCalendar by remember { mutableStateOf(editing?.showInCalendar ?: false) }
    // Fase C: VÁRIOS horários de lembrete por hábito ("HH:mm").
    var reminderTimes by remember { mutableStateOf<List<String>>(emptyList()) }
    var reminderTimeText by remember { mutableStateOf("") }
    val habitId = remember { editing?.id ?: newId() }
    var iconKey by remember {
        mutableStateOf(
            editing?.icon?.takeIf { it in HABIT_ICON_KEYS } ?: HABIT_ICON_KEYS[0],
        )
    }
    var colorValue by remember {
        mutableStateOf(editing?.color ?: argbLongOf(HabitPalette[0]))
    }
    var freqType by remember {
        mutableStateOf(editing?.frequencyType ?: FrequencyType.DAILY)
    }
    var weeklyTarget by remember { mutableStateOf(editing?.weeklyTarget ?: 3) }
    var days by remember {
        mutableStateOf(
            editing?.daysOfWeek?.ifEmpty { (1..7).toList() }?.toSet() ?: (1..7).toSet(),
        )
    }
    // Fase D (R13.1): dias de descanso planejados, ISO (1=seg..7=dom).
    var ignoreDays by remember {
        mutableStateOf(editing?.ignoreWeekdays ?: emptySet())
    }
    var session by remember { mutableStateOf(editing?.session ?: Session.MANHA) }
    var saving by remember { mutableStateOf(false) }

    // Edição: semeia a lista de lembretes com os horários gravados.
    LaunchedEffect(habitId) {
        if (editing != null) {
            reminderTimes = deps.habits.habitRemindersFlow(habitId)
                .first().map { it.time }
        }
    }

    val scheme = MaterialTheme.colorScheme

    // Sheet: surface, raio 28dp só no topo, padding 12/22/30dp (DESIGN_TOKENS.md).
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
                    text = if (editing != null) "Editar hábito" else "Novo hábito",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            Text(
                text = "Comece pequeno: um hábito que caiba no seu pior dia.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                label = { Text("Nome do hábito") },
                placeholder = { Text("Ex.: Beber água") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("habit_name_field"),
                shape = RoundedCornerShape(16.dp),
            )

            SheetSectionLabel("Tipo de meta")
            SegmentedRow(
                options = listOf("Marcar feito", "Quantidade"),
                selectedIndex = if (goalType == GoalType.BINARY) 0 else 1,
                onSelect = { goalType = if (it == 0) GoalType.BINARY else GoalType.QUANTITY },
            )
            if (goalType == GoalType.QUANTITY) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Meta") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { if (it.length <= 16) unit = it },
                        label = { Text("Unidade") },
                        placeholder = { Text("copos") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = stepText,
                        onValueChange = { raw ->
                            stepText = raw.filter { c -> c.isDigit() || c == '.' || c == ',' }
                                .take(5)
                        },
                        label = { Text("Passo") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    Text(
                        text = "Cada toque no + soma um passo (ex.: 1 copo, 250 ml, 0,5 km).",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SheetSectionLabel("Ícone")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HABIT_ICON_KEYS.chunked(6).forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        rowKeys.forEach { key ->
                            val selected = key == iconKey
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (selected) scheme.primaryContainer
                                        else scheme.surface,
                                        RoundedCornerShape(14.dp),
                                    )
                                    .border(
                                        BorderStroke(
                                            1.5.dp,
                                            if (selected) scheme.primary else scheme.outline,
                                        ),
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable { iconKey = key }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    habitIcon(key),
                                    contentDescription = key,
                                    tint = if (selected) scheme.primary
                                    else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            SheetSectionLabel("Cor")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HabitPalette.forEach { color ->
                    val selected = argbLongOf(color) == colorValue
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selected) Modifier.border(3.dp, scheme.primary, CircleShape)
                                else Modifier,
                            )
                            .clickable { colorValue = argbLongOf(color) },
                    )
                }
            }

            SheetSectionLabel("Frequência")
            SegmentedRow(
                options = listOf("Todo dia", "X por semana", "Dias da semana"),
                selectedIndex = when (freqType) {
                    FrequencyType.DAILY -> 0
                    FrequencyType.WEEKLY_X -> 1
                    FrequencyType.DAYS_OF_WEEK -> 2
                },
                onSelect = {
                    freqType = when (it) {
                        1 -> FrequencyType.WEEKLY_X
                        2 -> FrequencyType.DAYS_OF_WEEK
                        else -> FrequencyType.DAILY
                    }
                },
            )
            when (freqType) {
                FrequencyType.WEEKLY_X -> {
                    Text(
                        text = "Vale em qualquer dia da semana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    // Stepper: fundo surface, borda 1.5dp line, raio pill, padding 3dp.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .background(scheme.surface, RoundedCornerShape(999.dp))
                                .border(
                                    BorderStroke(1.5.dp, scheme.outline),
                                    RoundedCornerShape(999.dp),
                                )
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        enabled = weeklyTarget > 1,
                                        onClick = { if (weeklyTarget > 1) weeklyTarget-- },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "−",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (weeklyTarget > 1) scheme.onSurface
                                    else scheme.outline,
                                )
                            }
                            Text(
                                text = weeklyTarget.toString() + "x",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(scheme.primary)
                                    .clickable(
                                        enabled = weeklyTarget < 7,
                                        onClick = { if (weeklyTarget < 7) weeklyTarget++ },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = scheme.onPrimary,
                                )
                            }
                        }
                    }
                }
                FrequencyType.DAYS_OF_WEEK -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        (1..7).forEach { day ->
                            val selected = day in days
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (selected) scheme.primary else scheme.surface,
                                        CircleShape,
                                    )
                                    .then(
                                        if (selected) Modifier
                                        else Modifier.border(
                                            BorderStroke(1.5.dp, scheme.outline),
                                            CircleShape,
                                        ),
                                    )
                                    .clickable {
                                        days = if (selected) days - day else days + day
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = DAY_LETTERS[day - 1],
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) scheme.onPrimary
                                    else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                FrequencyType.DAILY -> Unit
            }

            // R13.1 (Fase D): descanso planejado — o hábito não é
            // devido nesses dias e a sequência fica protegida (o fim
            // de semana fora do hábito, sem pausa manual). A meta
            // semanal (WEEKLY_X) não usa: ela já é flexível por dia.
            if (freqType != FrequencyType.WEEKLY_X) {
                SheetSectionLabel("Dias de descanso")
                Text(
                    text = "Nesses dias o hábito não é devido — " +
                        "e a sequência fica protegida.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    (1..7).forEach { isoDay ->
                        val selected = isoDay in ignoreDays
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (selected) scheme.primary
                                    else scheme.surface,
                                    CircleShape,
                                )
                                .then(
                                    if (selected) Modifier
                                    else Modifier.border(
                                        BorderStroke(1.5.dp, scheme.outline),
                                        CircleShape,
                                    ),
                                )
                                .clickable {
                                    ignoreDays = if (selected) {
                                        ignoreDays - isoDay
                                    } else {
                                        ignoreDays + isoDay
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = ISO_DAY_LETTERS[isoDay - 1],
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) scheme.onPrimary
                                else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SheetSectionLabel("Sessão do dia")
            SegmentedRow(
                options = listOf("Manhã", "Tarde", "Noite"),
                selectedIndex = session.ordinal,
                onSelect = { session = Session.entries[it] },
                tagPrefix = "session_opt",
            )

            // Integração opt-in (item 4): desligado por padrão.
            SheetSectionLabel("Integração")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sempre mostrar no Hoje",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Aparece no seu dia mesmo fora dos dias programados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = showInToday, onCheckedChange = { showInToday = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Marcar no calendário do diário",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Os dias feitos viram marquinhas coloridas no calendário.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = showInCalendar, onCheckedChange = { showInCalendar = it })
            }

            SheetSectionLabel("Lembretes")
            Text(
                text = "Um toque em cada horário, todo dia. " +
                    "Pode adicionar quantos quiser.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (reminderTimes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    reminderTimes.sorted().forEach { time ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(scheme.primaryContainer)
                                .clickable {
                                    reminderTimes = reminderTimes - time
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = "$time ×",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeTextField(
                    value = reminderTimeText,
                    onValueChange = { value -> reminderTimeText = value },
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(scheme.surfaceVariant)
                        .border(
                            1.dp, scheme.outline, RoundedCornerShape(99.dp),
                        )
                        .clickable {
                            val parsed = app.constancia.domain
                                .parseTimeOrNull(reminderTimeText)
                            if (parsed != null) {
                                val hhmm = parsed.hour.toString()
                                    .padStart(2, '0') + ":" +
                                    parsed.minute.toString().padStart(2, '0')
                                if (hhmm !in reminderTimes) {
                                    reminderTimes = reminderTimes + hhmm
                                }
                                reminderTimeText = ""
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Adicionar horário",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            // CTA: largura total, raio 18dp, padding vertical 16dp, fundo primary,
            // texto onPrimary Sora 16/700 (titleLarge).
            val ctaEnabled = name.isNotBlank() && !saving
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.primary, RoundedCornerShape(18.dp))
                    .testTag("habit_save")
                    .clickable(enabled = ctaEnabled) {
                        if (saving || name.isBlank()) return@clickable
                        saving = true
                        scope.launch {
                            deps.habits.save(
                                Habit(
                                    id = habitId,
                                    name = name.trim(),
                                    icon = iconKey,
                                    color = colorValue,
                                    goalType = goalType,
                                    targetQuantity = targetText.toDoubleOrNull() ?: 1.0,
                                    quantityUnit = unit.trim().ifEmpty { "vezes" },
                                    quantityStep = stepText.replace(',', '.')
                                        .toDoubleOrNull()
                                        ?.takeIf { it > 0.0 } ?: 1.0,
                                    showInToday = showInToday,
                                    showInCalendar = showInCalendar,
                                    frequencyType = freqType,
                                    daysOfWeek = if (freqType == FrequencyType.DAYS_OF_WEEK) {
                                        days.sorted()
                                    } else {
                                        emptyList()
                                    },
                                    ignoreWeekdays = if (
                                        freqType == FrequencyType.WEEKLY_X
                                    ) {
                                        emptySet()
                                    } else {
                                        ignoreDays
                                    },
                                    weeklyTarget = weeklyTarget,
                                    session = session,
                                    reminderEnabled = reminderTimes.isNotEmpty(),
                                    reminderTime = reminderTimes.minOrNull() ?: "",
                                    paused = editing?.paused ?: false,
                                    createdAt = editing?.createdAt ?: "",
                                    sortOrder = editing?.sortOrder ?: 0,
                                ),
                            )
                            // Fase C: grava a LISTA de horários e agenda
                            // os alarmes diários (um por horário). Na
                            // edição sincroniza SEMPRE (lista vazia limpa
                            // os lembretes); na criação, só quando há
                            // horários.
                            if (editing != null || reminderTimes.isNotEmpty()) {
                                deps.habits.setHabitReminders(
                                    habitId, reminderTimes,
                                )
                            }
                            onDismiss()
                        }
                    }
                    .padding(vertical = 16.dp)
                    .then(if (ctaEnabled) Modifier else Modifier.alpha(0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (editing != null) "Salvar alterações" else "Criar hábito",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onPrimary,
                )
            }
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

