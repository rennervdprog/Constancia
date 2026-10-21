package app.constancia.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.constancia.model.normalizeHabitColor

// Catálogo de ícones disponíveis na criação/edição de hábitos.
// A chave é o que fica salvo em Habit.icon.
val HABIT_ICON_KEYS = listOf(
    "water", "run", "book", "meditate", "sleep", "gym",
    "heart", "study", "work", "food", "music", "check",
)

// Resolve a chave salva para o ImageVector correspondente.
// Chave desconhecida cai no Check para nunca quebrar a UI.
fun habitIcon(key: String): ImageVector = when (key) {
    "water" -> Icons.Filled.WaterDrop
    "run" -> Icons.Filled.DirectionsRun
    "book" -> Icons.Filled.MenuBook
    "meditate" -> Icons.Filled.SelfImprovement
    "sleep" -> Icons.Filled.Bedtime
    "gym" -> Icons.Filled.FitnessCenter
    "heart" -> Icons.Filled.Favorite
    "study" -> Icons.Filled.School
    "work" -> Icons.Filled.Work
    "food" -> Icons.Filled.Restaurant
    "music" -> Icons.Filled.MusicNote
    else -> Icons.Filled.Check
}

/**
 * Cor renderizável de um hábito a partir do Long gravado. Passa pela
 * normalização do modelo (linhas antigas guardaram o ULong empacotado
 * do Compose, que renderizava transparente) e usa `Color(Int)` — o
 * ARGB clássico, sem ambiguidade de empacotamento.
 */
fun habitColorOf(stored: Long): Color =
    Color(normalizeHabitColor(stored).toInt())

/** ARGB Long canônico (0xFF0B7A55) de uma cor — o formato de Habit.color. */
fun argbLongOf(color: Color): Long = (color.value shr 32).toLong()
