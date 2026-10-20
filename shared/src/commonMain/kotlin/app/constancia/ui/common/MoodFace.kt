package app.constancia.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import app.constancia.domain.MOOD_COLORS_HEX

/**
 * Rostinho do humor (1..5), desenhado como no protótipo: círculo em traço
 * na cor do humor, dois olhos cheios e a boca variando de triste a sorrindo.
 * Coordenadas no espaço 24x24 do SVG original.
 */
@Composable
fun MoodFace(mood: Int, size: Dp, modifier: Modifier = Modifier) {
    val level = mood.coerceIn(1, 5)
    val color = Color(MOOD_COLORS_HEX[level - 1])
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 24f
        fun x(v: Float) = v * s
        fun y(v: Float) = v * s
        // Círculo do rosto (traço 1.9).
        drawCircle(
            color = color,
            radius = x(9f),
            center = Offset(x(12f), y(12f)),
            style = Stroke(width = 1.9f * s),
        )
        // Olhos.
        drawCircle(color = color, radius = x(1.05f), center = Offset(x(9f), y(10f)))
        drawCircle(color = color, radius = x(1.05f), center = Offset(x(15f), y(10f)))
        // Boca por nível de humor (paths do protótipo).
        val mouth = Path()
        when (level) {
            1 -> { // Péssimo: arco para baixo bem marcado
                mouth.moveTo(x(8.5f), y(16.2f))
                mouth.cubicTo(x(9.6f), y(14.7f), x(10.8f), y(14f), x(12f), y(14f))
                mouth.cubicTo(x(13.2f), y(14f), x(14.4f), y(14.7f), x(15.5f), y(16.2f))
            }
            2 -> { // Ruim: arco para baixo suave
                mouth.moveTo(x(8.8f), y(15.8f))
                mouth.cubicTo(x(9.8f), y(14.7f), x(10.8f), y(14.2f), x(12f), y(14.2f))
                mouth.cubicTo(x(13.2f), y(14.2f), x(14.2f), y(14.7f), x(15.2f), y(15.8f))
            }
            3 -> { // Ok: linha reta
                mouth.moveTo(x(8.5f), y(15f))
                mouth.lineTo(x(15.5f), y(15f))
            }
            4 -> { // Bom: sorriso suave
                mouth.moveTo(x(8.8f), y(13.8f))
                mouth.cubicTo(x(9.8f), y(14.9f), x(10.8f), y(15.4f), x(12f), y(15.4f))
                mouth.cubicTo(x(13.2f), y(15.4f), x(14.2f), y(14.9f), x(15.2f), y(13.8f))
            }
            else -> { // Ótimo: sorrisão
                mouth.moveTo(x(8.5f), y(13.5f))
                mouth.cubicTo(x(9.6f), y(15.2f), x(10.8f), y(16f), x(12f), y(16f))
                mouth.cubicTo(x(13.2f), y(16f), x(14.4f), y(15.2f), x(15.5f), y(13.5f))
            }
        }
        drawPath(mouth, color = color, style = Stroke(width = 1.9f * s))
    }
}
