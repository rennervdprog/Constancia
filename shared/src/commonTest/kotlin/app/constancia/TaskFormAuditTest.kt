package app.constancia

import app.constancia.domain.normalizedTimeOrNull
import app.constancia.domain.sessionForHour
import app.constancia.model.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Auditoria da tela de nova tarefa (2026-10-30): as regras puras por
 * trás das correções — validação de horário no salvamento e sessão
 * padrão pela hora do dia (formulário e captura rápida alinhados).
 */
class TaskFormAuditTest {

    @Test
    fun horarioValidoNormalizaParaHHmm() {
        assertEquals("09:30", normalizedTimeOrNull("9:30"))
        assertEquals("09:30", normalizedTimeOrNull("09:30"))
        assertEquals("00:00", normalizedTimeOrNull("0:00"))
        assertEquals("23:59", normalizedTimeOrNull("23:59"))
    }

    @Test
    fun horarioForaDaFaixaEhInvalido() {
        // O bug da auditoria: "29:61" salvava cru no formulário.
        assertNull(normalizedTimeOrNull("29:61"))
        assertNull(normalizedTimeOrNull("24:00"))
        assertNull(normalizedTimeOrNull("12:60"))
    }

    @Test
    fun horarioParcialOuVazioEhInvalido() {
        assertNull(normalizedTimeOrNull("14"))
        assertNull(normalizedTimeOrNull(""))
        assertNull(normalizedTimeOrNull(null))
        assertNull(normalizedTimeOrNull("ab:cd"))
    }

    @Test
    fun sessaoPelaHoraSegueAsFaixasDaHoje() {
        assertEquals(Session.MANHA, sessionForHour(0))
        assertEquals(Session.MANHA, sessionForHour(11))
        assertEquals(Session.TARDE, sessionForHour(12))
        assertEquals(Session.TARDE, sessionForHour(17))
        assertEquals(Session.NOITE, sessionForHour(18))
        assertEquals(Session.NOITE, sessionForHour(23))
    }
}
