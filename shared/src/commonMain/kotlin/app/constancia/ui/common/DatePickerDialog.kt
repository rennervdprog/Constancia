package app.constancia.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import androidx.compose.material3.DatePickerDialog as M3DatePickerDialog

/**
 * T2 (auditoria de tarefas 2026-10-30): seletor de data em
 * calendário do Material 3 — o mesmo padrão do seletor de relógio
 * (`TimePickerDialog`): componente COMUM, então vale igual no
 * Android e no iOS (o calendário nativo de cada sistema só
 * existiria num deles e o código é único). Antes não havia como
 * escolher uma data específica na criação/edição de tarefa:
 * "Quando" só tinha Sem data / Hoje / Amanhã.
 *
 * A conversão usa UTC dos dois lados: o estado do seletor guarda
 * milissegundos em UTC, e a data do app é um dia civil — converter
 * pelo fuso local deslocaria o dia em fusos negativos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstanciaDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds(),
    )
    M3DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val ms = state.selectedDateMillis
                    if (ms != null) {
                        onConfirm(
                            Instant.fromEpochMilliseconds(ms)
                                .toLocalDateTime(TimeZone.UTC).date,
                        )
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
