package app.constancia

import app.constancia.domain.RepeatBase
import app.constancia.domain.RepeatFreq
import app.constancia.domain.RepeatRule
import app.constancia.domain.encodeRepeatSpec
import app.constancia.domain.legacyRepeatToSpec
import app.constancia.domain.nextOccurrence
import app.constancia.domain.nthWeekdayOfMonth
import app.constancia.domain.parseRepeatSpec
import app.constancia.domain.repeatLabel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Item 7 — recorrência estruturada: codificação, passos e rótulos. */
class RepeatRuleTest {

    private val oct20 = LocalDate(2026, 10, 20) // terça-feira

    @Test
    fun encodeParseRoundtrip() {
        val rules = listOf(
            RepeatRule(RepeatFreq.DAILY, 1),
            RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(1, 2, 3, 4, 5)),
            RepeatRule(RepeatFreq.WEEKLY, 2, weekdays = setOf(1, 3)),
            RepeatRule(RepeatFreq.MONTHLY, 1, monthDay = 31),
            RepeatRule(
                RepeatFreq.MONTHLY, 2, monthWeek = 2, monthWeekday = 1,
                base = RepeatBase.COMPLETION, endsOn = LocalDate(2027, 1, 31),
            ),
            RepeatRule(RepeatFreq.YEARLY, 1, monthDay = 29, month = 2),
        )
        for (rule in rules) {
            assertEquals(rule, parseRepeatSpec(encodeRepeatSpec(rule)))
        }
    }

    @Test
    fun parseRejectsGarbage() {
        assertNull(parseRepeatSpec(""))
        assertNull(parseRepeatSpec("xyz"))
        assertNull(parseRepeatSpec("v2|DAILY|1|-|-|-|-|-|DUE_DATE|-"))
        assertNull(parseRepeatSpec("v1|DAILY|0|-|-|-|-|-|DUE_DATE|-"))
    }

    @Test
    fun dailySteps() {
        assertEquals(
            LocalDate(2026, 10, 21),
            nextOccurrence(RepeatRule(RepeatFreq.DAILY, 1), oct20, oct20),
        )
        assertEquals(
            LocalDate(2026, 10, 23),
            nextOccurrence(RepeatRule(RepeatFreq.DAILY, 3), oct20, oct20),
        )
    }

    @Test
    fun weeklyWeekdaysSteps() {
        val segQua = RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(1, 3))
        assertEquals(
            LocalDate(2026, 10, 21), // terça -> quarta
            nextOccurrence(segQua, oct20, oct20),
        )
        assertEquals(
            LocalDate(2026, 10, 26), // quarta -> segunda
            nextOccurrence(segQua, LocalDate(2026, 10, 21), LocalDate(2026, 10, 21)),
        )
    }

    @Test
    fun biweeklyWeekdaysSkipWeek() {
        val rule = RepeatRule(RepeatFreq.WEEKLY, 2, weekdays = setOf(1, 3))
        val seg = LocalDate(2026, 10, 5)
        assertEquals(LocalDate(2026, 10, 7), nextOccurrence(rule, seg, seg))
        assertEquals(
            LocalDate(2026, 10, 19), // pula a semana do meio
            nextOccurrence(rule, LocalDate(2026, 10, 7), LocalDate(2026, 10, 7)),
        )
        assertEquals(
            LocalDate(2026, 10, 21),
            nextOccurrence(rule, LocalDate(2026, 10, 19), LocalDate(2026, 10, 19)),
        )
    }

    @Test
    fun monthlyDayClampsAndDoesNotDrift() {
        val rule = RepeatRule(RepeatFreq.MONTHLY, 1, monthDay = 31)
        assertEquals(
            LocalDate(2026, 2, 28),
            nextOccurrence(rule, LocalDate(2026, 1, 31), LocalDate(2026, 1, 31)),
        )
        assertEquals(
            LocalDate(2026, 3, 31), // volta ao 31 (sem deriva)
            nextOccurrence(rule, LocalDate(2026, 2, 28), LocalDate(2026, 2, 28)),
        )
    }

    @Test
    fun monthlyNthWeekday() {
        assertEquals(LocalDate(2026, 10, 13), nthWeekdayOfMonth(2026, 10, 2, 2))
        assertEquals(LocalDate(2026, 10, 30), nthWeekdayOfMonth(2026, 10, -1, 5))
        val segundas = RepeatRule(RepeatFreq.MONTHLY, 1, monthWeek = 2, monthWeekday = 2)
        assertEquals(
            LocalDate(2026, 11, 10),
            nextOccurrence(segundas, LocalDate(2026, 10, 13), LocalDate(2026, 10, 13)),
        )
        val ultimaSexta = RepeatRule(RepeatFreq.MONTHLY, 1, monthWeek = -1, monthWeekday = 5)
        assertEquals(
            LocalDate(2026, 11, 27),
            nextOccurrence(ultimaSexta, LocalDate(2026, 10, 30), LocalDate(2026, 10, 30)),
        )
    }

    @Test
    fun yearlyLeapDay() {
        val rule = RepeatRule(RepeatFreq.YEARLY, 1, monthDay = 29, month = 2)
        assertEquals(
            LocalDate(2025, 2, 28),
            nextOccurrence(rule, LocalDate(2024, 2, 29), LocalDate(2024, 2, 29)),
        )
        assertEquals(
            LocalDate(2028, 2, 29),
            nextOccurrence(rule, LocalDate(2027, 2, 28), LocalDate(2027, 2, 28)),
        )
    }

    @Test
    fun endsOnStopsSeries() {
        val rule = RepeatRule(RepeatFreq.DAILY, 1, endsOn = LocalDate(2026, 10, 21))
        assertEquals(LocalDate(2026, 10, 21), nextOccurrence(rule, oct20, oct20))
        assertNull(
            nextOccurrence(rule, LocalDate(2026, 10, 21), LocalDate(2026, 10, 21)),
        )
    }

    @Test
    fun completionBasePushesSeries() {
        val sexta = LocalDate(2026, 10, 23)
        val byCompletion = RepeatRule(RepeatFreq.WEEKLY, 1, base = RepeatBase.COMPLETION)
        assertEquals(LocalDate(2026, 10, 30), nextOccurrence(byCompletion, oct20, sexta))
        val byDue = RepeatRule(RepeatFreq.WEEKLY, 1, base = RepeatBase.DUE_DATE)
        assertEquals(LocalDate(2026, 10, 27), nextOccurrence(byDue, oct20, sexta))
    }

    @Test
    fun legacyConversion() {
        assertEquals(
            RepeatRule(RepeatFreq.MONTHLY, 1, monthDay = 31),
            legacyRepeatToSpec("Mensal", LocalDate(2026, 1, 31)),
        )
        assertEquals(
            RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(1, 2, 3, 4, 5)),
            legacyRepeatToSpec("Seg a Sex", oct20),
        )
        assertNull(legacyRepeatToSpec("", oct20))
        assertNull(legacyRepeatToSpec("Qualquer", oct20))
    }

    @Test
    fun labels() {
        assertEquals("Todo dia", repeatLabel(RepeatRule(RepeatFreq.DAILY, 1)))
        assertEquals("A cada 3 dias", repeatLabel(RepeatRule(RepeatFreq.DAILY, 3)))
        assertEquals(
            "Seg a Sex",
            repeatLabel(RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(1, 2, 3, 4, 5))),
        )
        assertEquals("Semanal", repeatLabel(RepeatRule(RepeatFreq.WEEKLY, 1)))
        assertEquals(
            "A cada 2 semanas",
            repeatLabel(RepeatRule(RepeatFreq.WEEKLY, 2)),
        )
        assertEquals(
            "Toda 2ª segunda do mês",
            repeatLabel(RepeatRule(RepeatFreq.MONTHLY, 1, monthWeek = 2, monthWeekday = 1)),
        )
        assertEquals(
            "Todo último sábado do mês",
            repeatLabel(RepeatRule(RepeatFreq.MONTHLY, 1, monthWeek = -1, monthWeekday = 6)),
        )
        assertEquals(
            "Todo dia 5 do mês",
            repeatLabel(RepeatRule(RepeatFreq.MONTHLY, 1, monthDay = 5)),
        )
        assertEquals("Anual", repeatLabel(RepeatRule(RepeatFreq.YEARLY, 1)))
    }
}
