package app.constancia.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.domain.QuickAddParse
import app.constancia.domain.parseQuickAdd
import app.constancia.domain.quickSpanSummary
import app.constancia.theme.constanciaExtra
import kotlinx.datetime.LocalDate

/**
 * Item 6 — captura rápida com linguagem natural: enquanto digita,
 * os trechos reconhecidos (data, hora, p1..p3, #projeto, recorrência)
 * ganham destaque no próprio campo e viram chips removíveis logo
 * abaixo (tocar no × desfaz aquele reconhecimento e o trecho volta a
 * ser título). Usado na aba Tarefas e na captura da Hoje.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddField(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: (QuickAddParse) -> Unit,
    today: LocalDate,
    inputTag: String,
    addTag: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    var ignored by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }

    // O pai limpa o texto após adicionar: sincroniza e zera os desfazeres.
    LaunchedEffect(text) {
        if (text != fieldValue.text) {
            fieldValue = TextFieldValue(text)
        }
        if (text.isEmpty()) ignored = emptySet()
    }

    val parse = remember(fieldValue.text, ignored) {
        parseQuickAdd(fieldValue.text, today, ignored)
    }
    val annotated = remember(parse, fieldValue.text) {
        buildAnnotatedString {
            var cursor = 0
            for (span in parse.spans) {
                if (span.start > cursor) {
                    append(fieldValue.text.substring(cursor, span.start))
                }
                withStyle(
                    SpanStyle(
                        color = scheme.primary,
                        background = scheme.primaryContainer,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                ) {
                    append(fieldValue.text.substring(span.start, span.end))
                }
                cursor = span.end
            }
            if (cursor < fieldValue.text.length) {
                append(fieldValue.text.substring(cursor))
            }
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = scheme.secondary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = fieldValue.copy(annotatedString = annotated),
                onValueChange = { newValue ->
                    fieldValue = newValue
                    onTextChange(newValue.text)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = scheme.onSurface,
                ),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (parse.title.isNotBlank()) onAdd(parse) },
                ),
                decorationBox = { inner ->
                    Box {
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                ),
                                color = extra.placeholder,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).testTag(inputTag),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(scheme.primary)
                    .testTag(addTag)
                    .clickable { if (parse.title.isNotBlank()) onAdd(parse) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text = "Adicionar",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = scheme.onPrimary,
                )
            }
        }

        if (parse.spans.isNotEmpty()) {
            Spacer(Modifier.size(9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                parse.spans.forEach { span ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(scheme.primaryContainer)
                            .clickable { ignored = ignored + span.key }
                            .padding(start = 10.dp, top = 5.dp, end = 7.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = quickSpanSummary(span),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = scheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Desfazer reconhecimento",
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}
