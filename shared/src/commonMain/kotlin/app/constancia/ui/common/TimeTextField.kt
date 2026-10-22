package app.constancia.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * texto exibido a cada edição.
 *
 * Ressincronização externa SEM corrida: a primeira versão usava
 * `LaunchedEffect(value)` comparando o value (que volta do pai com
 * atraso de um frame) com o buffer atual — digitando rápido, o efeito
 * de um eco antigo rodava quando o buffer já tinha o dígito seguinte
 * e REBOBINAVA o campo, engolindo tecla (o teste instrumentado de
 * sequências reais pegou: digitar 930 terminava em "090"). Agora a
 * comparação é feita DURANTE a composição (padrão oficial de
 * "ajustar estado quando a entrada muda"): value que é eco do que
 * este campo emitiu é ignorado; qualquer outro value novo é mudança
 * externa (ex.: o formulário limpa o campo) e ressincroniza o buffer.
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
    // Último value visto na composição e último texto emitido ao pai:
    // juntos distinguem "eco da minha digitação" de "mudança externa".
    var lastSeenValue by remember { mutableStateOf(value) }
    var lastEmitted by remember { mutableStateOf<String?>(null) }
    if (value != lastSeenValue) {
        if (value != lastEmitted) {
            val digits = timeDigitsOf(value)
            state = TextFieldValue(
                text = digits,
                selection = TextRange(digits.length),
            )
        }
        lastSeenValue = value
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
            val display = timeDisplayText(state.text)
            lastEmitted = display
            onValueChange(display)
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
