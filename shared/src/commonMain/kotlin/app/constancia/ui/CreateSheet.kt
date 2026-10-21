package app.constancia.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.theme.constanciaExtra

/**
 * Folha do botão central (+) — "O que você quer criar?" (protótipo
 * aprovado): título Sora 18/800, subtítulo e as opções em linhas com
 * tile suave na cor de cada tipo, separadas por divisórias finas.
 * (O protótipo mostra também "Novo projeto", mas os projetos do app são
 * fixos por área — sem fluxo de criação, a opção fica de fora.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(
    onCreateHabit: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateDiary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 24.dp),
        ) {
            Text(
                text = "O que você quer criar?",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Capturar rápido é metade da constância.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            CreateRow(
                icon = Icons.Filled.FormatListBulleted,
                tint = scheme.primaryContainer,
                iconColor = scheme.primary,
                title = "Nova tarefa",
                subtitle = "Com data, projeto, prioridade e subtarefas",
                tag = "create_task",
                onClick = onCreateTask,
            )
            SheetDivider()
            CreateRow(
                icon = Icons.Filled.Whatshot,
                tint = extra.tintFlame,
                iconColor = scheme.secondary,
                title = "Novo hábito",
                subtitle = "Diário, em dias fixos ou X por semana",
                tag = "create_habit",
                onClick = onCreateHabit,
            )
            SheetDivider()
            CreateRow(
                icon = Icons.Filled.MenuBook,
                tint = extra.tintSun,
                iconColor = extra.sunColor,
                title = "Registro no diário",
                subtitle = "Humor, energia, sono e a pergunta do dia",
                tag = "create_diary",
                onClick = onCreateDiary,
            )
        }
    }
}

@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.constanciaExtra.lineSoft),
    )
}

@Composable
private fun CreateRow(
    icon: ImageVector,
    tint: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
