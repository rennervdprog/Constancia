package app.constancia.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.constancia.domain.parseTimeOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Seletor de horário em relógio (Material 3) — o mesmo visual do
 * seletor do sistema Android, direto do código compartilhado (vale
 * igual no iOS). Abre a partir de [initialText] ("HH:mm", parcial ou
 * vazio): horário válido vira o ponto de partida; vazio/inválido
 * começa na hora atual. Confirmar devolve "HH:mm" canônico.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parsed = parseTimeOrNull(initialText)
    val now = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val state = rememberTimePickerState(
        initialHour = parsed?.hour ?: now.hour,
        initialMinute = parsed?.minute ?: now.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher horário") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hh = state.hour.toString().padStart(2, '0')
                    val mm = state.minute.toString().padStart(2, '0')
                    onConfirm("$hh:$mm")
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
