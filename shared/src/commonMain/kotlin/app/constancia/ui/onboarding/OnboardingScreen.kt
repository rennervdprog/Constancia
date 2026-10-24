package app.constancia.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.CtaButton

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        icon = Icons.Filled.WbSunny,
        title = "Bem-vindo ao Constância",
        subtitle = "Constância se constrói um dia de cada vez.",
        bullets = listOf(
            "Hábitos, tarefas e diário num só lugar",
            "Um dia de cada vez, sem pressa e sem pontos",
            "Tudo salvo no aparelho, mesmo sem internet",
        ),
    ),
    OnboardingPage(
        icon = Icons.Filled.Whatshot,
        title = "Hábitos que viram rotina",
        subtitle = "Pequenos passos, todos os dias.",
        bullets = listOf(
            "Check-in simples, em segundos",
            "Sequência e recorde à vista, com dias de folga",
            "Pular um dia não quebra a sequência",
        ),
    ),
    OnboardingPage(
        icon = Icons.Filled.Schedule,
        title = "Tarefas e diário juntos",
        subtitle = "O que fazer e como foi o dia, lado a lado.",
        bullets = listOf(
            "Tarefas com prazo, recorrência e lembretes",
            "Diário com humor, sono e nota do dia",
            "Revisão do dia para fechar com calma",
        ),
    ),
)

/**
 * Regra de exibição das telas iniciais, testada em unidade:
 * aparece quando o usuário ainda não concluiu o fluxo, ou quando ele
 * pede para rever a partir da aba Mais. Enquanto o valor ainda não
 * foi lido (null), não aparece — evita um flash na abertura de quem
 * já concluiu. Não cria nenhum hábito, tarefa ou registro de diário.
 */
fun shouldShowOnboarding(onboardingDone: Boolean?, revisit: Boolean): Boolean {
    return onboardingDone == false || revisit
}

/**
 * Telas iniciais do Constância: 3 telas, sem paywall e sem dados de
 * exemplo. A primeira abertura mostra o fluxo uma única vez; depois
 * ele só volta por "Telas iniciais" na aba Mais. "Pular" e "Começar"
 * têm o mesmo efeito: marcam o fluxo como concluído e vão para o app.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    var page by remember { mutableStateOf(0) }
    val current = ONBOARDING_PAGES[page]
    val isLast = page == ONBOARDING_PAGES.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("onboarding_screen")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .testTag("onboarding_skip")
                    .clickable(onClick = onFinish)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Pular",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = scheme.primary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4B1D96),
                            Color(0xFF38106E),
                            Color(0xFF1E0740),
                        ),
                    ),
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.20f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        current.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 23.sp,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = current.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color(0xFFDCCFFB),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surface)
                .border(1.dp, extra.lineSoft, RoundedCornerShape(24.dp))
                .padding(16.dp),
        ) {
            current.bullets.forEach { bullet ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                        ),
                        color = scheme.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ONBOARDING_PAGES.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .testTag("onboarding_dot_" + index)
                        .clickable { page = index }
                        .padding(4.dp)
                        .size(if (index == page) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == page) {
                                scheme.primary
                            } else {
                                scheme.outline
                            },
                        ),
                )
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = (page + 1).toString() + " de " +
                    ONBOARDING_PAGES.size.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (isLast) {
            CtaButton(
                text = "Começar",
                onClick = onFinish,
                tag = "onboarding_finish",
            )
        } else {
            CtaButton(
                text = "Continuar",
                onClick = { page += 1 },
                tag = "onboarding_next",
            )
        }
        if (page > 0) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_back")
                    .clickable { page -= 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Voltar",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
