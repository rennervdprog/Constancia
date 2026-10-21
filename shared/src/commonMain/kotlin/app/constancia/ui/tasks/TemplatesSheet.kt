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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.constancia.model.NewTask
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.TaskTemplate

/**
 * Item 11 — folha de modelos de lista: criar as tarefas de um modelo
 * (com a data de hoje), salvar as tarefas abertas de um projeto como
 * modelo novo e excluir modelos.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplatesSheet(
    templates: List<TaskTemplate>,
    tasks: List<TaskItem>,
    onDismiss: () -> Unit,
    onInstantiate: (String) -> Unit,
    onSaveFromProject: (String, List<NewTask>) -> Unit,
    onDelete: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var saveProject by remember { mutableStateOf(TaskProject.ENTRADA) }
    val openOfProject = tasks.filter {
        !it.done && it.project == saveProject
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Modelos de lista",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Listas que se repetem na sua vida: salve uma vez " +
                    "e recrie as tarefas com um toque.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            if (templates.isEmpty()) {
                Text(
                    text = "Nenhum modelo ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            templates.forEach { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = template.items.size.toString() +
                                " tarefas" +
                                if (template.isFactory) {
                                    " · modelo de fábrica"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(scheme.primaryContainer)
                            .clickable { onInstantiate(template.id) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "Criar tarefas",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable { onDelete(template.id) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "Excluir",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.error,
                        )
                    }
                }
            }

            SheetLabel("Salvar tarefas abertas de um projeto como modelo")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskProject.entries.forEach { project ->
                    PickChip(
                        project.label,
                        saveProject == project,
                    ) { saveProject = project }
                }
            }
            Spacer(Modifier.height(12.dp))
            val canSave = openOfProject.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (canSave) {
                            scheme.primary
                        } else {
                            scheme.surfaceVariant
                        },
                    )
                    .clickable(enabled = canSave) {
                        onSaveFromProject(
                            "Lista de " + saveProject.label,
                            openOfProject.map { task ->
                                NewTask(
                                    title = task.title,
                                    note = task.note,
                                    project = task.project,
                                    priority = task.priority,
                                    session = task.session,
                                )
                            },
                        )
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (canSave) {
                        "Salvar " + openOfProject.size.toString() +
                            " tarefas como modelo"
                    } else {
                        "Sem tarefas abertas neste projeto"
                    },
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
