package app.constancia.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import app.constancia.domain.dueLabel
import app.constancia.domain.prioCode
import app.constancia.domain.prioName
import app.constancia.domain.subtaskProgress
import app.constancia.model.TaskItem
import app.constancia.model.toLocalDate
import app.constancia.theme.constanciaExtra
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Cor da prioridade (código semântico do protótipo): P1 vermelha, P2 âmbar/chama, P3 azul. */
@Composable
fun priorityColor(priority: Int): Color = when (priority) {
    1 -> Color(0xFFD92D20)
    2 -> Color(0xFFE86A17)
    else -> Color(0xFF2563EB)
}

@OptIn(ExperimentalLayoutApi::class)
/**
 * Linha de tarefa estilo Todoist: bolinha colorida por
 * prioridade, título, metadados (prazo, horário, P1–P3, repetição,
 * subtarefas, hábito vinculado, projeto) e, ao expandir, anotação,
 * subtarefas marcáveis, barra de progresso e ações.
 */
@Composable
fun TodoRow(
    task: TaskItem,
    today: LocalDate,
    habitName: String?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleDone: () -> Unit,
    onSubtaskToggle: (String, Boolean) -> Unit,
    onMoveToday: () -> Unit,
    onPostpone: () -> Unit,
    onCyclePriority: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val prioColor = priorityColor(task.priority)
    val progress = subtaskProgress(task.subtasks)
    val dueText = dueLabel(task, today)
    val isOverdue = !task.done && task.dueDate != null &&
        task.dueDate < today.toString()
    val isDueToday = task.dueDate == today.toString()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(scheme.surface, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.Top,
        ) {
            // Bolinha da prioridade (check quando feita).
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .testTag("task_check")
                    .semantics { contentDescription = "Concluir tarefa" }
                    .background(if (task.done) scheme.primary else Color.Transparent)
                    .then(
                        if (task.done) Modifier
                        else Modifier.border(2.dp, prioColor, CircleShape),
                    )
                    .clickable(onClick = onToggleDone),
                contentAlignment = Alignment.Center,
            ) {
                if (task.done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (dueText != null) {
                        MetaItem(
                            icon = Icons.Filled.CalendarMonth,
                            text = dueText,
                            color = when {
                                isOverdue -> extra.red
                                isDueToday -> scheme.primary
                                else -> scheme.onSurfaceVariant
                            },
                        )
                    }
                    if (task.dueTime != null) {
                        MetaItem(
                            icon = Icons.Filled.Schedule,
                            text = task.dueTime,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    MetaItem(
                        icon = Icons.Filled.Flag,
                        text = prioCode(task.priority),
                        color = prioColor,
                    )
                    if (task.repeatRule.isNotBlank()) {
                        MetaItem(
                            icon = Icons.Filled.Repeat,
                            text = task.repeatRule,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (progress != null) {
                        MetaItem(
                            icon = Icons.Filled.FormatListBulleted,
                            text = progress.done.toString() + "/" + progress.total,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (habitName != null) {
                        MetaItem(
                            icon = Icons.Filled.Whatshot,
                            text = habitName,
                            color = scheme.primary,
                        )
                    }
                    // Projeto à direita da linha de metadados.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp)
                                .background(Color(task.project.colorHex), CircleShape),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = task.project.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            if (task.note.isNotBlank()) {
                Text(
                    text = task.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (task.subtasks.isEmpty()) {
                Text(
                    text = "Sem subtarefas. Tarefas grandes rendem mais quebradas em passos pequenos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                task.subtasks.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSubtaskToggle(sub.id, !sub.done) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (sub.done) scheme.primary else Color.Transparent,
                                )
                                .then(
                                    if (sub.done) Modifier
                                    else Modifier.border(2.dp, scheme.outline, CircleShape),
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (sub.done) scheme.onSurfaceVariant else scheme.onSurface,
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
                    // Barra de progresso das subtarefas.
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(scheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.pct / 100f)
                                .height(6.dp)
                                .background(scheme.primary, RoundedCornerShape(999.dp)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!task.done && !isDueToday) {
                    MiniAction(
                        icon = Icons.Filled.WbSunny,
                        text = if (task.dueDate == null) "Agendar p/ hoje" else "Mover p/ hoje",
                        onClick = onMoveToday,
                    )
                }
                if (!task.done) {
                    MiniAction(
                        icon = Icons.Filled.CalendarMonth,
                        text = if (task.dueDate == null) "Agendar p/ amanhã" else "Adiar 1 dia",
                        onClick = onPostpone,
                    )
                }
                MiniAction(
                    icon = Icons.Filled.Flag,
                    text = "Prioridade: " + prioName(task.priority),
                    onClick = onCyclePriority,
                )
                MiniAction(
                    icon = Icons.Filled.Delete,
                    text = "Excluir",
                    onClick = onDelete,
                    danger = true,
                )
            }
        }
    }
}

/**
 * [TodoRow] com as ações ligadas aos repositórios (concluir, subtarefas,
 * mover para hoje, adiar, ciclar prioridade, excluir) — usado nas telas
 * Tarefas e Hoje para o comportamento ser idêntico nos dois lugares.
 */
@Composable
fun TodoRowWithActions(
    task: TaskItem,
    today: LocalDate,
    habitName: String?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    deps: app.constancia.AppDeps,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier,
) {
    TodoRow(
        task = task,
        today = today,
        habitName = habitName,
        expanded = expanded,
        onToggleExpand = onToggleExpand,
        onToggleDone = {
            scope.launch { deps.tasks.setDone(task.id, !task.done) }
        },
        onSubtaskToggle = { subId, done ->
            scope.launch { deps.tasks.setSubtaskDone(subId, done) }
        },
        onMoveToday = {
            scope.launch { deps.tasks.setDueDate(task.id, today.toString()) }
        },
        onPostpone = {
            val next = task.dueDate?.toLocalDate()
                ?.plus(kotlinx.datetime.DatePeriod(days = 1))
                ?: today.plus(kotlinx.datetime.DatePeriod(days = 1))
            scope.launch { deps.tasks.setDueDate(task.id, next.toString()) }
        },
        onCyclePriority = {
            scope.launch {
                deps.tasks.setPriority(task.id, app.constancia.domain.cyclePriority(task.priority))
            }
        },
        onDelete = {
            scope.launch { deps.tasks.delete(task.id) }
        },
        modifier = modifier,
    )
}

@Composable
private fun MetaItem(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
private fun MiniAction(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (danger) Color(0xFFD92D20) else scheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = color,
        )
    }
}
