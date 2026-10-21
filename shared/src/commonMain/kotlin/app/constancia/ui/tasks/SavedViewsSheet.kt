package app.constancia.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.model.SavedView
import app.constancia.model.TaskProject
import app.constancia.theme.constanciaExtra
import kotlinx.datetime.Clock

/**
 * Item 10 — folha de nova vista salva: nome, cor, projeto, prioridade,
 * período e agrupamento. A vista vira um chip na barra de visões.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewSavedViewSheet(onDismiss: () -> Unit, onSave: (SavedView) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(VIEW_COLOR_CHOICES.first()) }
    var project by remember { mutableStateOf<TaskProject?>(null) }
    var priority by remember { mutableStateOf<Int?>(null) }
    var period by remember { mutableStateOf("all") }
    var groupBy by remember { mutableStateOf("none") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Nova vista",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Um atalho com filtros seus: projeto, prioridade " +
                    "e período, sempre à mão na barra de visões.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 30) name = it },
                label = { Text("Nome da vista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SheetLabel("Cor")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                VIEW_COLOR_CHOICES.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                if (color == hex) 3.dp else 0.dp,
                                scheme.onSurface,
                                CircleShape,
                            )
                            .clickable { color = hex },
                    )
                }
            }
            SheetLabel("Projeto")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickChip("Todos", project == null) { project = null }
                TaskProject.entries.forEach { p ->
                    PickChip(p.label, project == p) { project = p }
                }
            }
            SheetLabel("Prioridade")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickChip("Todas", priority == null) { priority = null }
                listOf(1 to "P1 alta", 2 to "P2 média", 3 to "P3 baixa")
                    .forEach { (value, label) ->
                        PickChip(label, priority == value) { priority = value }
                    }
            }
            SheetLabel("Período")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "all" to "Todas",
                    "today" to "Hoje",
                    "week" to "Próximos 7 dias",
                    "overdue" to "Atrasadas",
                    "nodate" to "Sem data",
                ).forEach { (value, label) ->
                    PickChip(label, period == value) { period = value }
                }
            }
            SheetLabel("Agrupar por")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "none" to "Não agrupar",
                    "project" to "Projeto",
                    "priority" to "Prioridade",
                    "date" to "Data",
                ).forEach { (value, label) ->
                    PickChip(label, groupBy == value) { groupBy = value }
                }
            }
            Spacer(Modifier.height(22.dp))
            val canSave = name.isNotBlank()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (canSave) {
                            scheme.primary
                        } else {
                            extra.checkBorder
                        },
                    )
                    .clickable(enabled = canSave) {
                        onSave(
                            SavedView(
                                id = "sv_" +
                                    Clock.System.now()
                                        .toEpochMilliseconds()
                                        .toString(),
                                name = name.trim(),
                                colorHex = color,
                                project = project,
                                priority = priority,
                                period = period,
                                groupBy = groupBy,
                            ),
                        )
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Salvar vista",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSave) {
                        scheme.onPrimary
                    } else {
                        scheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
internal fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) extra.chipInk else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (selected) scheme.surface else scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}


/** Paleta das vistas salvas: a cor da marca + as cores semânticas do app. */
private val VIEW_COLOR_CHOICES = listOf(
    0xFF6D28D9,
    0xFF2563EB,
    0xFF0B7A55,
    0xFFE86A17,
    0xFFD92D20,
    0xFFDB2777,
    0xFF0891B2,
    0xFF4B5563,
)
