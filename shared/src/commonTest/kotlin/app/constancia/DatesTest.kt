package app.constancia

import app.constancia.domain.DOW_SHORT
import app.constancia.domain.dateLinePt
import app.constancia.domain.greetingFor
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.isoToAppDay
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import kotlin.test.Test
import kotlin.test.assertEquals

class DatesTest {

    // ---------- Saudação ----------

    @Test
    fun greetingFor_morning() {
        assertEquals("Bom dia", greetingFor(0))
        assertEquals("Bom dia", greetingFor(6))
        assertEquals("Bom dia", greetingFor(11))
    }

    @Test
    fun greetingFor_afternoon() {
        assertEquals("Boa tarde", greetingFor(12))
        assertEquals("Boa tarde", greetingFor(15))
        assertEquals("Boa tarde", greetingFor(17))
    }

    @Test
    fun greetingFor_night() {
        assertEquals("Boa noite", greetingFor(18))
        assertEquals("Boa noite", greetingFor(21))
        assertEquals("Boa noite", greetingFor(23))
    }

    // ---------- Linha de data PT-BR ----------

    @Test
    fun dateLinePt_formatsSunday() {
        // 18/10/2026 é domingo.
        assertEquals("domingo, 18 de outubro", dateLinePt("2026-10-18".toLocalDate()))
    }

    @Test
    fun dateLinePt_formatsWeekday() {
        // 21/10/2026 é quarta-feira.
        assertEquals("quarta-feira, 21 de outubro", dateLinePt("2026-10-21".toLocalDate()))
    }

    // ---------- Convenção de dias ----------

    @Test
    fun isoDayNumberCompat_knownDates() {
        assertEquals(7, "2026-10-18".toLocalDate().isoDayNumberCompat()) // domingo
        assertEquals(1, "2026-10-19".toLocalDate().isoDayNumberCompat()) // segunda
        assertEquals(4, "1970-01-01".toLocalDate().isoDayNumberCompat()) // quinta (epoch)
    }

    @Test
    fun isoToAppDay_sundayFirst() {
        // App usa 1=domingo .. 7=sábado.
        assertEquals(1, isoToAppDay(7)) // dom ISO -> 1
        assertEquals(2, isoToAppDay(1)) // seg ISO -> 2
        assertEquals(7, isoToAppDay(6)) // sáb ISO -> 7
    }

    @Test
    fun dowShort_startsOnSunday() {
        assertEquals(listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"), DOW_SHORT)
    }

    // ---------- Roundtrip ----------

    @Test
    fun ymdRoundtrip() {
        val d = "2026-10-18".toLocalDate()
        assertEquals("2026-10-18", d.toYmd())
        assertEquals(2026, d.year)
        assertEquals(10, d.monthNumber)
        assertEquals(18, d.dayOfMonth)
    }
}
