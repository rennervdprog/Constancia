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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.domain.BillingEvent
import app.constancia.domain.BillingUiState
import app.constancia.domain.StoreProduct
import app.constancia.domain.reduceBilling
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.BackRow
import app.constancia.ui.common.CtaButton
import kotlinx.coroutines.launch

private data class Benefit(val title: String, val description: String)

// Fase F: os benefícios listados são EXATAMENTE as superfícies gateadas
// no código (ProGate) — nada de prometer recurso que não existe (a
// versão anterior anunciava "backup na nuvem", que não existe sem o
// Supabase do Constância; saiu da copy, registrado em FASE-F-NOTES.md).
private val benefits = listOf(
    Benefit(
        "Padrões cruzados com defasagem",
        "Sono de ontem × seu dia de hoje, hábitos da manhã × humor e atrasos.",
    ),
    Benefit(
        "Raio-X de atrasos",
        "Onde o planejamento fura: projeto, prioridade e horário.",
    ),
    Benefit(
        "Ano em Revista completo",
        "O ano inteiro + a revista do trimestre, prontos pra compartilhar.",
    ),
    Benefit(
        "Desafios Pro exclusivos",
        "Pacotes guiados marcados com o selo Pro, de 21 e 30 dias.",
    ),
)

/**
 * Paywall (Fase F): visual do protótipo aprovado (roxo profundo, cartão
 * do plano, CTA branco/roxo), mas com o comércio REAL por trás:
 * - o preço exibido vem do ProductDetails da loja, nunca hardcoded;
 * - seções de venda POR RECURSO Pro (R11.2), com demonstração visual;
 * - comprar abre o fluxo da loja; restaurar reconcilia o entitlement;
 * - loja/produto indisponível (sideload, Console não configurado, iOS
 *   sem StoreKit) = estado honesto, sem preço inventado nem compra falsa.
 * A máquina de estados é o redutor puro `reduceBilling` (testado).
 */
@Composable
fun PaywallScreen(deps: AppDeps, onClose: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val scope = rememberCoroutineScope()
    val isPro by deps.entitlement.isProFlow.collectAsState(initial = false)
    var state by remember { mutableStateOf<BillingUiState>(BillingUiState.Loading) }

    // Carrega o produto da loja uma vez (preço/trial são DA LOJA).
    LaunchedEffect(Unit) {
        if (!deps.billing.storeAvailable()) {
            state = reduceBilling(
                state,
                BillingEvent.StoreUnavailable(
                    "A assinatura do Constância Pro chega em breve a " +
                        "este aparelho. Por aqui você já vê tudo o que " +
                        "ele inclui — sem cobrança nenhuma por enquanto.",
                ),
            )
        } else {
            val product = deps.billing.queryProduct()
            state = if (product != null && product.formattedPrice.isNotBlank()) {
                reduceBilling(state, BillingEvent.ProductLoaded(product))
            } else {
                reduceBilling(state, BillingEvent.ProductNotConfigured)
            }
        }
    }
    // Compra/restauração concluídas fora daqui acendem o entitlement:
    // a tela vira "Purchased" sozinha.
    LaunchedEffect(isPro) {
        if (isPro) state = reduceBilling(state, BillingEvent.PurchaseCompleted)
    }

    val product: StoreProduct? = when (val s = state) {
        is BillingUiState.Ready -> s.product
        is BillingUiState.Purchasing -> s.product
        is BillingUiState.Failed -> s.product
        else -> null
    }

    fun buy() {
        state = reduceBilling(state, BillingEvent.BuyClicked)
        val purchasing = state as? BillingUiState.Purchasing ?: return
        if (!deps.billing.launchPurchase(purchasing.product)) {
            state = reduceBilling(
                state,
                BillingEvent.PurchaseError(
                    "Não foi possível abrir a loja agora. Confira a " +
                        "conexão e tente de novo.",
                ),
            )
        }
    }

    fun restore() {
        scope.launch {
            val pro = deps.entitlement.refreshFromStore()
            state = if (pro) {
                reduceBilling(state, BillingEvent.RestoreFoundPro)
            } else {
                reduceBilling(
                    state,
                    BillingEvent.RestoreFinished(
                        "Nenhuma assinatura ativa encontrada nesta conta.",
                    ),
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("paywall_list"),
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
                        text = if (product != null) {
                            "${product.formattedPrice} por ano"
                        } else {
                            "Um ano de Pro"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 19.sp,
                        ),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = when {
                            product?.trialDays != null ->
                                "Comece com ${product.trialDays} dias grátis. " +
                                    "Se não cancelar, a assinatura anual " +
                                    "começa automaticamente — cancele " +
                                    "quando quiser, sem multa."
                            product != null ->
                                "Renova todo ano pelo preço acima; cancele " +
                                    "quando quiser na loja, sem multa."
                            else ->
                                "O preço aparece aqui assim que a loja " +
                                    "responder — nada é cobrado sem a sua " +
                                    "confirmação na loja."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                        ),
                        color = Color(0xFFDCCFFB),
                    )
                    if (product?.trialDays != null) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFFC4A6F5))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "${product.trialDays} dias grátis",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = Color(0xFF38106E),
                            )
                        }
                    }
                }
            }
        }

        // R11.2: venda por recurso — demonstração visual de cada
        // superfície Pro, em páginas deslizáveis.
        item { FeatureShowcase() }

        // Recursos do Pro (lista com checks, como no protótipo).
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

        // CTA + estados + restaurar + rodapé honesto.
        item {
            Column {
                when (val s = state) {
                    BillingUiState.Purchased -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "Você é Pro — tudo desbloqueado.",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        CtaButton(
                            text = "Começar a usar",
                            tag = "paywall_done",
                            onClick = onClose,
                        )
                    }
                    BillingUiState.Loading -> {
                        CtaButton(
                            text = "Carregando o preço da loja…",
                            tag = "paywall_subscribe",
                            onClick = {},
                        )
                    }
                    is BillingUiState.Ready -> {
                        CtaButton(
                            text = if (s.product.trialDays != null) {
                                "Começar ${s.product.trialDays} dias grátis"
                            } else {
                                "Assinar o Pro — ${s.product.formattedPrice}/ano"
                            },
                            tag = "paywall_subscribe",
                            onClick = { buy() },
                        )
                    }
                    is BillingUiState.Purchasing -> {
                        CtaButton(
                            text = "Abrindo a loja…",
                            tag = "paywall_subscribe",
                            onClick = {},
                        )
                    }
                    is BillingUiState.Failed -> {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (s.product != null) {
                            Spacer(Modifier.height(10.dp))
                            CtaButton(
                                text = "Tentar de novo",
                                tag = "paywall_subscribe",
                                onClick = { buy() },
                            )
                        }
                    }
                    is BillingUiState.Unavailable -> {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 8.dp),
                        )
                    }
                }

                if (state != BillingUiState.Purchased &&
                    deps.billing.storeAvailable()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = { restore() })
                            .testTag("paywall_restore")
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = extra.ink2,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "Restaurar compra",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = extra.ink2,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
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
                    text = "No plano gratuito continua tudo: hábitos, " +
                        "tarefas, diário e os relatórios básicos — sem " +
                        "limite de itens, para sempre. O Pro destrava " +
                        "as análises avançadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                if (product != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Assinatura de renovação automática, " +
                            "gerenciada pela loja da sua conta. O teste " +
                            "grátis vira assinatura paga ao final do " +
                            "período se não for cancelado antes.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                        ),
                        color = extra.ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// R11.2 — páginas de venda por recurso Pro (demonstração ilustrativa).
// ---------------------------------------------------------------------------

private data class FeaturePage(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val featurePages = listOf(
    FeaturePage(
        Icons.Filled.Bedtime,
        "Padrões cruzados",
        "Durma mal numa noite e o dia seguinte paga a conta? O " +
            "Constância cruza sono, hábitos da manhã e carga de tarefas " +
            "com o seu humor e os seus atrasos — com a amostra sempre " +
            "visível, sem prometer causalidade.",
    ),
    FeaturePage(
        Icons.Filled.Schedule,
        "Raio-X de atrasos",
        "Atrasos por projeto, prioridade e parte do dia, a idade de " +
            "cada atraso e as recorrentes que sempre furam — para " +
            "consertar o planejamento, não a força de vontade.",
    ),
    FeaturePage(
        Icons.Filled.EmojiEvents,
        "Desafios Pro + Ano em Revista",
        "Pacotes guiados com selo Pro e a revista completa do ano e " +
            "do trimestre: check-ins, tarefas e sua maior sequência, " +
            "prontos pra compartilhar.",
    ),
)

@Composable
private fun FeatureShowcase() {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val pagerState = rememberPagerState(pageCount = { featurePages.size })
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surface)
            .border(1.dp, extra.lineSoft, RoundedCornerShape(24.dp))
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = "EXPLORE O PRO",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.12.em,
            ),
            color = scheme.primary,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(10.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val feature = featurePages[page]
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            feature.icon,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.size(11.dp))
                    Text(
                        feature.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    feature.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                    ),
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                DemoVisual(page = page)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            featurePages.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (pagerState.currentPage == index) {
                                scheme.primary
                            } else {
                                extra.lineSoft
                            },
                        ),
                )
            }
        }
    }
}

/** Mini-visual de demonstração de cada recurso (ilustrativo, sem dados). */
@Composable
private fun DemoVisual(page: Int) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    when (page) {
        0 -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DemoBar(label = "noites de 7h+", fraction = 0.82f, value = "82% dos dias em dia")
            DemoBar(label = "noites curtas", fraction = 0.41f, value = "41% dos dias em dia")
        }
        1 -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DemoBar(label = "noite", fraction = 0.54f, value = "54% dos atrasos")
            DemoBar(label = "tarde", fraction = 0.31f, value = "31% dos atrasos")
            DemoBar(label = "manhã", fraction = 0.15f, value = "15% dos atrasos")
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(7) { index ->
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (index < 5) scheme.primary
                            else extra.pageChip,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (index < 5) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = "dia 5 de 30 — demonstração",
                style = MaterialTheme.typography.bodySmall,
                color = extra.ink3,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun DemoBar(label: String, fraction: Float, value: String) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = extra.ink3,
            modifier = Modifier.size(width = 86.dp, height = 16.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(extra.pageChip),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(scheme.primary),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = scheme.onSurface,
        )
    }
}
