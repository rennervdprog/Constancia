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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import app.constancia.domain.RescheduleTarget
import app.constancia.domain.dueDateLabel
import app.constancia.domain.dueLabel
import app.constancia.domain.durationLabel
import app.constancia.domain.prioName
import app.constancia.domain.rescheduleDate
import app.constancia.domain.rescheduleDateFrom
import app.constancia.domain.rescheduleLabel
import app.constancia.domain.subtaskProgress
import app.constancia.domain.taskEffDate
import app.constancia.domain.taskMomentPast
import app.constancia.model.TaskItem
import app.constancia.model.toLocalDate
import app.constancia.theme.constanciaExtra
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

/** Cor da prioridade (código semântico do protótipo): P1 vermelha, P2 âmbar/chama, P3 azul. */
@Composable
fun priorityColor(priority: Int): Color = when (priority) {
    1 -> Color(0xFFD92D20)
    2 -> Color(0xFFE86A17)
    else -> Color(0xFF2563EB)
}

@OptIn(ExperimentalLayoutApi::class)
/**
 * Linha de tarefa pensada para viver dentro do cartão agrupado da
 * seção: check circular neutro, título 14.5/600 (com selo P1 quando a
 * prioridade é alta) e metadados em chips (data/hora, projeto, prazo,
 * duração, lembrete e recorrência). O progresso das subtarefas tem uma
 * linha própria; ao expandir, aparecem anotação, subtarefas marcáveis
 * e as ações em etiquetas (mover/adiar, prioridade, excluir).
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
    onReschedule: (String) -> Unit,
    // R2.3 (Fase A): concluir registrando a conclusão como de ontem.
    onCompleteYesterday: () -> Unit = {},
    // R6.1 (Fase C): substituir a LISTA de lembretes da tarefa.
    onSetReminders: (List<app.constancia.model.ReminderSpec>) -> Unit = {},
    // Auditoria 2026-10-30: abrir o formulário em modo edição e
    // adicionar subtarefa pelo detalhe (antes não existiam).
    onEdit: () -> Unit = {},
    onAddSubtask: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var newSubText by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val progress = subtaskProgress(task.subtasks)
    val dueText = dueLabel(task, today)
    val deadline = task.deadlineDate
    val duration = task.durationMinutes
    val isOverdue = !task.done && task.dueDate != null &&
        task.dueDate < today.toString()
    // "Passou do horário" (2026-11-02): tarefa DE HOJE cujo momento já
    // passou (horário marcado ou fim da sessão) recebe o mesmo
    // tratamento visual de atrasada — às 22h ela não é um "a fazer"
    // comum. Concluir atrasada continua valendo normalmente.
    val nowLocalRow = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val isLateToday = !task.done && taskEffDate(task) == today.toString() &&
        taskMomentPast(
            task.session, task.dueTime,
            nowLocalRow.hour * 60 + nowLocalRow.minute,
        )
    val isOverdueVisual = isOverdue || isLateToday
    val isDueToday = task.dueDate == today.toString()
    val isDeadlineOverdue = !task.done && deadline != null &&
        deadline < today.toString()
    val isDeadlineToday = !task.done && deadline == today.toString()
    val reminderSummary = app.constancia.domain.remindersSummary(task)
        ?.lowercase()?.takeIf { it.isNotBlank() }

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
            // Check neutro: a prioridade aparece no selo P1 do título,
            // não na cor do círculo (check roxo quando feita).
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .testTag("task_check")
                    .semantics { contentDescription = "Concluir tarefa" }
                    .background(
                        if (task.done) scheme.primary else Color.Transparent,
                    )
                    .then(
                        if (task.done) Modifier
                        else Modifier.border(2.dp, extra.checkBorder, CircleShape),
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
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = if (task.done) {
                            scheme.onSurfaceVariant
                        } else {
                            scheme.onSurface
                        },
                        textDecoration = if (task.done) {
                            TextDecoration.LineThrough
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (task.priority == 1) {
                        Spacer(Modifier.width(7.dp))
                        Box(
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(extra.red)
                                .padding(horizontal = 6.dp, vertical = 2.5.dp),
                        ) {
                            Text(
                                text = "P1",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp,
                                ),
                                color = Color.White,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (dueText != null || task.dueTime != null || isLateToday) {
                        MetaChip(
                            text = listOfNotNull(
                                if (isLateToday && !isOverdue) {
                                    "atrasada"
                                } else {
                                    null
                                },
                                dueText?.lowercase(), task.dueTime,
                            ).joinToString(" "),
                            background = when {
                                isOverdueVisual -> extra.redSoft
                                isDueToday -> scheme.primaryContainer
                                else -> extra.pageChip
                            },
                            contentColor = when {
                                isOverdueVisual -> extra.red
                                isDueToday -> scheme.onPrimaryContainer
                                else -> extra.ink2
                            },
                            borderColor = when {
                                isOverdueVisual -> extra.redSoft
                                isDueToday -> scheme.primaryContainer
                                else -> extra.lineSoft
                            },
                            icon = {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = when {
                                        isOverdueVisual -> extra.red
                                        isDueToday -> scheme.primary
                                        else -> extra.ink3
                                    },
                                    modifier = Modifier.size(12.dp),
                                )
                            },
                        )
                    }
                    // Projeto: ponto colorido + nome.
                    MetaChip(
                        text = task.project.label,
                        background = extra.pageChip,
                        contentColor = extra.ink2,
                        borderColor = extra.lineSoft,
                        icon = {
                            Box(
                                modifier = Modifier.size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(task.project.colorHex)),
                            )
                        },
                    )
                    if (task.repeatRule.isNotBlank()) {
                        MetaChip(
                            text = task.repeatRule,
                            background = extra.pageChip,
                            contentColor = extra.ink2,
                            borderColor = extra.lineSoft,
                            icon = {
                                Icon(
                                    Icons.Filled.Repeat,
                                    contentDescription = null,
                                    tint = extra.ink3,
                                    modifier = Modifier.size(12.dp),
                                )
                            },
                        )
                    }
                    // Prazo final (item 8): separado da data agendada.
                    if (deadline != null) {
                        MetaChip(
                            text = "prazo " + dueDateLabel(
                                deadline.toLocalDate(), today,
                            ).lowercase(),
                            background = when {
                                isDeadlineOverdue -> extra.redSoft
                                isDeadlineToday -> extra.amberSoft
                                else -> extra.pageChip
                            },
                            contentColor = when {
                                isDeadlineOverdue -> extra.red
                                isDeadlineToday -> extra.amberStrong
                                else -> extra.ink2
                            },
                            borderColor = when {
                                isDeadlineOverdue -> extra.redSoft
                                isDeadlineToday -> extra.amberSoft
                                else -> extra.lineSoft
                            },
                            icon = {
                                Icon(
                                    Icons.Filled.Flag,
                                    contentDescription = null,
                                    tint = when {
                                        isDeadlineOverdue -> extra.red
                                        isDeadlineToday -> extra.amberStrong
                                        else -> extra.ink3
                                    },
                                    modifier = Modifier.size(12.dp),
                                )
                            },
                        )
                    }
                    if (duration != null) {
                        MetaChip(
                            text = "~" + durationLabel(duration),
                            background = extra.pageChip,
                            contentColor = extra.ink2,
                            borderColor = extra.lineSoft,
                            icon = {
                                Icon(
                                    Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = extra.ink3,
                                    modifier = Modifier.size(12.dp),
                                )
                            },
                        )
                    }
                    if (reminderSummary != null) {
                        MetaChip(
                            text = reminderSummary,
                            background = extra.pageChip,
                            contentColor = extra.ink2,
                            borderColor = extra.lineSoft,
                            icon = {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = null,
                                    tint = extra.ink3,
                                    modifier = Modifier.size(12.dp),
                                )
                            },
                        )
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
                if (progress != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(extra.lineSoft),
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
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = progress.done.toString() + "/" +
                                progress.total.toString() + " subtarefas",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                }
                // Adicionar subtarefa pelo detalhe (auditoria
                // 2026-10-30): a função existia no repositório, mas
                // nenhuma tela chamava — subtarefa só nascia na criação.
                if (!task.done) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newSubText,
                            onValueChange = {
                                if (it.length <= 50) newSubText = it
                            },
                            placeholder = { Text("Adicionar subtarefa…") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val v = newSubText.trim()
                                    if (v.isNotEmpty()) {
                                        onAddSubtask(v)
                                        newSubText = ""
                                    }
                                },
                            ),
                            modifier = Modifier.weight(1f)
                                .testTag("task_detail_sub_input"),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(40.dp)
                                .background(scheme.primary, CircleShape)
                                .testTag("task_detail_sub_add")
                                .clickable {
                                    val v = newSubText.trim()
                                    if (v.isNotEmpty()) {
                                        onAddSubtask(v)
                                        newSubText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Adicionar subtarefa",
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
                // Fase C (R6.1): a LISTA de lembretes é editável aqui,
                // no detalhe da tarefa (relativos + horário fixo).
                if (!task.done) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Lembretes",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    ReminderListEditor(
                        specs = task.reminders.map {
                            app.constancia.model.ReminderSpec(
                                it.type, it.minuteOffset, it.atMillis,
                            )
                        },
                        onChange = onSetReminders,
                        absoluteDate = task.dueDate?.toLocalDate(),
                        // Sem data, relativo nunca dispara: chips
                        // inertes com aviso (auditoria 2026-10-30).
                        relativeEnabled = task.dueDate != null,
                    )
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
                    // R2.3: concluir com a data de conclusão de ontem.
                    if (!task.done) {
                        TagAction(
                            text = "Concluir como de ontem",
                            onClick = onCompleteYesterday,
                        )
                    }
                    // Item 1: chips de reagendamento rápido.
                    if (!task.done) {
                        RescheduleTarget.entries.forEach { target ->
                            TagAction(
                                text = rescheduleLabel(target),
                                onClick = {
                                    onReschedule(
                                        rescheduleDate(target, today)
                                            .toString(),
                                    )
                                },
                            )
                        }
                        // R5.1: chips relativos à DATA DA TAREFA.
                        val taskDate = task.dueDate?.toLocalDate()
                        if (taskDate != null) {
                            TagAction(
                                text = "+3 dias da data",
                                onClick = {
                                    onReschedule(
                                        rescheduleDateFrom(
                                            RescheduleTarget.PLUS_3_DAYS,
                                            taskDate,
                                        ).toString(),
                                    )
                                },
                            )
                            TagAction(
                                text = "+1 semana da data",
                                onClick = {
                                    onReschedule(
                                        rescheduleDateFrom(
                                            RescheduleTarget.PLUS_1_WEEK,
                                            taskDate,
                                        ).toString(),
                                    )
                                },
                            )
                            TagAction(
                                text = "+1 mês da data",
                                onClick = {
                                    onReschedule(
                                        rescheduleDateFrom(
                                            RescheduleTarget.NEXT_MONTH,
                                            taskDate,
                                        ).toString(),
                                    )
                                },
                            )
                        }
                    }
                    TagAction(
                        text = "Prioridade: " + prioName(task.priority),
                        onClick = onCyclePriority,
                    )
                    TagAction(
                        text = "Editar",
                        onClick = onEdit,
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

/** Chip compacto de metadado da tarefa (data, projeto, prazo etc.). */
@Composable
private fun MetaChip(
    text: String,
    background: Color,
    contentColor: Color,
    borderColor: Color,
    icon: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    // R2.2 (Fase B): chamado ao concluir, com o pacote de reversão —
    // a tela usa para oferecer o "Desfazer" (snackbar/botão).
    onCompleted: (app.constancia.model.CompletionReversal?) -> Unit = {},
) {
    var showEditSheet by remember { mutableStateOf(false) }
    TodoRow(
        task = task,
        today = today,
        habitName = habitName,
        expanded = expanded,
        onToggleExpand = onToggleExpand,
        onToggleDone = {
            scope.launch {
                val reversal = deps.tasks.setDone(task.id, !task.done)
                if (!task.done) onCompleted(reversal)
            }
        },
        onSubtaskToggle = { subId, done ->
            scope.launch { deps.tasks.setSubtaskDone(subId, done) }
        },
        onMoveToday = {
            scope.launch { deps.tasks.setDueDate(task.id, today.toString()) }
        },
        onPostpone = {
            // M8: adiar leva PARA amanhã (postponeTarget).
            val next = app.constancia.domain.postponeTarget(task.dueDate, today)
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
        onReschedule = { ymd ->
            scope.launch { deps.tasks.setDueDate(task.id, ymd) }
        },
        onSetReminders = { specs ->
            scope.launch { deps.tasks.setTaskReminders(task.id, specs) }
        },
        onEdit = { showEditSheet = true },
        onAddSubtask = { title ->
            scope.launch { deps.tasks.addSubtask(task.id, title) }
        },
        onCompleteYesterday = {
            scope.launch {
                val reversal = deps.tasks.setDone(
                    task.id, true,
                    today.minus(kotlinx.datetime.DatePeriod(days = 1)),
                )
                onCompleted(reversal)
            }
        },
        modifier = modifier,
    )
    if (showEditSheet) {
        NewTaskSheet(
            deps = deps,
            onDismiss = { showEditSheet = false },
            existing = task,
        )
    }
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
