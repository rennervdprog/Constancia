package app.constancia.ui.diary

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import kotlinx.coroutines.launch

private val moodLabels = listOf("Péssimo", "Ruim", "Ok", "Bom", "Ótimo")

private fun formatDdMm(ymd: String): String {
    val d = ymd.toLocalDate()
    val day = d.dayOfMonth.toString().padStart(2, '0')
    val month = d.monthNumber.toString().padStart(2, '0')
    return "$day/$month"
}

@Composable
fun DiaryScreen(deps: AppDeps, modifier: Modifier = Modifier) {
    val today = remember { todayYmd() }
    val scope = rememberCoroutineScope()
    val todayEntry by deps.diary.entryForDateFlow(today).collectAsState(initial = null)
    val recent by deps.diary.recentFlow(30).collectAsState(initial = emptyList())

    var mood by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(todayEntry) {
        if (!initialized && todayEntry != null) {
            mood = todayEntry?.mood
            note = todayEntry?.note ?: ""
            initialized = true
        }
    }

    val history = recent.filter { it.date != today }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Diário", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Um minuto por dia: como você está e como foi o dia",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Como foi seu dia?",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        moodLabels.forEachIndexed { index, label ->
                            val value = index + 1
                            val selected = mood == value
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { mood = value },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        value.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Como foi seu dia? Uma ou duas linhas bastam.") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                deps.diary.save(today, mood, note.trim())
                                savedMsg = "Diário salvo"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (todayEntry != null) "Atualizar diário" else "Salvar no diário")
                    }
                    if (savedMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            savedMsg ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Dias anteriores",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (history.size == 1) "1 registro" else "${history.size} registros",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(history, key = { it.date }) { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier.size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (entry.mood ?: 3).toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            formatDdMm(entry.date) + " · " +
                                (moodLabels.getOrNull((entry.mood ?: 3) - 1) ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (entry.note.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                entry.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
