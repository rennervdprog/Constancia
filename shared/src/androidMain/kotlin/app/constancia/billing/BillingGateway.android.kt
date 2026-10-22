package app.constancia.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import app.constancia.domain.ConstanciaBilling
import app.constancia.domain.PurchaseState
import app.constancia.domain.StoreProduct
import app.constancia.domain.StorePurchase
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Contexto/Activity vivos para o billing: o gateway é criado sem
 * contexto (padrão do NotificationScheduler). O MainActivity chama
 * [initBillingContext]; a Activity em primeiro plano é rastreada por
 * lifecycle callbacks (a Billing Library exige uma Activity para abrir
 * o fluxo de compra).
 */
object BillingHolder {
    @Volatile
    var appContext: Context? = null
        private set

    @Volatile
    private var resumedActivity: WeakReference<Activity>? = null

    val activity: Activity? get() = resumedActivity?.get()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        (context.applicationContext as? Application)
            ?.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: Activity) {
                        resumedActivity = WeakReference(activity)
                    }

                    override fun onActivityPaused(activity: Activity) {
                        if (resumedActivity?.get() === activity) {
                            resumedActivity = null
                        }
                    }

                    override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
                    override fun onActivityStarted(a: Activity) = Unit
                    override fun onActivityStopped(a: Activity) = Unit
                    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                    override fun onActivityDestroyed(a: Activity) = Unit
                },
            )
    }
}

/** Chamado pelo MainActivity (mesmo padrão de initNotificationContext). */
fun initBillingContext(context: Context) = BillingHolder.init(context)

/**
 * Android — Google Play Billing Library (Fase F).
 *
 * Fluxo: conectar → queryProductDetails(`constancia_pro`/subs) →
 * launchBillingFlow com a oferta do plano-base `anual` → o resultado
 * chega no PurchasesUpdatedListener → acknowledge → entitlement.
 * `queryPurchasesAsync` restaura compras existentes (abertura do app e
 * botão "Restaurar compra").
 *
 * Build fora da Play (sideload/emulador sem Play): a conexão falha ou o
 * produto não existe → as consultas retornam null e a UI mostra o estado
 * honesto de indisponível. Nada aqui finge compra nem preço.
 */
actual class BillingGateway : StoreBilling {

    @Volatile
    private var client: BillingClient? = null

    @Volatile
    private var purchaseListener: ((StorePurchase) -> Unit)? = null

    // ProductDetails + oferta escolhida da última consulta (o fluxo de
    // compra precisa do objeto da loja, não só do modelo neutro).
    @Volatile
    private var cachedDetails: com.android.billingclient.api.ProductDetails? = null

    @Volatile
    private var cachedOfferToken: String? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK &&
            purchases != null
        ) {
            purchases.forEach { purchase ->
                purchaseListener?.invoke(purchase.toStorePurchase())
            }
        }
    }

    actual override fun storeAvailable(): Boolean = true

    private suspend fun connectedClient(): BillingClient? {
        val context = BillingHolder.appContext ?: return null
        client?.takeIf { it.isReady }?.let { return it }
        val created = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .build()
        client = created
        val result = suspendCancellableCoroutine { cont ->
            created.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (cont.isActive) cont.resume(billingResult)
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            created
        } else {
            null
        }
    }

    actual override suspend fun queryProduct(): StoreProduct? {
        val billing = connectedClient() ?: return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ConstanciaBilling.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = runCatching { billing.queryProductDetails(params) }
            .getOrNull() ?: return null
        if (result.billingResult.responseCode !=
            BillingClient.BillingResponseCode.OK
        ) {
            return null
        }
        val details = result.productDetailsList?.firstOrNull() ?: return null
        val offers = details.subscriptionOfferDetails
        if (offers.isNullOrEmpty()) return null
        // Oferta do plano-base anual; sem ela, a primeira disponível.
        val offer = offers.firstOrNull {
            it.basePlanId == ConstanciaBilling.BASE_PLAN_ID
        } ?: offers.first()
        cachedDetails = details
        cachedOfferToken = offer.offerToken
        val phases = offer.pricingPhases.pricingPhaseList
        val paidPhase = phases.firstOrNull { it.priceAmountMicros > 0 } ?: phases.firstOrNull()
        val freePhase = phases.firstOrNull { it.priceAmountMicros == 0L }
        return StoreProduct(
            productId = details.productId,
            formattedPrice = paidPhase?.formattedPrice ?: "",
            trialDays = freePhase?.let { trialDaysOf(it.billingPeriod) },
        )
    }

    actual override suspend fun queryOwnedPurchases(): List<StorePurchase>? {
        val billing = connectedClient() ?: return null
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = runCatching { billing.queryPurchasesAsync(params) }
            .getOrNull() ?: return null
        if (result.billingResult.responseCode !=
            BillingClient.BillingResponseCode.OK
        ) {
            return null
        }
        return result.purchasesList.map { it.toStorePurchase() }
    }

    actual override suspend fun acknowledge(purchase: StorePurchase): Boolean {
        val billing = connectedClient() ?: return false
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = runCatching { billing.acknowledgePurchase(params) }
            .getOrNull() ?: return false
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    actual override fun setPurchaseListener(
        listener: ((StorePurchase) -> Unit)?,
    ) {
        purchaseListener = listener
    }

    actual override fun launchPurchase(product: StoreProduct): Boolean {
        val activity = BillingHolder.activity ?: return false
        val billing = client?.takeIf { it.isReady } ?: return false
        val details = cachedDetails ?: return false
        val offerToken = cachedOfferToken ?: return false
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        val result = billing.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun Purchase.toStorePurchase(): StorePurchase = StorePurchase(
        productId = products.firstOrNull() ?: "",
        purchaseToken = purchaseToken,
        state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.UNSPECIFIED
        },
        acknowledged = isAcknowledged,
    )

    /** "P7D" → 7, "P1W" → 7, "P1M" → 30, "P1Y" → 365 (só p/ exibição). */
    private fun trialDaysOf(period: String): Int? {
        var total = 0
        var found = false
        val regex = Regex("(\\d+)([YMWD])")
        regex.findAll(period).forEach { match ->
            val value = match.groupValues[1].toIntOrNull() ?: return@forEach
            total += when (match.groupValues[2]) {
                "Y" -> value * 365
                "M" -> value * 30
                "W" -> value * 7
                else -> value
            }
            found = true
        }
        return if (found && total > 0) total else null
    }
}
