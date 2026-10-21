package app.constancia

import app.constancia.domain.isDueOn
import app.constancia.domain.maskTimeText
import app.constancia.domain.parseTimeOrNull
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.habitActiveFrom
import app.constancia.model.normalizeHabitColor
import app.constancia.model.toLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reteste do Renner no APK da Fase D (2026-10-21): regressões e
 * caracterizações dos 5 problemas corrigidos nesta rodada.
 */
class RetestFixesTest {

    private val sp = TimeZone.of("America/Sao_Paulo")

    private fun habit(
        createdAt: String = "2026-10-21",
        ignoreWeekdays: Set<Int> = emptySet(),
    ) = Habit(
        id = "h1",
        name = "beber agua",
        icon = "water",
        color = 0xFF0B7A55L,
        goalType = GoalType.BINARY,
        targetQuantity = 1.0,
        quantityUnit = "",
        frequencyType = FrequencyType.DAILY,
        daysOfWeek = emptyList(),
        weeklyTarget = 7,
        session = Session.MANHA,
        reminderEnabled = false,
        reminderTime = "",
        paused = false,
        createdAt = createdAt,
        sortOrder = 0,
        ignoreWeekdays = ignoreWeekdays,
    )

    // ---- Bug nº 1: dia ativo do hábito é o dia LOCAL da criação ----

    @Test
    fun activeFrom_usesLocalDateNotUtcPrefix() {
        // 22/10 01:30 UTC = 21/10 22:30 em São Paulo: o hábito foi
        // criado no dia 21 (local) — pelo prefixo UTC ele só ficaria
        // devido no dia 22 e sumiria da Hoje no dia da criação.
        assertEquals(
            LocalDate(2026, 10, 21),
            habitActiveFrom("2026-10-22T01:30:00Z", sp),
        )
        assertEquals(
            LocalDate(2026, 10, 21),
            habitActiveFrom("2026-10-21T20:23:45Z", sp),
        )
    }

    @Test
    fun activeFrom_acceptsLegacyDateOnlyAndBlank() {
        assertEquals(LocalDate(2026, 10, 21), habitActiveFrom("2026-10-21", sp))
        assertNull(habitActiveFrom("", sp))
    }

    @Test
    fun habitCreatedAtNight_isDueOnCreationDay() {
        val h = habit(createdAt = "2026-10-22T01:30:00Z")
        val wednesday = "2026-10-21".toLocalDate()
        assertTrue(isDueOn(h, wednesday, habitActiveFrom(h.createdAt, sp)))
    }

    @Test
    fun restDay_habitIsNotDueThatWeekdayOnly() {
        // Caracterização do mecanismo por trás do sintoma: com folga
        // na quarta (ISO 3), o hábito diário não é devido na quarta —
        // mas volta na quinta. Sem a folga visível/editável (bugs nº 1
        // e nº 4), isso parecia "o hábito sumiu".
        val h = habit(ignoreWeekdays = setOf(3))
        assertFalse(isDueOn(h, "2026-10-21".toLocalDate(), null)) // quarta
        assertTrue(isDueOn(h, "2026-10-22".toLocalDate(), null)) // quinta
        assertTrue(isDueOn(h, "2026-10-20".toLocalDate(), null)) // terça
    }

    // ---- Bug nº 2: cor do hábito (ULong empacotado × ARGB) ----

    @Test
    fun normalizeHabitColor_unpacksLegacyComposeValue() {
        val packed = 0xFF0B7A55L shl 32 // Color(0xFF0B7A55).value.toLong()
        assertEquals(0xFF0B7A55L, normalizeHabitColor(packed))
    }

    @Test
    fun normalizeHabitColor_keepsCanonicalValues() {
        assertEquals(0xFF0B7A55L, normalizeHabitColor(0xFF0B7A55L))
        assertEquals(0L, normalizeHabitColor(0L))
        assertEquals(123L, normalizeHabitColor(123L))
    }

    // ---- Bug nº 3: máscara automática de horário ----

    @Test
    fun maskTimeText_formatsWhileTyping() {
        assertEquals("", maskTimeText(""))
        assertEquals("2", maskTimeText("2"))
        assertEquals("09", maskTimeText("9"))
        assertEquals("09:3", maskTimeText("93"))
        assertEquals("09:30", maskTimeText("930"))
        assertEquals("09:30", maskTimeText("0930"))
        assertEquals("12:34", maskTimeText("12:34"))
        assertEquals("12:34", maskTimeText("12345")) // dígito extra cai
        assertEquals("", maskTimeText("abc"))
    }

    @Test
    fun maskTimeText_clampsRanges() {
        assertEquals("23", maskTimeText("29"))
        assertEquals("23:59", maskTimeText("2361"))
        assertEquals("09:59", maskTimeText("999"))
    }

    @Test
    fun maskTimeText_outputParses() {
        val parsed = parseTimeOrNull(maskTimeText("930"))
        assertEquals(9, parsed?.hour)
        assertEquals(30, parsed?.minute)
    }
}
