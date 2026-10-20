package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.DiaryRepository
import app.constancia.db.ConstanciaDb
import app.constancia.model.DiaryEntry
import app.constancia.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class DiaryRepositoryImpl(private val db: ConstanciaDb) : DiaryRepository {

    private val queries get() = db.constanciaDbQueries

    override fun entryForDateFlow(dateYmd: String): Flow<DiaryEntry?> =
        queries.selectDiaryForDate(dateYmd)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.firstOrNull()?.toModel() }

    override suspend fun save(
        dateYmd: String,
        mood: Int?,
        note: String,
        energy: Int?,
        sleepHours: Double?,
        tags: List<String>,
        photoUri: String?,
        prompt: String?,
    ): Unit = withContext(Dispatchers.Default) {
        queries.upsertDiary(
            newId(),
            dateYmd,
            mood?.toLong(),
            note,
            energy?.toLong(),
            sleepHours,
            tags.toTagCsv(),
            photoUri,
            prompt,
            Clock.System.now().toString(),
        )
    }

    override fun recentFlow(limit: Int): Flow<List<DiaryEntry>> =
        queries.selectDiaryRecent(limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override fun allEntriesFlow(): Flow<List<DiaryEntry>> =
        queries.selectAllDiary()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }
}
