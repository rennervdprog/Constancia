package app.constancia.billing

import app.constancia.data.PrefsRepository
import app.constancia.domain.StorePurchase
import app.constancia.domain.grantsPro
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

// ---------------------------------------------------------------------------
// Fase F (Comercial) — FONTE ÚNICA DA VERDADE do entitlement.
//
// Quem é Pro? A LOJA diz (compras verificadas via [StoreBilling]); o
// resultado fica em cache nas prefs (`is_pro`) para o app abrir offline
// e as telas lerem um Flow só. Este manager é o ÚNICO escritor do cache:
// nenhuma tela chama `prefs.setPro` diretamente.
//
// SEM BACKEND nesta fase: o Supabase do Constância ainda não existe, então
// o entitlement é LOCAL ao aparelho + conta da loja. Limitação registrada
// em FASE-F-NOTES.md: sem servidor, não há como revogar/validar recibo
// fora do que a própria loja informa neste dispositivo.
// ---------------------------------------------------------------------------

class EntitlementManager(
    private val prefs: PrefsRepository,
    private val store: StoreBilling,
) {

    /** O Flow que todas as telas leem (cache local, reativo). */
    val isProFlow: Flow<Boolean> = prefs.isProFlow()

    /**
     * Reconcilia o cache com a loja: na abertura do app e no "Restaurar
     * compra". Se a loja confirmar compras, reconhece (acknowledge) as
     * pendentes e grava o entitlement; se confirmar que NÃO há compra,
     * grava false (downgrade honesto — as superfícies Pro voltam ao
     * cadeado, sem apagar dado nenhum). Se NÃO der para verificar
     * (loja inalcançável/build fora da Play), mantém o cache como está.
     * Retorna o entitlement resultante.
     */
    suspend fun refreshFromStore(): Boolean {
        val owned = store.queryOwnedPurchases()
            ?: return prefs.isProFlow().first()
        val proPurchases = owned.filter { it.grantsPro() }
        proPurchases.filter { !it.acknowledged }.forEach { purchase ->
            runCatching { store.acknowledge(purchase) }
        }
        val pro = proPurchases.isNotEmpty()
        prefs.setPro(pro)
        return pro
    }

    /**
     * Compra concluída entregue pelo fluxo da loja (listener): reconhece
     * e ativa o entitlement na hora. Compra PENDING não ativa (a loja
     * ainda não confirmou) — ela chega de novo quando confirmar.
     */
    suspend fun onPurchaseUpdated(purchase: StorePurchase) {
        if (!purchase.grantsPro()) return
        if (!purchase.acknowledged) {
            runCatching { store.acknowledge(purchase) }
        }
        prefs.setPro(true)
    }
}
