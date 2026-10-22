package app.constancia.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.constancia.domain.applyTimeEdit
import app.constancia.domain.timeDisplayText
import app.constancia.domain.timeDigitsOf
import app.constancia.domain.timeOriginalToTransformed
import app.constancia.domain.timeTransformedToOriginal

/**
 * Campo de horário "HH:mm" (reteste 2, 2026-10-21).
 *
 * Substitui o padrão antigo (estado String + função que reformatava o
 * texto a cada tecla), que no aparelho real deixava o cursor preso no
 * fim, engolia o backspace sobre o ":" e reescrevia dígitos digitados.
 * Aqui o estado é o BUFFER DE DÍGITOS ([TextFieldValue] com o texto =
 * dígitos) e o ":" entra só na apresentação, via [VisualTransformation]
 * com o mapeamento de cursor de `domain/TimeInput.kt` — apagar,
 * corrigir no meio, selecionar tudo e colar se comportam como num
 * campo normal, e o texto exibido é sempre [timeDisplayText].
 *
 * Contrato com quem chama (igual ao do campo antigo): [value] é o
 * TEXTO EXIBIDO ("09:30", parcial ou "") e [onValueChange] recebe o
 * texto exibido a cada edição. Mudanças externas do [value] (ex.: o
 * formulário limpa o campo após adicionar um lembrete) ressincronizam
 * o buffer; o eco das próprias edições é reconhecido e ignorado.
 */
@Composable
fun TimeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "HH:mm",
) {
    var state by remember {
        val digits = timeDigitsOf(value)
        mutableStateOf(
            TextFieldValue(text = digits, selection = TextRange(digits.length)),
        )
    }
    // Ressincroniza quando o valor muda POR FORA (não pelo eco da
    // própria digitação, que já chega igual ao texto exibido atual).
    LaunchedEffect(value) {
        if (value != timeDisplayText(state.text)) {
            val digits = timeDigitsOf(value)
            state = TextFieldValue(
                text = digits,
                selection = TextRange(digits.length),
            )
        }
    }
    val transformation = remember { TimeMaskTransformation() }
    OutlinedTextField(
        value = state,
        onValueChange = { newValue ->
            state = if (newValue.text == state.text) {
                // Só cursor/seleção mudou (toque, seleção) — aceita como veio.
                newValue
            } else {
                val edit = applyTimeEdit(newValue.text, newValue.selection.end)
                TextFieldValue(
                    text = edit.digits,
                    selection = TextRange(edit.cursor),
                )
            }
            onValueChange(timeDisplayText(state.text))
        },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = transformation,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    )
}

/** ":" só na apresentação: o texto real do campo são os dígitos. */
private class TimeMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                timeOriginalToTransformed(digits, offset)

            override fun transformedToOriginal(offset: Int): Int =
                timeTransformedToOriginal(digits, offset)
        }
        return TransformedText(AnnotatedString(timeDisplayText(digits)), mapping)
    }
}
