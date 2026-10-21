package app.constancia.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.BackRow
import app.constancia.ui.common.CtaButton

private data class Benefit(val title: String, val description: String)

private val benefits = listOf(
    Benefit(
        "Relatórios sem limite de tempo",
        "Semana, mês e ano completos — não só os últimos 30 dias.",
    ),
    Benefit(
        "Padrões cruzados com defasagem",
        "Sono de ontem × seu dia de hoje, humor × hábitos da manhã.",
    ),
    Benefit(
        "Raio-X de atrasos",
        "Onde o planejamento fura: projeto, prioridade e horário.",
    ),
    Benefit(
        "Desafios de 21 e 30 dias",
        "Pacotes guiados com começo, meio e fim.",
    ),
    Benefit(
        "Backup na nuvem",
        "Troca de celular sem perder a sequência.",
    ),
    Benefit(
        "Ano em Revista completo",
        "Seu ano em constância, pronto pra compartilhar.",
    ),
)

/**
 * Paywall do protótipo aprovado: backrow, o cartão do plano anual em
 * gradiente roxo (preço, equivalência mensal e o selo de economia), a
 * lista de recursos com checks em tiles suaves, o botão de assinatura e
 * o "Agora não" — com a nota do que continua grátis no rodapé.
 */
@Composable
fun PaywallScreen(onClose: () -> Unit = {}) {
    var purchaseMsg by remember { mutableStateOf<String?>(null) }
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentPadding = PaddingValues(
            start = 20.dp, top = 10.dp, end = 20.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackRow(
                title = "Constância Pro",
                subtitle = "para quem leva o dia a sério",
                onBack = onClose,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // Cartão do plano anual (.pro-card do protótipo).
        item {
            val shape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF4B1D96), Color(0xFF2A0A54)),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(360f, 300f),
                        ),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 50.dp, y = (-60).dp)
                        .size(150.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFC4A6F5).copy(alpha = 0.18f)),
                )
                Column(modifier = Modifier.padding(19.dp)) {
                    Text(
                        text = "PLANO ANUAL",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.12.em,
                        ),
                        color = Color(0xFFC4A6F5),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "R$ 119,90 por ano",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 19.sp,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Equivale a ")
                            withStyle(
                                SpanStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                            ) {
                                append("R$ 9,99/mês")
                            }
                            append(" — menos que um lanche, pra cuidar de " +
                                "todos os seus dias.")
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                        ),
                        color = Color(0xFFDCCFFB),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFFC4A6F5))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "Economize 37%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = Color(0xFF38106E),
                            )
                        }
                        Spacer(Modifier.size(9.dp))
                        Text(
                            text = "R$ 191,88 no mensal",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                            ),
                            color = Color(0xFFA78BDB),
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
            }
        }

        // Recursos do Pro.
        item {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surface)
                    .border(1.dp, extra.lineSoft, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                benefits.forEachIndexed { index, benefit ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(extra.lineSoft),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.size(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                benefit.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Text(
                                benefit.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                CtaButton(
                    text = "Assinar o Pro — R$ 119,90/ano",
                    tag = "paywall_subscribe",
                    onClick = {
                        purchaseMsg =
                            "Assinatura via Google Play/App Store — em breve"
                    },
                )
                if (purchaseMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        purchaseMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClose)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Agora não",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = extra.ink2,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "No plano gratuito continuam: hábitos, tarefas, " +
                        "diário e relatórios dos últimos 30 dias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}
