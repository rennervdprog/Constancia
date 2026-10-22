package app.constancia

import app.constancia.domain.maskTimeText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * REPRODUÇÃO (reteste 2, 2026-10-21): o Renner testou no aparelho real
 * e achou a máscara de hora "toda bugada", embora o teste raso da
 * rodada anterior (digitar 930 -> "09:30") passasse. Este arquivo
 * simula FIELMENTE como o app usa a máscara hoje — estado String (o
 * cursor volta ao fim a cada mudança de valor), onValueChange =
 * maskTimeText(textoNovo) — e afirma o comportamento DESEJADO nas
 * sequências reais de edição. Os testes marcados [BUG] FALHAM contra
 * a implementação atual: são a reprodução numérica do problema.
 */
class TimeMaskReproTest {

    /** Campo como o app o monta hoje: String state + maskTimeText. */
    private class OldField {
        var text: String = ""
            private set

        /** Digita no fim (cursor real pós-reformatação fica no fim). */
        fun type(s: String) {
            for (c in s) text = maskTimeText(text + c)
        }

        /** Apaga o último caractere (backspace com cursor no fim). */
        fun backspace() {
            if (text.isNotEmpty()) text = maskTimeText(text.dropLast(1))
        }

        /** Toque no meio + digitar: a plataforma insere na posição do toque. */
        fun insertAt(displayPos: Int, s: String) {
            text = maskTimeText(
                text.substring(0, displayPos) + s + text.substring(displayPos),
            )
        }

        /** Backspace com o cursor logo após a posição [displayPos] (apaga o char anterior). */
        fun deleteAt(displayPos: Int) {
            text = maskTimeText(text.removeRange(displayPos, displayPos + 1))
        }
    }

    // ---------- controles: passam hoje e têm que continuar passando ----------

    @Test
    fun control_typing930Formats() {
        val f = OldField()
        f.type("930")
        assertEquals("09:30", f.text)
    }

    @Test
    fun control_typing1234Formats() {
        val f = OldField()
        f.type("1234")
        assertEquals("12:34", f.text)
    }

    @Test
    fun control_backspaceFromEndUndoesDigitByDigit() {
        val f = OldField()
        f.type("930")
        f.backspace(); assertEquals("09:3", f.text)
        f.backspace(); assertEquals("09", f.text)
        f.backspace(); assertEquals("0", f.text)
        f.backspace(); assertEquals("", f.text)
    }

    // ---------- [BUG] sequências reais que a implementação atual quebra ----------

    @Test
    fun bug_typingHour29_rewritesTypedDigit() {
        // Usuário digita "29" (depois corrigiria ou seria validado no
        // salvar). A máscara reescreve o 9 digitado virando "23" — o
        // dígito muda sem o usuário tocar nele.
        val f = OldField()
        f.type("29")
        assertEquals("29", f.text)
    }

    @Test
    fun bug_typing2400_silentlyBecomes2300() {
        val f = OldField()
        f.type("2400")
        assertEquals("24:00", f.text)
    }

    @Test
    fun bug_typing2530_silentlyBecomes2330() {
        val f = OldField()
        f.type("2530")
        assertEquals("25:30", f.text)
    }

    @Test
    fun bug_typingMinute61_rewritesTo59() {
        val f = OldField()
        f.type("1261")
        assertEquals("12:61", f.text)
    }

    @Test
    fun bug_backspaceRightAfterColon_deletesPreviousDigit() {
        // Cursor logo após o ":" de "12:34" (posição 3) e backspace:
        // o esperado é apagar o dígito anterior ('2'), virando "13:4".
        // Na implementação atual o mask reaplica sobre "1234" e o
        // campo NÃO muda — a tecla parece engolida e a edição trava.
        val f = OldField()
        f.type("1234")
        assertEquals("12:34", f.text)
        f.deleteAt(2) // apaga o ':' na posição 2
        assertEquals("13:4", f.text)
    }
}
