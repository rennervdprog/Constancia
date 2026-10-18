package app.constancia.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.newId
import app.constancia.theme.HabitPalette
import app.constancia.ui.common.HABIT_ICON_KEYS
import app.constancia.ui.common.habitIcon
import kotlinx.coroutines.launch

// Nomes dos dias na ordem do app: 1=domingo .. 7=sábado.
private val DAY_LETTERS = listOf("D", "S", "T", "Q", "Q", "S", "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHabitSheet(deps: AppDeps, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var goalType by remember { mutableStateOf(GoalType.BINARY) }
    var targetText by remember { mutableStateOf("8") }
    var unit by remember { mutableStateOf("copos") }
    var iconKey by remember { mutableStateOf(HABIT_ICON_KEYS[0]) }
    var colorValue by remember { mutableStateOf(HabitPalette[0].value.toLong()) }
    var freqType by remember { mutableStateOf(FrequencyType.DAILY) }
    var weeklyTarget by remember { mutableStateOf(3) }
    var days by remember { mutableStateOf((1..7).toSet()) }
    var session by remember { mutableStateOf(Session.MANHA) }
    var saving by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Novo hábito",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
            Text(
                text = "Comece pequeno: um hábito que caiba no seu pior dia.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                label = { Text("Nome do hábito") },
                placeholder = { Text("Ex.: Beber água") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) scheme.primaryContainer
                                        else scheme.surfaceVariant,
                                    )
                                    .clickable { iconKey = key }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    habitIcon(key),
                                    contentDescription = key,
                                    tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
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
                    val selected = color.value.toLong() == colorValue
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selected) Modifier.border(3.dp, scheme.onSurface, CircleShape)
                                else Modifier,
                            )
                            .clickable { colorValue = color.value.toLong() },
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(
                            onClick = { if (weeklyTarget > 1) weeklyTarget-- },
                            enabled = weeklyTarget > 1,
                        ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                        Text(
                            text = weeklyTarget.toString() + "x",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        TextButton(
                            onClick = { if (weeklyTarget < 7) weeklyTarget++ },
                            enabled = weeklyTarget < 7,
                        ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
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
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) scheme.primary else scheme.surfaceVariant,
                                    )
                                    .clickable {
                                        days = if (selected) days - day else days + day
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = DAY_LETTERS[day - 1],
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                FrequencyType.DAILY -> Unit
            }

            SheetSectionLabel("Sessão do dia")
            SegmentedRow(
                options = listOf("Manhã", "Tarde", "Noite"),
                selectedIndex = session.ordinal,
                onSelect = { session = Session.entries[it] },
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    if (saving || name.isBlank()) return@Button
                    saving = true
                    scope.launch {
                        deps.habits.save(
                            Habit(
                                id = newId(),
                                name = name.trim(),
                                icon = iconKey,
                                color = colorValue,
                                goalType = goalType,
                                targetQuantity = targetText.toDoubleOrNull() ?: 1.0,
                                quantityUnit = unit.trim().ifEmpty { "vezes" },
                                frequencyType = freqType,
                                daysOfWeek = if (freqType == FrequencyType.DAYS_OF_WEEK) {
                                    days.sorted()
                                } else {
                                    emptyList()
                                },
                                weeklyTarget = weeklyTarget,
                                session = session,
                                reminderEnabled = false,
                                reminderTime = "",
                                paused = false,
                                createdAt = "",
                                sortOrder = 0,
                            ),
                        )
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Criar hábito")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) scheme.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
