package app.constancia.ui.tasks

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.domain.REPEAT_OPTIONS
import app.constancia.model.NewTask
import app.constancia.model.Session
import app.constancia.model.TaskProject
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.ui.common.CtaButton
import app.constancia.ui.common.SegmentedRow
import app.constancia.ui.common.SheetSectionLabel
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/**
 * Formulário completo de nova tarefa (protótipo aprovado): nome, lista com
 * cor, prioridade, quando, sessão, horário, repetir, subtarefas e anotação.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var repeat by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var subs by remember { mutableStateOf<List<String>>(emptyList()) }
    var subText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

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
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { v ->
                        timeText = v.filter { c -> c.isDigit() || c == ':' }.take(5)
                    },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            SheetSectionLabel("Repetir")
            SegmentedRow(
                options = listOf("Não repete", "Todo dia", "Seg a Sex", "Semanal"),
                selectedIndex = REPEAT_OPTIONS.indexOf(repeat).coerceAtLeast(0),
                onSelect = { repeat = REPEAT_OPTIONS[it] },
                tagPrefix = "task_repeat",
            )

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
                    val dueDate = when (dueChoice) {
                        0 -> null
                        1 -> today
                        else -> today.toLocalDate()
                            .plus(DatePeriod(days = 1))
                            .toString()
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
                                repeatRule = repeat,
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
