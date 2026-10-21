package app.constancia.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import app.constancia.domain.REMINDER_OPTIONS
import app.constancia.domain.parseTimeOrNull
import app.constancia.domain.taskReminderLabel
import app.constancia.model.ReminderSpec
import app.constancia.model.ReminderType
import app.constancia.model.TaskReminder
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Fase C (R6.1) — editor da LISTA de lembretes de uma tarefa: chips
 * liga/desliga dos relativos padrão (na hora, 10/30/60 min, 1 dia
 * antes) e lembretes ABSOLUTOS ("no dia às HH:mm") removíveis um a um.
 * Usado no formulário de nova tarefa e no detalhe (linha expandida).
 * [absoluteDate] é o dia-âncora dos absolutos; null (tarefa sem data)
 * esconde a parte absoluta — sem dia, não há "no dia às" que faça
 * sentido.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderListEditor(
    specs: List<ReminderSpec>,
    onChange: (List<ReminderSpec>) -> Unit,
    absoluteDate: LocalDate?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var absTimeText by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            REMINDER_OPTIONS.forEach { (label, value) ->
                val offset = value ?: return@forEach
                val selected = specs.any {
                    it.type == ReminderType.RELATIVE &&
                        it.minuteOffset == offset
                }
                PickChip(label, selected) {
                    onChange(
                        if (selected) {
                            specs.filterNot {
                                it.type == ReminderType.RELATIVE &&
                                    it.minuteOffset == offset
                            }
                        } else {
                            specs + ReminderSpec(
                                ReminderType.RELATIVE,
                                minuteOffset = offset,
                            )
                        },
                    )
                }
            }
        }

        val absolutes = specs.filter { it.type == ReminderType.ABSOLUTE }
        if (absolutes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                absolutes.forEach { spec ->
                    val label = spec.atMillis?.let {
                        taskReminderLabel(
                            TaskReminder(
                                id = "", taskId = "",
                                type = ReminderType.ABSOLUTE, atMillis = it,
                            ),
                        )
                    } ?: "Horário fixo"
                    PickChip("$label ×", true) {
                        onChange(specs - spec)
                    }
                }
            }
        }

        if (absoluteDate != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = absTimeText,
                    onValueChange = { value ->
                        absTimeText = value
                            .filter { c -> c.isDigit() || c == ':' }
                            .take(5)
                    },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    modifier = Modifier.width(104.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(8.dp))
                PickChip("Adicionar horário fixo", false) {
                    val time = parseTimeOrNull(absTimeText)
                        ?: return@PickChip
                    val atMillis = LocalDateTime(absoluteDate, time)
                        .toInstant(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                    onChange(
                        specs + ReminderSpec(
                            ReminderType.ABSOLUTE, atMillis = atMillis,
                        ),
                    )
                    absTimeText = ""
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Horário fixo: dispara no dia da tarefa, " +
                    "na hora escolhida.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
