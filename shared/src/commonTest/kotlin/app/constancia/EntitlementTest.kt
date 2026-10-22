package app.constancia

import app.constancia.billing.EntitlementManager
import app.constancia.billing.StoreBilling
import app.constancia.data.PrefsRepository
import app.constancia.domain.BillingEvent
import app.constancia.domain.BillingUiState
import app.constancia.domain.ConstanciaBilling
import app.constancia.domain.PlanCatalog
import app.constancia.domain.PlanPeriod
import app.constancia.domain.ProFeature
import app.constancia.domain.ProGate
import app.constancia.domain.PurchaseState
import app.constancia.domain.StoreOffer
import app.constancia.domain.StorePlan
import app.constancia.domain.StoreProduct
import app.constancia.domain.StorePurchase
import app.constancia.domain.grantsPro
import app.constancia.domain.monthlyEquivalentMicros
import app.constancia.domain.monthsOfBillingPeriod
import app.constancia.domain.reduceBilling
import app.constancia.domain.savingsPercentVsMonthly
import app.constancia.model.ChallengeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fase F (Comercial) — a decisão aprovada em forma de teste:
 * grátis generoso (nenhum gate quantitativo), Pro = superfícies
 * avançadas; downgrade devolve o cadeado sem apagar nada; upgrade
 * destrava na hora. Gates, redutor da tela de compra e o manager de
 * entitlement (com loja falsa) — tudo lógica pura.
 */
class EntitlementTest {

    // -- ProGate: superfícies ------------------------------------------

    @Test
    fun proFeatures_lockedForFree_unlockedForPro() {
        ProFeature.values().forEach { feature ->
            assertFalse(ProGate.isUnlocked(feature, isPro = false), "$feature")
            assertTrue(ProGate.isUnlocked(feature, isPro = true), "$feature")
        }
    }

    // -- ProGate: desafios ----------------------------------------------

    @Test
    fun freeUser_cannotStartProChallenge_available() {
        assertFalse(
            ProGate.canStartChallenge(
                challengeIsPro = true,
                status = ChallengeStatus.AVAILABLE,
                isPro = false,
            ),
        )
    }

    @Test
    fun proUser_canStartProChallenge() {
        assertTrue(
            ProGate.canStartChallenge(
                challengeIsPro = true,
                status = ChallengeStatus.AVAILABLE,
                isPro = true,
            ),
        )
    }

    @Test
    fun freeChallenge_isAlwaysStartable() {
        ChallengeStatus.values().forEach { status ->
            assertTrue(
                ProGate.canStartChallenge(
                    challengeIsPro = false, status = status, isPro = false,
                ),
                "$status",
            )
        }
    }

    @Test
    fun downgrade_keepsStartedProChallengeUsable() {
        // Começou no Pro, caiu pro grátis: ACTIVE/DONE seguem usáveis —
        // nada do que o usuário já usa trava (decisão aprovada).
        listOf(
            ChallengeStatus.ACTIVE,
            ChallengeStatus.DONE,
            ChallengeStatus.GIVEN_UP,
        ).forEach { status ->
            assertTrue(
                ProGate.canStartChallenge(
                    challengeIsPro = true, status = status, isPro = false,
                ),
                "$status",
            )
        }
    }

    // -- grantsPro -------------------------------------------------------

    private fun purchase(
        productId: String = ConstanciaBilling.PRODUCT_ID,
        state: PurchaseState = PurchaseState.PURCHASED,
        acknowledged: Boolean = true,
    ) = StorePurchase(productId, "token-1", state, acknowledged)

    @Test
    fun grantsPro_onlyForRightProductPurchased() {
        assertTrue(purchase().grantsPro())
        assertFalse(purchase(productId = "outro_produto").grantsPro())
        assertFalse(purchase(state = PurchaseState.PENDING).grantsPro())
        assertFalse(purchase(state = PurchaseState.UNSPECIFIED).grantsPro())
    }

    // -- Redutor da tela de compra ---------------------------------------

    private val monthlyPlan = StorePlan(
        basePlanId = ConstanciaBilling.MONTHLY_BASE_PLAN_ID,
        period = PlanPeriod.MONTHLY,
        offerId = null,
        formattedPrice = "R$ 19,90",
        priceAmountMicros = 19_900_000L,
        currencyCode = "BRL",
        formattedMonthlyEquivalent = "R$ 19,90",
        trialDays = null,
    )

    private val quarterlyPlan = StorePlan(
        basePlanId = ConstanciaBilling.QUARTERLY_BASE_PLAN_ID,
        period = PlanPeriod.QUARTERLY,
        offerId = null,
        formattedPrice = "R$ 44,90",
        priceAmountMicros = 44_900_000L,
        currencyCode = "BRL",
        formattedMonthlyEquivalent = "R$ 14,97",
        trialDays = null,
    )

    private val annualPlan = StorePlan(
        basePlanId = ConstanciaBilling.ANNUAL_BASE_PLAN_ID,
        period = PlanPeriod.ANNUAL,
        offerId = ConstanciaBilling.TRIAL_OFFER_ID,
        formattedPrice = "R$ 119,90",
        priceAmountMicros = 119_900_000L,
        currencyCode = "BRL",
        formattedMonthlyEquivalent = "R$ 9,99",
        trialDays = 7,
    )

    private val product = StoreProduct(
        productId = ConstanciaBilling.PRODUCT_ID,
        plans = listOf(monthlyPlan, quarterlyPlan, annualPlan),
    )

    private val annual = ConstanciaBilling.ANNUAL_BASE_PLAN_ID

    @Test
    fun billing_loadingToReadyToPurchasingToPurchased() {
        var state: BillingUiState = BillingUiState.Loading
        state = reduceBilling(state, BillingEvent.ProductLoaded(product))
        assertEquals(BillingUiState.Ready(product), state)
        state = reduceBilling(state, BillingEvent.BuyClicked(annual))
        assertEquals(BillingUiState.Purchasing(product, annual), state)
        state = reduceBilling(state, BillingEvent.PurchaseCompleted)
        assertEquals(BillingUiState.Purchased, state)
    }

    @Test
    fun billing_productMissing_isHonestUnavailable() {
        val state = reduceBilling(
            BillingUiState.Loading, BillingEvent.ProductNotConfigured,
        )
        assertTrue(state is BillingUiState.Unavailable)
    }

    @Test
    fun billing_cancelReturnsToReady_errorGoesToFailedAndRetries() {
        var state: BillingUiState = BillingUiState.Purchasing(product, annual)
        state = reduceBilling(state, BillingEvent.PurchaseCancelled)
        assertEquals(BillingUiState.Ready(product), state)
        state = reduceBilling(state, BillingEvent.BuyClicked(annual))
        state = reduceBilling(state, BillingEvent.PurchaseError("rede"))
        assertEquals(BillingUiState.Failed(product, "rede"), state)
        // Do erro dá para tentar de novo — inclusive com OUTRO plano.
        state = reduceBilling(
            state,
            BillingEvent.BuyClicked(ConstanciaBilling.MONTHLY_BASE_PLAN_ID),
        )
        assertEquals(
            BillingUiState.Purchasing(
                product, ConstanciaBilling.MONTHLY_BASE_PLAN_ID,
            ),
            state,
        )
    }

    @Test
    fun billing_buyWithoutProduct_doesNothing() {
        val loading: BillingUiState = BillingUiState.Loading
        assertEquals(
            loading, reduceBilling(loading, BillingEvent.BuyClicked(annual)),
        )
        val unavailable: BillingUiState = BillingUiState.Unavailable("x")
        assertEquals(
            unavailable,
            reduceBilling(unavailable, BillingEvent.BuyClicked(annual)),
        )
    }

    @Test
    fun billing_buyPlanTheStoreDidNotReturn_doesNothing() {
        // Plano que não veio da loja (ex.: "semanal") não abre compra.
        val ready: BillingUiState = BillingUiState.Ready(product)
        assertEquals(
            ready, reduceBilling(ready, BillingEvent.BuyClicked("semanal")),
        )
    }

    @Test
    fun billing_buyMonthly_goesToPurchasingWithMonthlyPlan() {
        val state = reduceBilling(
            BillingUiState.Ready(product),
            BillingEvent.BuyClicked(ConstanciaBilling.MONTHLY_BASE_PLAN_ID),
        )
        assertEquals(
            BillingUiState.Purchasing(
                product, ConstanciaBilling.MONTHLY_BASE_PLAN_ID,
            ),
            state,
        )
    }

    @Test
    fun billing_restoreFoundPro_goesToPurchased() {
        val state = reduceBilling(
            BillingUiState.Ready(product), BillingEvent.RestoreFoundPro,
        )
        assertEquals(BillingUiState.Purchased, state)
    }

    @Test
    fun billing_restoreWithoutPro_keepsProductWithMessage() {
        val state = reduceBilling(
            BillingUiState.Ready(product),
            BillingEvent.RestoreFinished("Nenhuma assinatura ativa encontrada."),
        )
        assertEquals(
            BillingUiState.Failed(product, "Nenhuma assinatura ativa encontrada."),
            state,
        )
    }

    // -- EntitlementManager (loja falsa) ----------------------------------

    private class FakePrefs : PrefsRepository {
        val pro = MutableStateFlow(false)
        override fun isProFlow(): Flow<Boolean> = pro
        override suspend fun setPro(value: Boolean) { pro.value = value }

        override fun darkThemeFlow(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setDarkTheme(dark: Boolean) = Unit
        override fun onboardingDoneFlow(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setOnboardingDone() = Unit
        override fun taskDailyGoalFlow(): Flow<Int> = MutableStateFlow(0)
        override suspend fun setTaskDailyGoal(goal: Int) = Unit
        override fun taskWeeklyGoalFlow(): Flow<Int> = MutableStateFlow(0)
        override suspend fun setTaskWeeklyGoal(goal: Int) = Unit
        override fun dailyReviewEnabledFlow(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setDailyReviewEnabled(enabled: Boolean) = Unit
        override fun dailyReviewTimeFlow(): Flow<String> = MutableStateFlow("08:00")
        override suspend fun setDailyReviewTime(time: String) = Unit
        override fun autoReminderMinutesFlow(): Flow<Int?> = MutableStateFlow(null)
        override suspend fun setAutoReminderMinutes(minutes: Int?) = Unit
        override fun goalIgnoreWeekdaysFlow(): Flow<Set<Int>> = MutableStateFlow(emptySet())
        override suspend fun setGoalIgnoreWeekdays(days: Set<Int>) = Unit
        override fun vacationModeFlow(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setVacationMode(enabled: Boolean) = Unit
        override fun vacationHabitIdsFlow(): Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun setVacationHabitIds(ids: Set<String>) = Unit
        override fun vacationGoalPauseOpenedFlow(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setVacationGoalPauseOpened(opened: Boolean) = Unit
    }

    private class FakeStore(
        var owned: List<StorePurchase>? = emptyList(),
    ) : StoreBilling {
        val acknowledgedTokens = mutableListOf<String>()
        override fun storeAvailable(): Boolean = true
        override suspend fun queryProduct(): StoreProduct? = null
        override suspend fun queryOwnedPurchases(): List<StorePurchase>? = owned
        override suspend fun acknowledge(purchase: StorePurchase): Boolean {
            acknowledgedTokens += purchase.purchaseToken
            return true
        }
        override fun setPurchaseListener(listener: ((StorePurchase) -> Unit)?) = Unit
        override fun launchPurchase(
            product: StoreProduct,
            basePlanId: String,
        ): Boolean = false
    }

    @Test
    fun refresh_withProPurchase_activatesAndAcknowledges() = runBlocking {
        val prefs = FakePrefs()
        val store = FakeStore(
            owned = listOf(purchase(acknowledged = false)),
        )
        val manager = EntitlementManager(prefs, store)
        assertTrue(manager.refreshFromStore())
        assertTrue(manager.isProFlow.first())
        assertEquals(listOf("token-1"), store.acknowledgedTokens)
    }

    @Test
    fun refresh_verifiedEmpty_downgradesCache() = runBlocking {
        // O critério de pronto da fase: usuário Pro sem compra ativa na
        // loja → entitlement cai (as telas voltam ao cadeado sozinhas).
        val prefs = FakePrefs()
        prefs.setPro(true)
        val manager = EntitlementManager(prefs, FakeStore(owned = emptyList()))
        assertFalse(manager.refreshFromStore())
        assertFalse(manager.isProFlow.first())
    }

    @Test
    fun refresh_unverifiable_keepsCache() = runBlocking {
        // Loja inalcançável (build fora da Play): NUNCA rebaixa no escuro.
        val prefs = FakePrefs()
        prefs.setPro(true)
        val manager = EntitlementManager(prefs, FakeStore(owned = null))
        assertTrue(manager.refreshFromStore())
        assertTrue(manager.isProFlow.first())
    }

    @Test
    fun refresh_pendingPurchase_doesNotActivate() = runBlocking {
        val prefs = FakePrefs()
        val store = FakeStore(
            owned = listOf(purchase(state = PurchaseState.PENDING)),
        )
        val manager = EntitlementManager(prefs, store)
        assertFalse(manager.refreshFromStore())
        assertTrue(store.acknowledgedTokens.isEmpty())
    }

    @Test
    fun purchaseUpdated_activatesImmediately() = runBlocking {
        val prefs = FakePrefs()
        val store = FakeStore()
        val manager = EntitlementManager(prefs, store)
        manager.onPurchaseUpdated(purchase(acknowledged = false))
        assertTrue(manager.isProFlow.first())
        assertEquals(listOf("token-1"), store.acknowledgedTokens)
    }

    @Test
    fun purchaseUpdated_pendingOrForeign_doesNotActivate() = runBlocking {
        val prefs = FakePrefs()
        val manager = EntitlementManager(prefs, FakeStore())
        manager.onPurchaseUpdated(purchase(state = PurchaseState.PENDING))
        manager.onPurchaseUpdated(purchase(productId = "outro"))
        assertFalse(manager.isProFlow.first())
    }

    @Test
    fun upgradeAfterDowngrade_restoresEverything() = runBlocking {
        // Downgrade → upgrade: o ciclo completo sem perda (o cache é só
        // um booleano; os dados do usuário nunca são tocados).
        val prefs = FakePrefs()
        val store = FakeStore(owned = listOf(purchase()))
        val manager = EntitlementManager(prefs, store)
        assertTrue(manager.refreshFromStore())
        store.owned = emptyList()
        assertFalse(manager.refreshFromStore())
        store.owned = listOf(purchase())
        assertTrue(manager.refreshFromStore())
        assertTrue(ProGate.isUnlocked(ProFeature.CROSS_PATTERNS, prefs.pro.value))
    }

    @Test
    fun refresh_anyBasePlanPurchase_grantsPro() = runBlocking {
        // Entitlement é por PRODUTO, não por plano: a compra que a loja
        // devolve pode ser de qualquer plano-base (mensal, trimestral
        // ou anual) — todas concedem o Pro do mesmo jeito.
        val prefs = FakePrefs()
        val manager = EntitlementManager(
            prefs, FakeStore(owned = listOf(purchase())),
        )
        assertTrue(manager.refreshFromStore())
        assertTrue(manager.isProFlow.first())
    }

    @Test
    fun planSwitch_keepsProWithoutGap() = runBlocking {
        // Troca de plano (mensal → anual): a loja passa a devolver uma
        // compra NOVA (token novo) do mesmo produto. A reconciliação
        // nunca pode derrubar o Pro no meio da troca.
        val prefs = FakePrefs()
        val store = FakeStore(
            owned = listOf(
                StorePurchase(
                    ConstanciaBilling.PRODUCT_ID, "token-mensal",
                    PurchaseState.PURCHASED, acknowledged = true,
                ),
            ),
        )
        val manager = EntitlementManager(prefs, store)
        assertTrue(manager.refreshFromStore())
        store.owned = listOf(
            StorePurchase(
                ConstanciaBilling.PRODUCT_ID, "token-anual",
                PurchaseState.PURCHASED, acknowledged = false,
            ),
        )
        assertTrue(manager.refreshFromStore())
        assertTrue(manager.isProFlow.first())
        // A compra nova (da troca) também é reconhecida (acknowledge).
        assertEquals(listOf("token-anual"), store.acknowledgedTokens)
    }

    // -- PlanCatalog: mapeamento plano → oferta ---------------------------

    private fun offer(
        basePlanId: String,
        offerId: String? = null,
        formattedPrice: String = "R$ 19,90",
        micros: Long = 19_900_000L,
        period: String = "P1M",
        monthlyEq: String = "R$ 19,90",
        trialDays: Int? = null,
        token: String = "tok-$basePlanId-${offerId ?: "padrao"}",
    ) = StoreOffer(
        basePlanId = basePlanId,
        offerId = offerId,
        formattedPrice = formattedPrice,
        priceAmountMicros = micros,
        currencyCode = "BRL",
        billingPeriod = period,
        formattedMonthlyEquivalent = monthlyEq,
        trialDays = trialDays,
        offerToken = token,
    )

    private fun storeOffers() = listOf(
        // Anual chega em duas ofertas: a padrão e a de trial.
        offer(
            basePlanId = "anual", formattedPrice = "R$ 119,90",
            micros = 119_900_000L, period = "P1Y", monthlyEq = "R$ 9,99",
        ),
        offer(
            basePlanId = "anual", offerId = "trial-7-dias",
            formattedPrice = "R$ 119,90", micros = 119_900_000L,
            period = "P1Y", monthlyEq = "R$ 9,99", trialDays = 7,
        ),
        offer(basePlanId = "mensal"),
        offer(
            basePlanId = "trimestral", formattedPrice = "R$ 44,90",
            micros = 44_900_000L, period = "P3M", monthlyEq = "R$ 14,97",
        ),
        // Plano desconhecido no app: a loja pode ter, o app ignora.
        offer(basePlanId = "semanal", period = "P1W"),
    )

    @Test
    fun catalog_resolvePlans_displayOrderAndKnownPlansOnly() {
        val plans = PlanCatalog.resolvePlans(storeOffers())
        assertEquals(
            listOf("mensal", "trimestral", "anual"),
            plans.map { it.basePlanId },
        )
        assertEquals(
            listOf(PlanPeriod.MONTHLY, PlanPeriod.QUARTERLY, PlanPeriod.ANNUAL),
            plans.map { it.period },
        )
    }

    @Test
    fun catalog_annual_usesTrialOffer() {
        val annual = PlanCatalog.resolvePlans(storeOffers())
            .first { it.basePlanId == "anual" }
        assertEquals("trial-7-dias", annual.offerId)
        assertEquals(7, annual.trialDays)
        // E o token da compra é o DA OFERTA DE TRIAL, não o da padrão.
        val chosen = PlanCatalog.chooseOffers(storeOffers())
        assertEquals("tok-anual-trial-7-dias", chosen["anual"]?.offerToken)
    }

    @Test
    fun catalog_monthlyAndQuarterly_useDefaultOfferWithoutTrial() {
        val plans = PlanCatalog.resolvePlans(storeOffers())
        val monthly = plans.first { it.basePlanId == "mensal" }
        val quarterly = plans.first { it.basePlanId == "trimestral" }
        assertEquals(null, monthly.offerId)
        assertEquals(null, quarterly.offerId)
        assertEquals(null, monthly.trialDays)
        assertEquals(null, quarterly.trialDays)
    }

    @Test
    fun catalog_trialNeverLeaksToNonAnnualPlans() {
        // Mesmo que a loja devolva fase grátis num plano não-anual, o
        // plano resolvido NÃO anuncia trial (decisão de preço).
        val offers = listOf(
            offer(basePlanId = "mensal", trialDays = 3),
            offer(
                basePlanId = "anual", offerId = "trial-7-dias",
                formattedPrice = "R$ 119,90", micros = 119_900_000L,
                period = "P1Y", monthlyEq = "R$ 9,99", trialDays = 7,
            ),
        )
        val plans = PlanCatalog.resolvePlans(offers)
        assertEquals(
            null, plans.first { it.basePlanId == "mensal" }.trialDays,
        )
        assertEquals(7, plans.first { it.basePlanId == "anual" }.trialDays)
    }

    @Test
    fun catalog_annualWithoutTrialOffer_hasNoTrial() {
        val offers = listOf(
            offer(
                basePlanId = "anual", formattedPrice = "R$ 119,90",
                micros = 119_900_000L, period = "P1Y", monthlyEq = "R$ 9,99",
            ),
        )
        val annual = PlanCatalog.resolvePlans(offers).single()
        assertEquals(null, annual.offerId)
        assertEquals(null, annual.trialDays)
    }

    @Test
    fun catalog_missingPlans_areSimplyAbsent() {
        val plans = PlanCatalog.resolvePlans(listOf(offer(basePlanId = "mensal")))
        assertEquals(listOf("mensal"), plans.map { it.basePlanId })
        assertTrue(PlanCatalog.resolvePlans(emptyList()).isEmpty())
    }

    // -- Matemática de exibição (só preços reais da loja) -----------------

    @Test
    fun monthsOfBillingPeriod_parsesIsoPeriods() {
        assertEquals(1, monthsOfBillingPeriod("P1M"))
        assertEquals(3, monthsOfBillingPeriod("P3M"))
        assertEquals(12, monthsOfBillingPeriod("P1Y"))
        assertEquals(14, monthsOfBillingPeriod("P1Y2M"))
        assertEquals(0, monthsOfBillingPeriod("P7D"))
        assertEquals(0, monthsOfBillingPeriod("P1W"))
    }

    @Test
    fun monthlyEquivalent_dividesByPeriodMonths() {
        assertEquals(19_900_000L, monthlyPlan.monthlyEquivalentMicros())
        assertEquals(14_966_666L, quarterlyPlan.monthlyEquivalentMicros())
        assertEquals(9_991_666L, annualPlan.monthlyEquivalentMicros())
    }

    @Test
    fun savings_computedFromRealPrices() {
        // 19,90/mês vs 119,90/ano (≈9,99/mês) → 50%; vs 44,90/trim
        // (≈14,97/mês) → 25%. Calculado dos micros, nunca decorativo.
        assertEquals(50, savingsPercentVsMonthly(annualPlan, monthlyPlan))
        assertEquals(25, savingsPercentVsMonthly(quarterlyPlan, monthlyPlan))
    }

    @Test
    fun savings_nullWhenNoHonestComparison() {
        // O próprio mensal não "economiza" contra si mesmo.
        assertEquals(null, savingsPercentVsMonthly(monthlyPlan, monthlyPlan))
        // Plano cuja taxa mensal NÃO é menor: sem selo de economia.
        val expensiveAnnual = annualPlan.copy(
            priceAmountMicros = 300_000_000L,
        )
        assertEquals(null, savingsPercentVsMonthly(expensiveAnnual, monthlyPlan))
        // Preço zerado/ausente: sem comparação.
        val freeMonthly = monthlyPlan.copy(priceAmountMicros = 0L)
        assertEquals(null, savingsPercentVsMonthly(annualPlan, freeMonthly))
    }

    @Test
    fun planLabels_priceWithSuffixAndMonthlyEquivalent() {
        assertEquals("R$ 19,90/mês", monthlyPlan.priceWithSuffix())
        assertEquals("R$ 44,90/3 meses", quarterlyPlan.priceWithSuffix())
        assertEquals("R$ 119,90/ano", annualPlan.priceWithSuffix())
        assertEquals("≈ R$ 9,99/mês", annualPlan.monthlyEquivalentLabel())
    }

    @Test
    fun product_featuredAndHeadlinePlans() {
        assertEquals(annualPlan, product.featuredPlan())
        assertEquals(monthlyPlan, product.headlinePlan())
        val noMonthly = StoreProduct(
            ConstanciaBilling.PRODUCT_ID, listOf(quarterlyPlan, annualPlan),
        )
        assertEquals(annualPlan, noMonthly.headlinePlan())
        val onlyQuarterly = StoreProduct(
            ConstanciaBilling.PRODUCT_ID, listOf(quarterlyPlan),
        )
        assertEquals(quarterlyPlan, onlyQuarterly.featuredPlan())
    }
}
