package app.constancia.data

import app.constancia.model.Challenge
import app.constancia.model.ChallengeStatus
import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.Habit
import app.constancia.model.HabitStatus
import app.constancia.model.TaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone

/**
 * Contratos da camada de dados (implementação SQLDelight em data/local/).
 * As telas dependem só destas interfaces — a troca futura para sync remoto
 * não muda nenhuma tela.
 */
interface HabitRepository {
    fun habitsFlow(): Flow<List<Habit>>
    suspend fun getById(id: String): Habit?
    suspend fun save(habit: Habit) // insert ou update pelo id
    suspend fun setPaused(id: String, paused: Boolean)
    suspend fun archive(id: String)
    suspend fun delete(id: String)
    /** Hábitos com o status de hoje (check-in, streak, progresso semanal). */
    fun todayStatusFlow(timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<List<HabitStatus>>
}

interface CheckinRepository {
    suspend fun get(habitId: String, dateYmd: String): Checkin?
    suspend fun toggle(habitId: String, dateYmd: String, quantity: Double = 1.0): Boolean // retorna done após toggle
    suspend fun setQuantity(habitId: String, dateYmd: String, quantity: Double)
    suspend fun remove(habitId: String, dateYmd: String)
    fun checkinsInRangeFlow(fromYmd: String, toYmd: String): Flow<List<Checkin>>
    suspend fun checkinsForHabit(habitId: String): List<Checkin>
}

interface TaskRepository {
    fun tasksForDateFlow(dateYmd: String): Flow<List<TaskItem>>
    suspend fun add(title: String, dateYmd: String, dueTime: String?)
    suspend fun setDone(id: String, done: Boolean)
    suspend fun delete(id: String)
}

interface DiaryRepository {
    fun entryForDateFlow(dateYmd: String): Flow<DiaryEntry?>
    suspend fun save(dateYmd: String, mood: Int?, note: String)
    fun recentFlow(limit: Int): Flow<List<DiaryEntry>>
}

interface ChallengeRepository {
    fun challengesFlow(): Flow<List<Challenge>>
    suspend fun start(id: String, startDateYmd: String)
    suspend fun setStatus(id: String, status: ChallengeStatus)
    /** Garante que os pacotes padrão existam (idempotente). */
    suspend fun ensureSeed()
}

/** Preferências simples: tema, status Pro, onboarding. */
interface PrefsRepository {
    fun darkThemeFlow(): Flow<Boolean>
    suspend fun setDarkTheme(dark: Boolean)
    fun isProFlow(): Flow<Boolean>
    suspend fun setPro(pro: Boolean)
    fun onboardingDoneFlow(): Flow<Boolean>
    suspend fun setOnboardingDone()
}
