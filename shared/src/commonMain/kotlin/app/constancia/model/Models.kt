package app.constancia.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.random.Random

/** Sessão do dia em que o hábito aparece na tela Hoje. */
enum class Session { MANHA, TARDE, NOITE }

/** Tipo de meta: marcar feito (binário) ou atingir quantidade. */
enum class GoalType { BINARY, QUANTITY }

/** Frequência: todo dia, X vezes por semana, ou dias fixos da semana. */
enum class FrequencyType { DAILY, WEEKLY_X, DAYS_OF_WEEK }

data class Habit(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val goalType: GoalType,
    val targetQuantity: Double,
    val quantityUnit: String,
    val frequencyType: FrequencyType,
    val daysOfWeek: List<Int>, // 1=domingo .. 7=sábado; vazio = todos os dias
    val weeklyTarget: Int,
    val session: Session,
    val reminderEnabled: Boolean,
    val reminderTime: String,
    val paused: Boolean,
    val createdAt: String,
    val sortOrder: Int,
)

data class Checkin(
    val id: String,
    val habitId: String,
    val date: String, // yyyy-MM-dd
    val quantity: Double,
)

data class HabitStatus(
    val habit: Habit,
    val todayCheckin: Checkin?,
    val isDoneToday: Boolean,
    val currentStreak: Int,
    val weekDone: Int, // check-ins na semana atual (para WEEKLY_X)
)

data class TaskItem(
    val id: String,
    val title: String,
    val dueTime: String?, // HH:mm ou null
    val done: Boolean,
    val date: String, // yyyy-MM-dd
)

data class DiaryEntry(
    val id: String,
    val date: String, // yyyy-MM-dd
    val mood: Int?, // 1..5 ou null
    val note: String,
)

enum class ChallengeStatus { AVAILABLE, ACTIVE, DONE, GIVEN_UP }

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val durationDays: Int,
    val habitName: String,
    val habitIcon: String,
    val startDate: String?,
    val status: ChallengeStatus,
    val isPro: Boolean,
)

/** Gera um id único sem depender de java.util.UUID (minSdk 24, commonMain). */
fun newId(): String =
    "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong().toString(16)}"

fun todayYmd(timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    Clock.System.todayIn(timeZone).toYmd()

fun LocalDate.toYmd(): String {
    val m = monthNumber.toString().padStart(2, '0')
    val d = dayOfMonth.toString().padStart(2, '0')
    return "$year-$m-$d"
}

/** Converte "yyyy-MM-dd" em LocalDate sem String.format nem java.time. */
fun String.toLocalDate(): LocalDate {
    val parts = split("-")
    return LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

/** dayOfWeek.isoDayNumber: 1=segunda..7=domingo -> 1=domingo..7=sábado (padrão do app). */
fun isoToAppDay(isoDayNumber: Int): Int = if (isoDayNumber == 7) 1 else isoDayNumber + 1
