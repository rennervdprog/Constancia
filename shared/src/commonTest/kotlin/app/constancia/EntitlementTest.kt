package app.constancia

import app.constancia.billing.EntitlementManager
import app.constancia.billing.StoreBilling
import app.constancia.data.PrefsRepository
import app.constancia.domain.BillingEvent
import app.constancia.domain.BillingUiState
import app.constancia.domain.ConstanciaBilling
import app.constancia.domain.ProFeature
import app.constancia.domain.ProGate
import app.constancia.domain.PurchaseState
import app.constancia.domain.StoreProduct
import app.constancia.domain.StorePurchase
import app.constancia.domain.grantsPro
import app.constancia.domain.reduceBilling
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

    private val product = StoreProduct(
        productId = ConstanciaBilling.PRODUCT_ID,
        formattedPrice = "R$ 119,90",
        trialDays = 7,
    )

    @Test
    fun billing_loadingToReadyToPurchasingToPurchased() {
        var state: BillingUiState = BillingUiState.Loading
        state = reduceBilling(state, BillingEvent.ProductLoaded(product))
        assertEquals(BillingUiState.Ready(product), state)
        state = reduceBilling(state, BillingEvent.BuyClicked)
        assertEquals(BillingUiState.Purchasing(product), state)
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
        var state: BillingUiState = BillingUiState.Purchasing(product)
        state = reduceBilling(state, BillingEvent.PurchaseCancelled)
        assertEquals(BillingUiState.Ready(product), state)
        state = reduceBilling(state, BillingEvent.BuyClicked)
        state = reduceBilling(state, BillingEvent.PurchaseError("rede"))
        assertEquals(BillingUiState.Failed(product, "rede"), state)
        // Do erro dá para tentar de novo com o mesmo produto.
        state = reduceBilling(state, BillingEvent.BuyClicked)
        assertEquals(BillingUiState.Purchasing(product), state)
    }

    @Test
    fun billing_buyWithoutProduct_doesNothing() {
        val loading: BillingUiState = BillingUiState.Loading
        assertEquals(loading, reduceBilling(loading, BillingEvent.BuyClicked))
        val unavailable: BillingUiState = BillingUiState.Unavailable("x")
        assertEquals(unavailable, reduceBilling(unavailable, BillingEvent.BuyClicked))
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
        override fun launchPurchase(product: StoreProduct): Boolean = false
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
}
