package app.constancia.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.constancia.domain.timeSortKey
import app.constancia.model.CheckinStatus
import app.constancia.model.HabitStatus
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.ui.common.habitColorOf
import app.constancia.ui.common.habitIcon

/**
 * Cartão "Agora" da Hoje (pacote aprovado em 2026-11-01 a partir de
 * protótipo visual): mostra o próximo item pendente do dia — sessão
 * atual primeiro, depois as seguintes, por fim as anteriores — com a
 * ação principal na mão (check direto). Some quando o dia fecha.
 */
sealed interface AgoraTarget {
    data class HabitTarget(val status: HabitStatus) : AgoraTarget
    data class TaskTarget(val task: TaskItem) : AgoraTarget
    data object DiaryTarget : AgoraTarget
}

private fun AgoraTarget.sortKey(): String = when (this) {
    is AgoraTarget.HabitTarget -> timeSortKey(
        if (status.habit.reminderEnabled) status.habit.reminderTime else null,
    ) + "0"
    is AgoraTarget.TaskTarget -> timeSortKey(task.dueTime) + "1"
    AgoraTarget.DiaryTarget -> "99:99" + "2"
}

/** Próximo item pendente do dia, ou null quando está tudo feito. */
fun pickAgoraTarget(
    habits: List<HabitStatus>,
    tasks: List<TaskItem>,
    diaryDone: Boolean,
    current: Session,
): AgoraTarget? {
    val bySession = mutableMapOf<Session, MutableList<AgoraTarget>>()
    habits.filter {
        !it.isDoneToday && it.todayCheckin?.status != CheckinStatus.SKIPPED
    }.forEach {
        bySession.getOrPut(it.habit.session) { mutableListOf() }
            .add(AgoraTarget.HabitTarget(it))
    }
    tasks.filter { !it.done }.forEach {
        bySession.getOrPut(it.session) { mutableListOf() }
            .add(AgoraTarget.TaskTarget(it))
    }
    val order = Session.entries.let { all ->
        val start = all.indexOf(current)
        all.drop(start) + all.take(start)
    }
    for (session in order) {
        val candidates = bySession[session].orEmpty()
            .sortedBy { it.sortKey() }
        if (candidates.isNotEmpty()) return candidates.first()
    }
    // O diário é o fechamento do dia: só vira o "Agora" quando não
    // resta hábito nem tarefa pendente em sessão nenhuma. (Na 1ª
    // versão ele disputava pela sessão Noite e vencia um hábito da
    // Manhã ainda pendente às 13h — provado pelo print 39 da
    // rodada 2 do roteiro 28.)
    return if (!diaryDone) AgoraTarget.DiaryTarget else null
}

private fun agoraSessionLabel(session: Session): String = when (session) {
    Session.MANHA -> "Manhã"
    Session.TARDE -> "Tarde"
    Session.NOITE -> "Noite"
}

@Composable
fun AgoraCard(
    target: AgoraTarget,
    onCheckHabit: (HabitStatus) -> Unit,
    onCheckTask: (TaskItem) -> Unit,
    onOpenDiary: () -> Unit,
    onOpenTimer: (HabitStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(modifier = Modifier.padding(top = 9.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = scheme.primary.copy(alpha = 0.35f),
                        ambientColor = scheme.primary.copy(alpha = 0.35f),
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surface)
                    .border(1.5.dp, scheme.primary, RoundedCornerShape(20.dp))
                    .clickable {
                        when (target) {
                            is AgoraTarget.HabitTarget -> onOpenTimer(target.status)
                            is AgoraTarget.TaskTarget -> onCheckTask(target.task)
                            AgoraTarget.DiaryTarget -> onOpenDiary()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
                    .testTag("agora_card"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (target) {
                    is AgoraTarget.HabitTarget -> {
                        val habit = target.status.habit
                        val color = habitColorOf(habit.color)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                habitIcon(habit.icon),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = habit.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Hábito · " +
                                    agoraSessionLabel(habit.session) +
                                    if (habit.reminderEnabled &&
                                        habit.reminderTime.isNotBlank()
                                    ) {
                                        " · " + habit.reminderTime
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    is AgoraTarget.TaskTarget -> {
                        val task = target.task
                        val color = Color(task.project.colorHex)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(color.copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.FormatListBulleted,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Tarefa · " + task.project.label +
                                    " · " + agoraSessionLabel(task.session),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    AgoraTarget.DiaryTarget -> {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(scheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Escrever no diário",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Diário · Noite",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(scheme.primary)
                        .clickable {
                            when (target) {
                                is AgoraTarget.HabitTarget ->
                                    onCheckHabit(target.status)
                                is AgoraTarget.TaskTarget ->
                                    onCheckTask(target.task)
                                AgoraTarget.DiaryTarget -> onOpenDiary()
                            }
                        }
                        .testTag("agora_check"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (target is AgoraTarget.DiaryTarget) {
                            Icons.Filled.ArrowForward
                        } else {
                            Icons.Filled.Check
                        },
                        contentDescription = if (target is AgoraTarget.DiaryTarget) {
                            "Abrir o diário"
                        } else {
                            "Marcar como feito"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 14.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(scheme.primary)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(
                text = "AGORA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.09.em,
                ),
                color = Color.White,
            )
        }
    }
}
