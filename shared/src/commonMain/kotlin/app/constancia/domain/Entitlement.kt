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

    /** ID do plano-base anual dentro do produto. */
    const val BASE_PLAN_ID = "anual"

    /** ID da oferta com teste grátis (dentro do plano-base anual). */
    const val TRIAL_OFFER_ID = "trial-7-dias"

    /** Dias de teste grátis anunciados (a fonte exibida é a loja). */
    const val TRIAL_DAYS = 7

    /** Preço de referência do plano aprovado (R$ 119,90/ano). NUNCA é
     *  exibido na UI: o preço mostrado vem do ProductDetails da loja. */
    const val REFERENCE_PRICE_BRL = "R$ 119,90"
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

/** Produto de assinatura como a loja o descreve. [formattedPrice] é o
 *  texto de preço DA LOJA (ex.: "R$ 119,90") — a única fonte de preço
 *  que a UI pode exibir. [trialDays] vem da fase grátis da oferta. */
data class StoreProduct(
    val productId: String,
    val formattedPrice: String,
    val trialDays: Int?,
)

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

    /** Fluxo da loja aberto / compra em andamento. */
    data class Purchasing(val product: StoreProduct) : BillingUiState

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
    data object BuyClicked : BillingEvent
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
        BillingEvent.BuyClicked -> when (state) {
            is BillingUiState.Ready -> BillingUiState.Purchasing(state.product)
            is BillingUiState.Failed ->
                state.product?.let { BillingUiState.Purchasing(it) } ?: state
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
