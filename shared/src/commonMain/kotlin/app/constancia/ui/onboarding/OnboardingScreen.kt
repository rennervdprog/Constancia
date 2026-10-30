package app.constancia.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.CtaButton
import app.constancia.ui.common.MoodFace

private data class OnboardingPage(
    val kicker: String,
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        kicker = "BEM-VINDO",
        title = "Tudo do seu dia, num só lugar",
        subtitle = "Constância se constrói um dia de cada vez.",
        bullets = listOf(
            "Sem cadastro: começa em segundos",
            "Um dia de cada vez, sem pressa e sem pontos",
            "Tudo salvo no aparelho, mesmo sem internet",
        ),
    ),
    OnboardingPage(
        kicker = "HÁBITOS",
        title = "Hábitos que viram rotina",
        subtitle = "Pequenos passos, todos os dias.",
        bullets = listOf(
            "Check-in simples, em segundos",
            "Sequência e recorde à vista, com dias de folga",
            "Pular um dia não quebra a sequência",
        ),
    ),
    OnboardingPage(
        kicker = "TAREFAS E DIÁRIO",
        title = "Planeje e reflita, juntos",
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
 * Telas iniciais do Constância (layout profissional, 2026-10-30):
 * gradiente roxo profundo em tela cheia com progresso segmentado e
 * "Pular" em vidro no topo; no centro, uma CENA do produto (cartões de
 * prévia desenhados na hora, só ilustração — nenhum dado é criado);
 * embaixo, folha clara de cantos 32dp com selo, título Sora forte,
 * benefícios em tiles e a área fixa de ação (indicador + botão).
 * 3 telas, sem paywall. A primeira abertura mostra o fluxo uma única
 * vez; depois ele só volta por "Telas iniciais" na aba Mais.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(0) }
    val current = ONBOARDING_PAGES[page]
    val isLast = page == ONBOARDING_PAGES.lastIndex

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4B1D96),
                        Color(0xFF38106E),
                        Color(0xFF1E0740),
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("onboarding_screen"),
    ) {
        // Tela baixa (ex.: 640dp de altura): a cena encolhe e os
        // cartões de prévia são escalados, para os 3 benefícios da
        // folha caberem sem rolagem. Em telas altas, cena cheia.
        val compact = maxHeight < 700.dp
        val sceneHeight = if (compact) 176.dp else 252.dp
        val sceneScale = if (compact) 0.78f else 1f
        Column(modifier = Modifier.fillMaxSize()) {
        // Topo sobre o gradiente: progresso segmentado + Pular em vidro.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ONBOARDING_PAGES.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (index <= page) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.25f)
                            },
                        ),
                )
                if (index < ONBOARDING_PAGES.lastIndex) {
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.20f),
                        RoundedCornerShape(99.dp),
                    )
                    .testTag("onboarding_skip")
                    .clickable(onClick = onFinish)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Pular",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = Color.White,
                )
            }
        }

        // Cena do produto (prévia ilustrada, muda a cada tela).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sceneHeight)
                .clip(RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Anéis decorativos translúcidos do painel-herói aprovado.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = (-70).dp)
                    .size(190.dp)
                    .border(
                        26.dp,
                        Color.White.copy(alpha = 0.05f),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-50).dp, y = 60.dp)
                    .size(140.dp)
                    .border(
                        20.dp,
                        Color.White.copy(alpha = 0.04f),
                        CircleShape,
                    ),
            )
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = sceneScale
                    scaleY = sceneScale
                },
            ) {
                when (page) {
                    0 -> SceneDayCard()
                    1 -> SceneHabitCard()
                    else -> SceneTasksDiaryCards()
                }
            }
        }

        // Folha clara com o texto e a ação.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp),
            ) {
                Text(
                    text = current.kicker,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.12.em,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = current.subtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                current.bullets.forEachIndexed { index, bullet ->
                    BenefitRow(index = index, text = bullet)
                    if (index < current.bullets.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(
                modifier = Modifier.padding(
                    start = 24.dp, end = 24.dp, bottom = 18.dp,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ONBOARDING_PAGES.forEachIndexed { index, _ ->
                        val selected = index == page
                        Box(
                            modifier = Modifier
                                .testTag("onboarding_dot_" + index)
                                .clickable { page = index }
                                .padding(4.dp)
                                .then(
                                    if (selected) {
                                        Modifier
                                            .width(24.dp)
                                            .height(8.dp)
                                    } else {
                                        Modifier.size(8.dp)
                                    },
                                )
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = (page + 1).toString() + " de " +
                            ONBOARDING_PAGES.size.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (isLast) {
                    CtaButton(
                        text = "Começar",
                        onClick = onFinish,
                        tag = "onboarding_finish",
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Sem cadastro. Seus dados ficam no aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CtaButton(
                        text = "Continuar",
                        onClick = { page += 1 },
                        tag = "onboarding_next",
                    )
                }
                if (page > 0) {
                    Spacer(Modifier.height(6.dp))
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        }
    }
}

/** Linha de benefício: tile 34dp raio 11 em cor suave (ciclo de 3
 *  cores do tema) com o check sólido e o texto em 14,5/700. */
@Composable
private fun BenefitRow(index: Int, text: String) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val tints = listOf(
        extra.tintAmber to extra.amberStrong,
        extra.tintFlame to scheme.secondary,
        extra.tintAqua to extra.aquaColor,
    )
    val (tint, solid) = tints[index % tints.size]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = solid,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp),
            color = scheme.onSurface,
        )
    }
}

/** Cartão branco de prévia usado nas cenas (mockup claro nos dois
 *  temas, como o painel-herói: sombra roxa e raio 24). */
@Composable
private fun PreviewCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(272.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0xFF1E0740).copy(alpha = 0.45f),
                ambientColor = Color(0xFF1E0740).copy(alpha = 0.45f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(16.dp),
        content = content,
    )
}

private val PREVIEW_INK = Color(0xFF221238)
private val PREVIEW_MUTED = Color(0xFF7A6B90)

/** Selo de sequência em chama: fundo #FDEBDC, chama + "N dias". */
@Composable
private fun StreakChip(days: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0xFFFDEBDC))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Whatshot,
            contentDescription = null,
            tint = Color(0xFFE86A17),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = days.toString() + " dias",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp),
            color = Color(0xFFE86A17),
        )
    }
}

/** Cena 1: miniatura do cartão "Seu dia" (anel + breakdown). */
@Composable
private fun SceneDayCard() {
    PreviewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SEU DIA",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.12.em,
                ),
                color = Color(0xFF8A5FC0),
            )
            Spacer(Modifier.weight(1f))
            StreakChip(days = 8)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(76.dp)) {
                    drawCircle(
                        color = Color(0xFFEAE2F4),
                        style = Stroke(width = 8.dp.toPx()),
                    )
                    drawArc(
                        color = Color(0xFF4B1D96),
                        startAngle = -90f,
                        sweepAngle = 360f * 0.67f,
                        useCenter = false,
                        style = Stroke(
                            width = 8.dp.toPx(),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "67%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 17.sp,
                        ),
                        color = PREVIEW_INK,
                    )
                    Text(
                        text = "DO DIA",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = PREVIEW_MUTED,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                PreviewLine(label = "Hábitos", value = "2 de 4")
                Spacer(Modifier.height(7.dp))
                PreviewLine(label = "Tarefas", value = "1 de 3")
                Spacer(Modifier.height(7.dp))
                PreviewLine(label = "Diário", value = "por fazer")
            }
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = PREVIEW_MUTED,
            modifier = Modifier.width(52.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            color = PREVIEW_INK,
        )
    }
}

/** Cena 2: cartão de hábito com meta, sequência e semana de checks. */
@Composable
private fun SceneHabitCard() {
    PreviewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFFE0F2F4)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = Color(0xFF0E7C86),
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ler 10 páginas",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.5.sp,
                    ),
                    color = PREVIEW_INK,
                )
                Text(
                    text = "Todo dia · noite",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                    ),
                    color = PREVIEW_MUTED,
                )
            }
            CheckCircle(filled = true)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakChip(days = 12)
            Spacer(Modifier.weight(1f))
            Text(
                text = "meta da semana · 5 de 7",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = PREVIEW_MUTED,
            )
        }
        Spacer(Modifier.height(8.dp))
        // Barra da meta na cor do hábito.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xFFEAE2F4)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.71f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF0E7C86)),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Semana: quadradinhos dos últimos 7 dias, hoje com contorno.
        Row {
            (0..6).forEach { i ->
                val done = i < 5
                val isToday = i == 6
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (done) {
                                Color(0xFF0E7C86)
                            } else {
                                Color(0xFFE7DEF5)
                            },
                        )
                        .then(
                            if (isToday) {
                                Modifier.border(
                                    2.dp, PREVIEW_INK, RoundedCornerShape(6.dp),
                                )
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                if (i < 6) Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "semana",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = PREVIEW_MUTED,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** Círculo de check 30dp (cheio no primário ou vazio com borda). */
@Composable
private fun CheckCircle(filled: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(Color(0xFF4B1D96))
                } else {
                    Modifier.border(2.dp, Color(0xFFC9BCE0), CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Cena 3: tarefas do dia + a pergunta de humor do diário. */
@Composable
private fun SceneTasksDiaryCards() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PreviewCard {
            SceneTaskRow(title = "Enviar proposta", meta = "Hoje · Trabalho", done = true)
            Spacer(Modifier.height(10.dp))
            SceneTaskRow(title = "Comprar presente", meta = "Amanhã · Pessoal", done = false)
        }
        Spacer(Modifier.height(10.dp))
        PreviewCard {
            Text(
                text = "Como foi o seu dia?",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp),
                color = PREVIEW_INK,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                (1..5).forEach { mood ->
                    MoodFace(mood = mood, size = 30.dp)
                    if (mood < 5) Spacer(Modifier.width(9.dp))
                }
            }
        }
    }
}

@Composable
private fun SceneTaskRow(title: String, meta: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(filled = done)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = if (done) PREVIEW_MUTED else PREVIEW_INK,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = PREVIEW_MUTED,
            )
        }
    }
}
