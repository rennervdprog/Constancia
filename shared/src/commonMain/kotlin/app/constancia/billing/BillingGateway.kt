package app.constancia.billing

import app.constancia.domain.StoreProduct
import app.constancia.domain.StorePurchase

// ---------------------------------------------------------------------------
// Fase F (Comercial) — fronteira com a loja, por plataforma.
//
// Android: Google Play Billing Library de verdade (BillingGateway.android).
// iOS: SEM StoreKit nesta fase — a conta Apple Developer está adiada
// (decisão do Renner), então o gateway iOS responde "loja indisponível"
// honestamente e o paywall mostra "em breve", sem compra falsa.
//
// A interface [StoreBilling] é a costura testável: o EntitlementManager
// depende dela, não da classe da plataforma.
// ---------------------------------------------------------------------------

interface StoreBilling {

    /** A plataforma tem loja de assinaturas integrada nesta fase?
     *  Android = true; iOS = false (StoreKit fora do escopo). */
    fun storeAvailable(): Boolean

    /**
     * Produto `constancia_pro` como a loja o descreve (preço formatado
     * e dias de teste vêm DA LOJA). null = produto não configurado, loja
     * inalcançável ou build fora da Play — a UI mostra estado honesto.
     */
    suspend fun queryProduct(): StoreProduct?

    /**
     * Compras de assinatura que a conta da loja já tem.
     * null = NÃO deu para verificar agora (sem conexão com a loja) — o
     * chamador mantém o entitlement em cache, nunca rebaixa no escuro.
     * Lista vazia = verificado: nenhuma compra ativa.
     */
    suspend fun queryOwnedPurchases(): List<StorePurchase>?

    /** Confirma (acknowledge) uma compra comprada; true se ok. */
    suspend fun acknowledge(purchase: StorePurchase): Boolean

    /** Listener de compras concluídas fora da tela (fluxo da loja). */
    fun setPurchaseListener(listener: ((StorePurchase) -> Unit)?)

    /**
     * Abre o fluxo de compra da loja para o produto já consultado.
     * true = fluxo aberto; o resultado chega pelo listener. false = não
     * deu para abrir (sem Activity, sem produto em cache etc.).
     */
    fun launchPurchase(product: StoreProduct): Boolean
}

expect class BillingGateway() : StoreBilling {
    override fun storeAvailable(): Boolean
    override suspend fun queryProduct(): StoreProduct?
    override suspend fun queryOwnedPurchases(): List<StorePurchase>?
    override suspend fun acknowledge(purchase: StorePurchase): Boolean
    override fun setPurchaseListener(listener: ((StorePurchase) -> Unit)?)
    override fun launchPurchase(product: StoreProduct): Boolean
}
