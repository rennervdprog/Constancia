package app.constancia.domain

import app.constancia.model.ChallengeStatus

// ---------------------------------------------------------------------------
// Fase F (Comercial) — camada de entitlement e gating, LÓGICA PURA.
//
// Decisão comercial aprovada pelo Renner (2026-10-22), NÃO reabrir:
// - Grátis GENEROSO: sem limites quantitativos de hábitos/tarefas/vistas.
//   Nada do que o usuário já usa trava, congela ou some. (Substitui os
//   "plan_limits" + freeze do plano original.)
// - Pro = superfícies avançadas: padrões cruzados, raio-X de atrasos,
//   insights avançados, desafios marcados Pro e a revista completa.
// - Downgrade: as superfícies Pro voltam ao cartão de cadeado (teaser);
//   nenhum dado é apagado nem escondido; re-assinar destrava na hora.
// ---------------------------------------------------------------------------

/** Identificadores do produto na Google Play — DEVEM ser idênticos aos
 *  criados no Play Console (ver FASE-F-NOTES.md, checklist do Renner). */
object ConstanciaBilling {
    /** ID do produto de assinatura. */
    const val PRODUCT_ID = "constancia_pro"

    /** ID do plano-base mensal (R$ 19,90/mês, SEM trial). */
    const val MONTHLY_BASE_PLAN_ID = "mensal"

    /** ID do plano-base trimestral (R$ 44,90/3 meses, SEM trial). */
    const val QUARTERLY_BASE_PLAN_ID = "trimestral"

    /** ID do plano-base anual (R$ 119,90/ano, COM trial). */
    const val ANNUAL_BASE_PLAN_ID = "anual"

    /** ID da oferta com teste grátis (SÓ dentro do plano-base anual). */
    const val TRIAL_OFFER_ID = "trial-7-dias"

    /** Dias de teste grátis anunciados (a fonte exibida é a loja). */
    const val TRIAL_DAYS = 7

    /** Ordem de exibição dos planos no paywall: do compromisso menor
     *  ao maior (o anual, melhor valor, fecha a lista e vem
     *  pré-selecionado). */
    val PLAN_DISPLAY_ORDER: List<String> = listOf(
        MONTHLY_BASE_PLAN_ID,
        QUARTERLY_BASE_PLAN_ID,
        ANNUAL_BASE_PLAN_ID,
    )
}

/** Superfícies que o Pro destrava. O básico (resumo/KPIs, evolução,
 *  heatmap, desempenho por hábito, padrão por dia da semana, humor,
 *  revisão do período, insights básicos, Ano em Revista resumido)
 *  permanece grátis para todos — sem gate aqui de propósito. */
enum class ProFeature {
    /** ReportsScreen: PatternsSection (sono × hoje, manhã × dia). */
    CROSS_PATTERNS,

    /** ReportsScreen: XraySection (atrasos por projeto/prioridade/horário). */
    OVERDUE_XRAY,

    /** ReportsScreen: Ano em Revista completo + revista do trimestre. */
    ADVANCED_REVIEW,

    /** ChallengesScreen: desafios com selo Pro (campo is_pro do pacote). */
    PRO_CHALLENGE,
}

/** Regra única de gating: toda tela pergunta aqui, nunca compara flag
 *  solta. Funções puras — a matriz inteira é testada em EntitlementTest. */
object ProGate {

    /** Uma superfície Pro está aberta sse o entitlement está ativo. */
    fun isUnlocked(feature: ProFeature, isPro: Boolean): Boolean = isPro

    /**
     * Desafio: o gate vale só para COMEÇAR um pacote Pro. Quem já
     * começou (ACTIVE/DONE/GIVEN_UP) continua usando mesmo após o
     * downgrade — nada do que o usuário já usa trava (decisão aprovada).
     * Desafio sem selo Pro é sempre livre.
     */
    fun canStartChallenge(
        challengeIsPro: Boolean,
        status: ChallengeStatus,
        isPro: Boolean,
    ): Boolean = !challengeIsPro || isPro || status != ChallengeStatus.AVAILABLE
}

// ---------------------------------------------------------------------------
// Modelos da loja (neutros de plataforma: o gateway de cada plataforma
// traduz ProductDetails/Purchase da loja para estes tipos).
// ---------------------------------------------------------------------------

/** Período de cobrança de um plano-base, com os textos de exibição.
 *  [priceSuffix] acompanha o preço cheio ("R$ 44,90/3 meses");
 *  [renewalLabel] vai em "Renova {renewalLabel} por …". */
enum class PlanPeriod(
    val months: Int,
    val displayName: String,
    val priceSuffix: String,
    val renewalLabel: String,
) {
    MONTHLY(1, "Mensal", "/mês", "todo mês"),
    QUARTERLY(3, "Trimestral", "/3 meses", "a cada 3 meses"),
    ANNUAL(12, "Anual", "/ano", "todo ano"),
}

/**
 * Oferta da loja já traduzida para tipos neutros pelo gateway da
 * plataforma (no Android: um `SubscriptionOfferDetails`). O paywall
 * nunca vê tipos da loja — quem escolhe QUAL oferta representa cada
 * plano-base é [PlanCatalog], lógica pura e testada.
 */
data class StoreOffer(
    val basePlanId: String,
    /** null = oferta padrão do plano-base (sem oferta promocional). */
    val offerId: String?,
    /** Preço formatado DA LOJA da fase paga (ex.: "R$ 119,90"). */
    val formattedPrice: String,
    /** Preço da fase paga em micros (1/1.000.000 da moeda). */
    val priceAmountMicros: Long,
    val currencyCode: String,
    /** Período ISO da fase paga: "P1M", "P3M", "P1Y". */
    val billingPeriod: String,
    /** Equivalente mensal já formatado pela plataforma (ex.: "R$ 9,99"). */
    val formattedMonthlyEquivalent: String,
    /** Dias de teste grátis da oferta; null = sem fase grátis. */
    val trialDays: Int?,
    /** Token opaco da loja para abrir a compra DESTA oferta. */
    val offerToken: String,
)

/**
 * Um plano-base vendável do produto, pronto para a UI. Todos os textos
 * de preço vêm DA LOJA: [formattedPrice] é o preço do período e
 * [formattedMonthlyEquivalent] é o equivalente mensal formatado pela
 * plataforma a partir do preço real — nenhum número é digitado à mão.
 */
data class StorePlan(
    val basePlanId: String,
    val period: PlanPeriod,
    /** Oferta escolhida para a compra; null = oferta padrão do plano. */
    val offerId: String?,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val formattedMonthlyEquivalent: String,
    /** Teste grátis em dias — SÓ o anual tem (regra do PlanCatalog). */
    val trialDays: Int?,
) {
    /** Preço do período com sufixo: "R$ 119,90/ano". */
    fun priceWithSuffix(): String = formattedPrice + period.priceSuffix

    /** Equivalente mensal com sufixo: "≈ R$ 9,99/mês". */
    fun monthlyEquivalentLabel(): String = "≈ $formattedMonthlyEquivalent/mês"
}

/** Produto de assinatura como a loja o descreve: os planos-base
 *  vendáveis, já resolvidos por [PlanCatalog]. */
data class StoreProduct(
    val productId: String,
    val plans: List<StorePlan>,
) {
    fun plan(basePlanId: String): StorePlan? =
        plans.firstOrNull { it.basePlanId == basePlanId }

    fun monthlyPlan(): StorePlan? = plan(ConstanciaBilling.MONTHLY_BASE_PLAN_ID)

    /** Plano em destaque (melhor valor): o anual; sem anual, o
     *  primeiro plano na ordem de exibição. */
    fun featuredPlan(): StorePlan? =
        plan(ConstanciaBilling.ANNUAL_BASE_PLAN_ID) ?: plans.firstOrNull()

    /** Plano do preço de chamada (ex.: cartão da aba Mais): o mensal,
     *  que é a porta de entrada; sem mensal, o plano em destaque. */
    fun headlinePlan(): StorePlan? = monthlyPlan() ?: featuredPlan()
}

/** Meses de um período ISO de cobrança ("P1Y2M" → 14; "P3M" → 3;
 *  semanas/dias não contam — fase paga de assinatura é M/ano). */
fun monthsOfBillingPeriod(period: String): Int {
    var months = 0
    Regex("(\\d+)Y").find(period)?.let {
        months += (it.groupValues[1].toIntOrNull() ?: 0) * 12
    }
    Regex("(\\d+)M").find(period)?.let {
        months += it.groupValues[1].toIntOrNull() ?: 0
    }
    return months
}

/** Período de um plano: pelo período de cobrança da loja; se ele não
 *  disser (0 meses), pelo ID do plano-base conhecido. */
fun periodFor(basePlanId: String, billingPeriod: String): PlanPeriod =
    when (monthsOfBillingPeriod(billingPeriod)) {
        1 -> PlanPeriod.MONTHLY
        3 -> PlanPeriod.QUARTERLY
        in 12..Int.MAX_VALUE -> PlanPeriod.ANNUAL
        else -> when (basePlanId) {
            ConstanciaBilling.MONTHLY_BASE_PLAN_ID -> PlanPeriod.MONTHLY
            ConstanciaBilling.QUARTERLY_BASE_PLAN_ID -> PlanPeriod.QUARTERLY
            else -> PlanPeriod.ANNUAL
        }
    }

/**
 * Mapeamento plano → oferta (lógica pura, testada): para cada
 * plano-base conhecido presente nas ofertas da loja, escolhe a oferta
 * que o representa na vitrine e na compra.
 *
 * - **Anual**: a oferta `trial-7-dias` (com fase grátis de verdade),
 *   se existir; senão a oferta padrão do plano-base.
 * - **Mensal/Trimestral**: a oferta padrão do plano-base (offerId
 *   null) — SEM trial, por decisão de preço aprovada.
 *
 * O trial só sobrevive no anual: mesmo que outra oferta traga fase
 * grátis, o plano resolvido sai com `trialDays = null` fora do anual.
 * Retorna mapa ordenado pela ordem de exibição do paywall.
 */
object PlanCatalog {

    fun chooseOffers(offers: List<StoreOffer>): Map<String, StoreOffer> {
        val byBasePlan = offers.groupBy { it.basePlanId }
        val chosen = LinkedHashMap<String, StoreOffer>()
        ConstanciaBilling.PLAN_DISPLAY_ORDER.forEach { basePlanId ->
            val candidates = byBasePlan[basePlanId] ?: return@forEach
            chosen[basePlanId] = chooseOffer(basePlanId, candidates)
        }
        return chosen
    }

    private fun chooseOffer(
        basePlanId: String,
        candidates: List<StoreOffer>,
    ): StoreOffer {
        if (basePlanId == ConstanciaBilling.ANNUAL_BASE_PLAN_ID) {
            candidates.firstOrNull {
                it.offerId == ConstanciaBilling.TRIAL_OFFER_ID &&
                    (it.trialDays ?: 0) > 0
            }?.let { return it }
        }
        return candidates.firstOrNull { it.offerId == null }
            ?: candidates.first()
    }

    fun resolvePlans(offers: List<StoreOffer>): List<StorePlan> =
        chooseOffers(offers).map { (basePlanId, offer) ->
            StorePlan(
                basePlanId = basePlanId,
                period = periodFor(basePlanId, offer.billingPeriod),
                offerId = offer.offerId,
                formattedPrice = offer.formattedPrice,
                priceAmountMicros = offer.priceAmountMicros,
                currencyCode = offer.currencyCode,
                formattedMonthlyEquivalent = offer.formattedMonthlyEquivalent,
                trialDays = if (
                    basePlanId == ConstanciaBilling.ANNUAL_BASE_PLAN_ID
                ) {
                    offer.trialDays
                } else {
                    null
                },
            )
        }
}

/** Equivalente mensal em micros: preço do período ÷ meses do período. */
fun StorePlan.monthlyEquivalentMicros(): Long =
    if (period.months > 0) {
        priceAmountMicros / period.months
    } else {
        priceAmountMicros
    }

/**
 * Economia percentual DESTE plano contra a taxa mensal do plano
 * [monthly] — calculada SÓ dos preços reais (micros) que a loja
 * retornou. null = não há comparação honesta (é o próprio mensal,
 * preço ausente ou este plano não é mais barato por mês).
 */
fun savingsPercentVsMonthly(plan: StorePlan, monthly: StorePlan): Int? {
    if (plan.basePlanId == monthly.basePlanId) return null
    val monthlyRate = monthly.monthlyEquivalentMicros()
    val planRate = plan.monthlyEquivalentMicros()
    if (monthlyRate <= 0 || planRate <= 0 || planRate >= monthlyRate) {
        return null
    }
    val pct = (monthlyRate - planRate).toDouble() * 100.0 / monthlyRate
    return kotlin.math.round(pct).toInt().takeIf { it > 0 }
}

enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** Compra de assinatura vista pela loja. */
data class StorePurchase(
    val productId: String,
    val purchaseToken: String,
    val state: PurchaseState,
    val acknowledged: Boolean,
)

/** Uma compra concede o Pro sse é do produto certo e está comprada
 *  (PENDING ainda não concede — a loja pode não confirmar). */
fun StorePurchase.grantsPro(): Boolean =
    productId == ConstanciaBilling.PRODUCT_ID && state == PurchaseState.PURCHASED

// ---------------------------------------------------------------------------
// Máquina de estados da tela de compra (paywall). Redutor puro: a UI só
// despacha eventos; toda transição é testável sem Compose nem loja.
// ---------------------------------------------------------------------------

sealed interface BillingUiState {
    /** Consultando a loja (produto/preço ainda não chegaram). */
    data object Loading : BillingUiState

    /** Produto carregado, pronto para comprar. */
    data class Ready(val product: StoreProduct) : BillingUiState

    /** Fluxo da loja aberto / compra em andamento DESTE plano-base. */
    data class Purchasing(
        val product: StoreProduct,
        val basePlanId: String,
    ) : BillingUiState

    /** Loja/produto indisponível neste aparelho ou build (sideload sem
     *  Play, produto ainda não criado no Console, iOS sem StoreKit).
     *  Estado HONESTO: nunca finge preço nem compra. */
    data class Unavailable(val message: String) : BillingUiState

    /** Erro exibível com o produto ainda em mãos (dá para tentar de novo). */
    data class Failed(val product: StoreProduct?, val message: String) : BillingUiState

    /** Entitlement ativo (compra concluída ou restaurada). */
    data object Purchased : BillingUiState
}

sealed interface BillingEvent {
    data class ProductLoaded(val product: StoreProduct) : BillingEvent
    data object ProductNotConfigured : BillingEvent
    data class StoreUnavailable(val message: String) : BillingEvent
    data class BuyClicked(val basePlanId: String) : BillingEvent
    data object PurchaseCompleted : BillingEvent
    data class PurchaseError(val message: String) : BillingEvent
    data object PurchaseCancelled : BillingEvent
    data object RestoreFoundPro : BillingEvent
    data class RestoreFinished(val message: String) : BillingEvent
}

fun reduceBilling(state: BillingUiState, event: BillingEvent): BillingUiState =
    when (event) {
        is BillingEvent.ProductLoaded -> BillingUiState.Ready(event.product)
        is BillingEvent.ProductNotConfigured -> BillingUiState.Unavailable(
            "A assinatura ainda não está disponível nesta versão. " +
                "Quando o Constância Pro entrar na loja, ele aparece aqui.",
        )
        is BillingEvent.StoreUnavailable -> BillingUiState.Unavailable(event.message)
        is BillingEvent.BuyClicked -> when (state) {
            // Só compra plano que a loja REALMENTE retornou.
            is BillingUiState.Ready ->
                if (state.product.plan(event.basePlanId) != null) {
                    BillingUiState.Purchasing(state.product, event.basePlanId)
                } else {
                    state
                }
            is BillingUiState.Failed ->
                state.product
                    ?.takeIf { it.plan(event.basePlanId) != null }
                    ?.let { BillingUiState.Purchasing(it, event.basePlanId) }
                    ?: state
            else -> state
        }
        BillingEvent.PurchaseCompleted -> BillingUiState.Purchased
        is BillingEvent.PurchaseError -> when (state) {
            is BillingUiState.Purchasing ->
                BillingUiState.Failed(state.product, event.message)
            is BillingUiState.Ready ->
                BillingUiState.Failed(state.product, event.message)
            else -> BillingUiState.Failed(null, event.message)
        }
        BillingEvent.PurchaseCancelled -> when (state) {
            is BillingUiState.Purchasing -> BillingUiState.Ready(state.product)
            else -> state
        }
        BillingEvent.RestoreFoundPro -> BillingUiState.Purchased
        is BillingEvent.RestoreFinished -> when (state) {
            is BillingUiState.Ready ->
                BillingUiState.Failed(state.product, event.message)
            is BillingUiState.Failed ->
                BillingUiState.Failed(state.product, event.message)
            else -> state
        }
    }
