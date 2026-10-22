package app.constancia.billing

import app.constancia.domain.StoreProduct
import app.constancia.domain.StorePurchase

/**
 * iOS — Fase F: StoreKit FORA do escopo (conta Apple Developer adiada
 * pelo Renner). O gateway responde honestamente: sem loja integrada,
 * sem produto, sem como verificar compras. O paywall mostra "em breve"
 * e NENHUMA compra é fingida. O entitlement continua vindo do cache
 * local (prefs), que no iOS hoje é sempre o estado gravado no aparelho.
 */
actual class BillingGateway : StoreBilling {

    actual override fun storeAvailable(): Boolean = false

    actual override suspend fun queryProduct(): StoreProduct? = null

    /** null = não deu para verificar (mantém o cache). */
    actual override suspend fun queryOwnedPurchases(): List<StorePurchase>? = null

    actual override suspend fun acknowledge(purchase: StorePurchase): Boolean = false

    actual override fun setPurchaseListener(listener: ((StorePurchase) -> Unit)?) = Unit

    actual override fun launchPurchase(
        product: StoreProduct,
        basePlanId: String,
    ): Boolean = false
}
