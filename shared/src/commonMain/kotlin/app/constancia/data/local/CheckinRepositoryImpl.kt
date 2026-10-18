package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.CheckinRepository
import app.constancia.db.ConstanciaDb
import app.constancia.model.Checkin
import app.constancia.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class CheckinRepositoryImpl(private val db: ConstanciaDb) : CheckinRepository {

    private val queries get() = db.constanciaDbQueries

    override suspend fun get(habitId: String, dateYmd: String): Checkin? =
        withContext(Dispatchers.IO) {
            queries.selectCheckin(habitId, dateYmd).executeAsOneOrNull()?.toModel()
        }

    // Retorna true se o dia ficou marcado como feito apos o toggle.
    override suspend fun toggle(
        habitId: String,
        dateYmd: String,
        quantity: Double,
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = queries.selectCheckin(habitId, dateYmd).executeAsOneOrNull()
        if (existing != null) {
            queries.deleteCheckin(habitId, dateYmd)
            false
        } else {
            queries.upsertCheckin(newId(), habitId, dateYmd, quantity, nowIso())
            true
        }
    }

    override suspend fun setQuantity(
        habitId: String,
        dateYmd: String,
        quantity: Double,
    ): Unit = withContext(Dispatchers.IO) {
        queries.upsertCheckin(newId(), habitId, dateYmd, quantity, nowIso())
    }

    override suspend fun remove(habitId: String, dateYmd: String): Unit =
        withContext(Dispatchers.IO) {
            queries.deleteCheckin(habitId, dateYmd)
        }

    override fun checkinsInRangeFlow(fromYmd: String, toYmd: String): Flow<List<Checkin>> =
        queries.selectCheckinsInRange(fromYmd, toYmd)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun checkinsForHabit(habitId: String): List<Checkin> =
        withContext(Dispatchers.IO) {
            queries.selectCheckinsForHabit(habitId).executeAsList().map { it.toModel() }
        }

    private fun nowIso(): String = Clock.System.now().toString()
}
