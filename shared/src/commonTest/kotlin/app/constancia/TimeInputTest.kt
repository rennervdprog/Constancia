package app.constancia

import app.constancia.domain.applyTimeEdit
import app.constancia.domain.normalizeTimeDigits
import app.constancia.domain.parseTimeOrNull
import app.constancia.domain.timeDigitsOf
import app.constancia.domain.timeDisplayText
import app.constancia.domain.timeOriginalToTransformed
import app.constancia.domain.timeTransformedToOriginal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reteste 2 (2026-10-21) — bateria de SEQUÊNCIAS REAIS do campo de
 * hora sobre o modelo novo (buffer de dígitos + transformação visual,
 * `domain/TimeInput.kt`). Substitui os testes rasos da `maskTimeText`
 * removida: aqueles afirmavam justamente os comportamentos que o
 * Renner chamou de "toda bugada" (dígito reescrito por clamp, cursor
 * preso no fim, backspace engolido no ":").
 *
 * [FieldSession] simula a plataforma de verdade: a seleção vive nas
 * coordenadas do BUFFER; toque converte posição exibida -> buffer via
 * [timeTransformedToOriginal]; digitar/apagar entrega texto+cursor em
 * coordenadas do buffer, processados por [applyTimeEdit] — exatamente
 * o caminho do `TimeTextField`.
 */
class TimeInputTest {

    private class FieldSession {
        var digits: String = ""
            private set
        var cursor: Int = 0 // em dígitos (coordenadas do buffer)
            private set

        val display: String get() = timeDisplayText(digits)
        val displayCursor: Int get() = timeOriginalToTransformed(digits, cursor)

        private fun platformEdit(newText: String, newCursor: Int) {
            val e = applyTimeEdit(newText, newCursor)
            digits = e.digits
            cursor = e.cursor
        }

        /** Digita um caractere na posição do cursor (como o IME faz). */
        fun type(c: Char) {
            val nt = digits.substring(0, cursor) + c + digits.substring(cursor)
            platformEdit(nt, cursor + 1)
        }

        fun typeText(s: String) = s.forEach { type(it) }

        /** Backspace: apaga o dígito antes do cursor. */
        fun backspace() {
            if (cursor == 0) return
            platformEdit(digits.removeRange(cursor - 1, cursor), cursor - 1)
        }

        /** Toque numa posição do texto EXIBIDO (converte como a plataforma). */
        fun tapDisplay(pos: Int) {
            cursor = timeTransformedToOriginal(digits, pos)
        }

        /** Selecionar tudo + redigitar / colar: a plataforma troca o texto todo. */
        fun replaceAll(s: String) {
            platformEdit(s, s.length)
        }
    }

    // ---------- digitar dígito a dígito ----------

    @Test
    fun typing_twoDigitHour_digitByDigit() {
        val f = FieldSession()
        f.type('1'); assertEquals("1", f.display); assertEquals(1, f.displayCursor)
        f.type('2'); assertEquals("12", f.display); assertEquals(2, f.displayCursor)
        f.type('3'); assertEquals("12:3", f.display); assertEquals(4, f.displayCursor)
        f.type('4'); assertEquals("12:34", f.display); assertEquals(5, f.displayCursor)
        assertEquals(12, parseTimeOrNull(f.display)?.hour)
        assertEquals(34, parseTimeOrNull(f.display)?.minute)
    }

    @Test
    fun typing_singleDigitHour_digitByDigit() {
        val f = FieldSession()
        f.type('9'); assertEquals("09", f.display); assertEquals(2, f.displayCursor)
        f.type('3'); assertEquals("09:3", f.display); assertEquals(4, f.displayCursor)
        f.type('0'); assertEquals("09:30", f.display); assertEquals(5, f.displayCursor)
    }

    @Test
    fun typing_leadingZeroHour() {
        val f = FieldSession()
        f.typeText("0930")
        assertEquals("09:30", f.display)
    }

    @Test
    fun typing_extraDigitBeyondBuffer_isDroppedNotShifted() {
        val f = FieldSession()
        f.typeText("12345")
        assertEquals("1234", f.digits)
        assertEquals("12:34", f.display)
        assertEquals(4, f.cursor)
    }

    // ---------- dígitos NUNCA são reescritos (os bugs do clamp) ----------

    @Test
    fun typing_hour29_keepsTypedDigits() {
        val f = FieldSession()
        f.typeText("29")
        assertEquals("29", f.display)
        // Inválido é problema de validação no uso, não de reescrita:
        assertNull(parseTimeOrNull(f.display))
    }

    @Test
    fun typing_2400_keepsTypedDigits() {
        val f = FieldSession()
        f.typeText("2400")
        assertEquals("24:00", f.display)
        assertNull(parseTimeOrNull(f.display))
    }

    @Test
    fun typing_minute61_keepsTypedDigits() {
        val f = FieldSession()
        f.typeText("1261")
        assertEquals("12:61", f.display)
        assertNull(parseTimeOrNull(f.display))
    }

    @Test
    fun typing_valid2345_parses() {
        val f = FieldSession()
        f.typeText("2359")
        assertEquals("23:59", f.display)
        assertEquals(23, parseTimeOrNull(f.display)?.hour)
        assertEquals(59, parseTimeOrNull(f.display)?.minute)
    }

    // ---------- apagar: fim, meio e através do ":" ----------

    @Test
    fun backspace_fromEnd_undoesDigitByDigit() {
        val f = FieldSession()
        f.typeText("930")
        assertEquals("09:30", f.display)
        f.backspace(); assertEquals("09:3", f.display)
        f.backspace(); assertEquals("09", f.display)
        f.backspace(); assertEquals("", f.display)
        f.backspace(); assertEquals("", f.display) // vazio: sem travar
    }

    @Test
    fun backspace_rightAfterColon_deletesHourDigit() {
        // O bug clássico: cursor logo após o ":" e backspace parecia
        // engolido. Aqui apaga o dígito anterior ('2' de "12:34").
        val f = FieldSession()
        f.typeText("1234")
        f.tapDisplay(3) // logo após o ":"
        assertEquals(2, f.cursor)
        f.backspace()
        assertEquals("134", f.digits)
        assertEquals("13:4", f.display)
    }

    @Test
    fun backspace_rightBeforeColon_deletesHourDigit() {
        val f = FieldSession()
        f.typeText("1234")
        f.tapDisplay(2) // logo antes do ":"
        assertEquals(2, f.cursor)
        f.backspace()
        assertEquals("13:4", f.display)
    }

    @Test
    fun backspace_atStart_doesNothing() {
        val f = FieldSession()
        f.typeText("1234")
        f.tapDisplay(0)
        f.backspace()
        assertEquals("12:34", f.display)
    }

    // ---------- corrigir no meio ----------

    @Test
    fun correctMinuteDigit_inMiddle() {
        val f = FieldSession()
        f.typeText("1234") // 12:34 — trocar o '3' por '5' -> 12:54
        f.tapDisplay(4) // logo após o '3'
        assertEquals(3, f.cursor)
        f.backspace()
        assertEquals("124", f.digits)
        assertEquals("12:4", f.display)
        f.type('5')
        assertEquals("1254", f.digits)
        assertEquals("12:54", f.display)
        assertEquals(4, f.displayCursor)
    }

    @Test
    fun correctHourDigit_inMiddle() {
        val f = FieldSession()
        f.typeText("1234") // apagar o '1' inicial e digitar '0' -> 02:34
        f.tapDisplay(1)
        assertEquals(1, f.cursor)
        f.backspace()
        assertEquals("234", f.digits)
        assertEquals("23:4", f.display)
        f.tapDisplay(0)
        f.type('0')
        assertEquals("0234", f.digits)
        assertEquals("02:34", f.display)
    }

    // ---------- selecionar tudo, colar, caracteres estranhos ----------

    @Test
    fun selectAllAndRetype_replacesEverything() {
        val f = FieldSession()
        f.typeText("1234")
        f.replaceAll("8")
        assertEquals("08", f.display)
        f.typeText("45")
        assertEquals("845", f.digits)
        assertEquals("08:45", f.display)
    }

    @Test
    fun paste_formattedAndLooseText() {
        val f = FieldSession()
        f.replaceAll("14:30")
        assertEquals("1430", f.digits)
        assertEquals("14:30", f.display)
        f.replaceAll("9h30")
        assertEquals("930", f.digits)
        assertEquals("09:30", f.display)
        f.replaceAll("abc")
        assertEquals("", f.display)
    }

    @Test
    fun typingColonChar_isHarmlessNoOp() {
        val f = FieldSession()
        f.typeText("12")
        f.type(':')
        assertEquals("12", f.digits)
        assertEquals("12", f.display)
        assertEquals(2, f.cursor)
        f.typeText("30")
        assertEquals("12:30", f.display)
    }

    // ---------- extração/sincronização (valor externo -> buffer) ----------

    @Test
    fun digitsOf_extractsFromDisplayText() {
        assertEquals("", timeDigitsOf(""))
        assertEquals("0800", timeDigitsOf("08:00"))
        assertEquals("930", timeDigitsOf("9:30"))
        assertEquals("930", timeDigitsOf("09:30"))
        assertEquals("1234", timeDigitsOf("12:34"))
    }

    @Test
    fun normalize_capsByHourKind() {
        assertEquals("930", normalizeTimeDigits("9305")) // hora de 1 dígito: máx 3
        assertEquals("1234", normalizeTimeDigits("12345"))
        assertEquals("12", normalizeTimeDigits("12"))
        assertEquals("", normalizeTimeDigits(""))
    }

    // ---------- mapeamento de cursor: ida e volta estáveis ----------

    @Test
    fun offsetMapping_roundTripsForAllBuffers() {
        val alphabet = listOf('0', '1', '2', '3', '9')
        val buffers = mutableListOf("")
        var level = listOf("")
        repeat(4) {
            level = level.flatMap { prefix -> alphabet.map { prefix + it } }
            buffers += level
        }
        var checked = 0
        for (raw in buffers) {
            val digits = normalizeTimeDigits(raw)
            if (digits != raw) continue // só buffers já normalizados
            for (o in 0..digits.length) {
                val t = timeOriginalToTransformed(digits, o)
                assertEquals(
                    o,
                    timeTransformedToOriginal(digits, t),
                    "round-trip falhou: buffer=$digits offset=$o -> $t",
                )
                checked++
            }
            // transformedToOriginal nunca passa do tamanho do buffer
            val displayLen = timeDisplayText(digits).length
            for (t in 0..displayLen) {
                assertTrue(
                    timeTransformedToOriginal(digits, t) in 0..digits.length,
                    "t2o fora da faixa: buffer=$digits t=$t",
                )
            }
        }
        assertTrue(checked > 200, "cobertura do round-trip: $checked casos")
    }
}
