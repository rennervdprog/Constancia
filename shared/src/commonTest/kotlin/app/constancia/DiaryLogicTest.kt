package app.constancia

import app.constancia.domain.HeatCell
import app.constancia.domain.computeDiaryPattern
import app.constancia.domain.diaryDayLabel
import app.constancia.domain.diaryMatchesQuery
import app.constancia.domain.diaryStreak
import app.constancia.domain.heatGrid
import app.constancia.domain.promptFor
import app.constancia.domain.promptIndexFor
import app.constancia.domain.weekFractions
import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryLogicTest {

    // Quarta-feira, 21 de outubro de 2026.
    private val today = LocalDate(2026, 10, 21)

    private fun habit(id: String = "h1", name: String = "Treino") = Habit(
        id = id,
        name = name,
        icon = "gym",
        color = 1L,
        goalType = GoalType.BINARY,
        targetQuantity = 1.0,
        quantityUnit = "",
        frequencyType = FrequencyType.DAILY,
        daysOfWeek = emptyList(),
        weeklyTarget = 3,
        session = Session.MANHA,
        reminderEnabled = false,
        reminderTime = "",
        paused = false,
        createdAt = "",
        sortOrder = 0,
    )

    private fun entry(date: String, mood: Int?, note: String = "", tags: List<String> = emptyList()) =
        DiaryEntry(id = "e-$date", date = date, mood = mood, note = note, tags = tags)

    private fun checkin(habitId: String, date: String, qty: Double = 1.0) =
        Checkin(id = "c-$habitId-$date", habitId = habitId, date = date, quantity = qty)

    @Test
    fun promptRotatesByDayOfMonth() {
        assertEquals(0, promptIndexFor(LocalDate(2026, 10, 20))) // 20 % 5 = 0
        assertEquals(1, promptIndexFor(LocalDate(2026, 10, 21)))
        assertEquals(
            "Qual foi o melhor momento de hoje, por menor que seja?",
            promptFor(LocalDate(2026, 10, 20)),
        )
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val dates = setOf("2026-10-21", "2026-10-20", "2026-10-19", "2026-10-17")
        assertEquals(3, diaryStreak(dates, today))
        // Sem hoje: a sequência de ontem continua valendo.
        assertEquals(2, diaryStreak(setOf("2026-10-20", "2026-10-19"), today))
        // Buraco ontem quebra tudo.
        assertEquals(0, diaryStreak(setOf("2026-10-19"), today))
        assertEquals(0, diaryStreak(emptySet(), today))
    }

    @Test
    fun dayLabels() {
        assertEquals("Hoje", diaryDayLabel(today, today))
        assertEquals("Ontem", diaryDayLabel(LocalDate(2026, 10, 20), today))
        assertEquals("Segunda, 19 de out", diaryDayLabel(LocalDate(2026, 10, 19), today))
    }

    @Test
    fun searchMatchesNoteTagsAndDayLabel() {
        val e = entry("2026-10-19", 4, note = "Dia de praia com a família", tags = listOf("Praia"))
        assertTrue(diaryMatchesQuery(e, "praia", today))
        assertTrue(diaryMatchesQuery(e, "segunda", today))
        assertTrue(diaryMatchesQuery(e, "", today))
        assertTrue(!diaryMatchesQuery(e, "trabalho", today))
    }

    @Test
    fun patternComparesMoodWithAndWithoutHabit() {
        val treino = habit()
        val entries = listOf(
            entry("2026-10-12", 5), entry("2026-10-13", 5), entry("2026-10-14", 4),
            entry("2026-10-15", 2), entry("2026-10-16", 3), entry("2026-10-17", 2),
        )
        val checkins = listOf(
            checkin("h1", "2026-10-12"), checkin("h1", "2026-10-13"), checkin("h1", "2026-10-14"),
        )
        val pattern = computeDiaryPattern(entries, listOf(treino), checkins)
        assertNotNull(pattern)
        assertEquals("Treino", pattern?.habit?.name)
        assertEquals(14.0 / 3, pattern?.avgWith ?: 0.0, 0.001)
        assertEquals(7.0 / 3, pattern?.avgWithout ?: 0.0, 0.001)
        assertEquals("Seu humor sobe nos dias de treino", pattern?.headline)
    }

    @Test
    fun patternNeedsTwoEntriesPerGroup() {
        val treino = habit()
        val entries = listOf(
            entry("2026-10-12", 5), entry("2026-10-13", 3), entry("2026-10-14", 2),
        )
        val checkins = listOf(checkin("h1", "2026-10-12"))
        assertNull(computeDiaryPattern(entries, listOf(treino), checkins))
        assertNull(computeDiaryPattern(emptyList(), listOf(treino), checkins))
    }

    @Test
    fun weekFractionsMarkDonePendingAndFuture() {
        val h = habit()
        // Semana de 19 (seg) a 25 (dom) de outubro; hoje = quarta 21.
        val checkins = listOf(checkin("h1", "2026-10-19"), checkin("h1", "2026-10-20"))
        val fractions = weekFractions(listOf(h), checkins, today)
        assertEquals(7, fractions.size)
        assertEquals(1.0, fractions[0]) // segunda feita
        assertEquals(1.0, fractions[1]) // terça feita
        assertEquals(0.0, fractions[2]) // hoje pendente
        assertEquals(-1.0, fractions[3]) // futuro
        assertEquals(-1.0, fractions[6])
    }

    @Test
    fun heatGridHas18WeeksAndMarksTodayDone() {
        val h = habit()
        val checkins = listOf(checkin("h1", "2026-10-21"), checkin("h1", "2026-10-19"))
        val grid = heatGrid(h, checkins, today)
        assertEquals(18, grid.size)
        assertTrue(grid.all { it.size == 7 })
        // Última coluna = semana atual (dom 18 .. sáb 24): seg=índice 1, qua=índice 3.
        val lastWeek = grid.last()
        assertEquals(HeatCell.DONE, lastWeek[1]) // segunda 19 feita
        assertEquals(HeatCell.MISSED, lastWeek[2]) // terça 20 devida, não feita
        assertEquals(HeatCell.DONE, lastWeek[3]) // hoje feito
        assertEquals(HeatCell.FUTURE, lastWeek[4]) // quinta 22 é futuro
    }
}
