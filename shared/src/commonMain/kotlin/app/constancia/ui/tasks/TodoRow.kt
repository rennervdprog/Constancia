package app.constancia.ui.tasks

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import app.constancia.domain.dueLabel
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
 * Linha de tarefa do protótipo (.trow), pensada para viver dentro do
 * cartão agrupado da seção: bolinha de 23dp com a cor/fundo da
 * prioridade, título 14.5/600, metadados discretos (data, projeto com
 * ponto, progresso de subtarefas, recorrência e o selo do hábito
 * vinculado) e, ao expandir, anotação, subtarefas marcáveis e as ações
 * em etiquetas (mover/adiar, prioridade, excluir).
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
            .padding(horizontal = 15.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.Top,
        ) {
            // Bolinha da prioridade (check roxo quando feita).
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(23.dp)
                    .clip(CircleShape)
                    .testTag("task_check")
                    .semantics { contentDescription = "Concluir tarefa" }
                    .background(
                        if (task.done) scheme.primary
                        else prioColor.copy(alpha = 0.12f),
                    )
                    .then(
                        if (task.done) Modifier
                        else Modifier.border(2.2.dp, prioColor, CircleShape),
                    )
                    .clickable(onClick = onToggleDone),
                contentAlignment = Alignment.Center,
            ) {
                if (task.done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (task.done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(3.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (dueText != null) {
                        Text(
                            text = dueText.lowercase() +
                                (task.dueTime?.let { " $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                            ),
                            color = when {
                                isOverdue -> extra.red
                                isDueToday -> scheme.primary
                                else -> scheme.onSurfaceVariant
                            },
                        )
                    }
                    // Projeto: ponto colorido + nome.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp)
                                .clip(CircleShape)
                                .background(Color(task.project.colorHex)),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = task.project.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                            ),
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (progress != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(38.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(scheme.outline),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress.pct / 100f)
                                        .height(5.dp)
                                        .background(
                                            scheme.primary,
                                            RoundedCornerShape(99.dp),
                                        ),
                                )
                            }
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = progress.done.toString() + "/" +
                                    progress.total.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                ),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (task.repeatRule.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Repeat,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = task.repeatRule,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                ),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (habitName != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(scheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Whatshot,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "marca o hábito " + habitName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = scheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Column(modifier = Modifier.padding(start = 34.dp)) {
                if (task.note.isNotBlank()) {
                    Text(
                        text = task.note,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                        ),
                        color = extra.ink2,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (task.subtasks.isEmpty()) {
                    Text(
                        text = "Sem subtarefas. Tarefas grandes rendem mais " +
                            "quebradas em passos pequenos.",
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
                                        if (sub.done) scheme.primary
                                        else Color.Transparent,
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
                            modifier = Modifier.fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(scheme.outline),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.pct / 100f)
                                    .height(5.dp)
                                    .background(
                                        scheme.primary,
                                        RoundedCornerShape(99.dp),
                                    ),
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
                        TagAction(
                            text = if (task.dueDate == null) {
                                "Agendar p/ hoje"
                            } else {
                                "Mover p/ hoje"
                            },
                            onClick = onMoveToday,
                        )
                    }
                    if (!task.done) {
                        TagAction(
                            text = if (task.dueDate == null) {
                                "Agendar p/ amanhã"
                            } else {
                                "Adiar p/ amanhã"
                            },
                            onClick = onPostpone,
                        )
                    }
                    TagAction(
                        text = "Prioridade: " + prioName(task.priority),
                        onClick = onCyclePriority,
                    )
                    TagAction(
                        text = "Excluir",
                        onClick = onDelete,
                        danger = true,
                    )
                }
            }
        }
    }
}

/**
 * [TodoRow] com as ações ligadas aos repositórios (concluir, subtarefas,
 * mover para hoje, adiar, ciclar prioridade, excluir) — usado na aba
 * Tarefas; a Hoje tem a linha própria dela (com tile de projeto).
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

/** Etiqueta de ação da linha expandida (estilo das tags do protótipo). */
@Composable
private fun TagAction(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(scheme.surfaceVariant)
            .border(1.dp, scheme.outline, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
            color = if (danger) extra.red else extra.ink2,
        )
    }
}
