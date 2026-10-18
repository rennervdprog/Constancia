package app.constancia.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Estado da sincronização com a nuvem. */
enum class SyncState {
    /** Conta do Supabase ainda não configurada — tudo fica só no aparelho. */
    CLOUD_SOON,
    SYNCED,
    SYNCING,
    ERROR,
}

data class SyncStatus(
    val state: SyncState,
    val lastSyncLabel: String, // ex.: "agora mesmo", "há 2 h", "nunca"
)

/**
 * Contrato da sincronização. A v1 usa [LocalSyncRepository] (tudo local).
 * A implementação Supabase ([SupabaseSyncRepository], DEFERIDA — Renner ainda
 * vai criar a conta) pluga aqui sem mudar nenhuma tela: basta trocar a
 * instância em App.kt quando SUPABASE_URL deixar de ser placeholder.
 */
interface SyncRepository {
    fun statusFlow(): Flow<SyncStatus>
    suspend fun syncNow(): Boolean
}

class LocalSyncRepository : SyncRepository {
    override fun statusFlow(): Flow<SyncStatus> = flowOf(
        SyncStatus(SyncState.CLOUD_SOON, "só neste aparelho")
    )

    override suspend fun syncNow(): Boolean = false
}
