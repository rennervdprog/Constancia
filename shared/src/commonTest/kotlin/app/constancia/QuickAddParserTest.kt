package app.constancia

import app.constancia.domain.QuickSpanKind
import app.constancia.domain.RepeatFreq
import app.constancia.domain.RepeatRule
import app.constancia.domain.parseQuickAdd
import app.constancia.model.TaskProject
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Item 6 — parser de linguagem natural PT: reconhecimento, título limpo e desfazer. */
class QuickAddParserTest {

    private val today = LocalDate(2026, 10, 20) // terça-feira

    @Test
    fun fullSentence() {
        val parse = parseQuickAdd(
            "Pagar boleto amanhã às 15h p1 #Casa todo mês", today,
        )
        assertEquals("Pagar boleto", parse.title)
        assertEquals(LocalDate(2026, 10, 21), parse.dueDate)
        assertEquals("15:00", parse.dueTime)
        assertEquals(1, parse.priority)
        assertEquals(TaskProject.CASA, parse.project)
        assertEquals(RepeatFreq.MONTHLY, parse.repeat?.freq)
        assertEquals(21, parse.repeat?.monthDay) // herda o dia da data
        assertEquals(5, parse.spans.size)
        assertEquals(
            "amanhã",
            parse.spans.first { it.kind == QuickSpanKind.DATE }.text,
        )
        assertEquals(
            "às 15h",
            parse.spans.first { it.kind == QuickSpanKind.TIME }.text,
        )
    }

    @Test
    fun relativeDates() {
        assertEquals(today, parseQuickAdd("Comprar pão hoje", today).dueDate)
        assertEquals(
            LocalDate(2026, 10, 22),
            parseQuickAdd("Jantar depois de amanhã", today).dueDate,
        )
        assertEquals(
            LocalDate(2026, 10, 27),
            parseQuickAdd("Revisar semana que vem", today).dueDate,
        )
        assertEquals(
            LocalDate(2026, 10, 24),
            parseQuickAdd("Corrida no fim de semana", today).dueDate,
        )
        assertEquals("Corrida", parseQuickAdd("Corrida no fim de semana", today).title)
    }

    @Test
    fun weekdayDates() {
        assertEquals(
            LocalDate(2026, 10, 23),
            parseQuickAdd("Ligar para o dentista sexta", today).dueDate,
        )
        assertEquals(
            "Ligar para o dentista",
            parseQuickAdd("Ligar para o dentista sexta", today).title,
        )
        assertEquals( // de terça, a próxima segunda já é "que vem"
            LocalDate(2026, 10, 26),
            parseQuickAdd("Reunião segunda que vem", today).dueDate,
        )
        assertEquals( // ditado na segunda, "que vem" pula uma semana
            LocalDate(2026, 10, 26),
            parseQuickAdd("Reunião segunda que vem", LocalDate(2026, 10, 19)).dueDate,
        )
        assertEquals( // dia da semana hoje conta como hoje
            LocalDate(2026, 10, 20),
            parseQuickAdd("Reunião terça", today).dueDate,
        )
    }

    @Test
    fun dayOfMonthAndExplicitDates() {
        assertEquals(
            LocalDate(2026, 10, 25),
            parseQuickAdd("Estudar dia 25", today).dueDate,
        )
        assertEquals( // dia já passou no mês -> mês que vem
            LocalDate(2026, 11, 5),
            parseQuickAdd("Pagar aluguel dia 5", today).dueDate,
        )
        assertEquals(
            LocalDate(2027, 3, 15),
            parseQuickAdd("Aniversário 15 de março", today).dueDate,
        )
        assertEquals(
            LocalDate(2026, 11, 5),
            parseQuickAdd("Consulta 05/11", today).dueDate,
        )
        assertEquals( // data já passou no ano -> ano que vem
            LocalDate(2027, 1, 5),
            parseQuickAdd("Consulta 05/01", today).dueDate,
        )
        assertEquals(
            LocalDate(2028, 2, 29),
            parseQuickAdd("Revisão 29/02/2028", today).dueDate,
        )
    }

    @Test
    fun times() {
        assertEquals("06:30", parseQuickAdd("Meditar às 6h30", today).dueTime)
        assertEquals("Meditar", parseQuickAdd("Meditar às 6h30", today).title)
        assertEquals("15:45", parseQuickAdd("Alongar 15:45", today).dueTime)
        assertEquals("18:00", parseQuickAdd("Enviar às 18h", today).dueTime)
        assertEquals("12:00", parseQuickAdd("Almoço meio-dia", today).dueTime)
        assertNull(parseQuickAdd("Voo 1234 amanhã", today).dueTime)
    }

    @Test
    fun priorityAndProject() {
        val parse = parseQuickAdd("Revisar p3 #Trabalho", today)
        assertEquals(3, parse.priority)
        assertEquals(TaskProject.TRABALHO, parse.project)
        assertEquals("Revisar", parse.title)
        assertEquals(
            TaskProject.SAUDE,
            parseQuickAdd("Sem pressa #Saúde", today).project,
        )
        assertNull(parseQuickAdd("Algo #Inexistente", today).project)
    }

    @Test
    fun repeats() {
        assertEquals(
            RepeatRule(RepeatFreq.DAILY, 1),
            parseQuickAdd("Beber água todo dia", today).repeat,
        )
        assertEquals(
            RepeatRule(RepeatFreq.DAILY, 2),
            parseQuickAdd("Beber água a cada 2 dias", today).repeat,
        )
        assertEquals(
            RepeatRule(RepeatFreq.WEEKLY, 2),
            parseQuickAdd("Revisão a cada 2 semanas", today).repeat,
        )
        assertEquals(
            RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(1, 2, 3, 4, 5)),
            parseQuickAdd("Trabalho seg a sex", today).repeat,
        )
        assertEquals(
            RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(5)),
            parseQuickAdd("Enviar relatório toda sexta", today).repeat,
        )
        assertEquals( // recorrência semanal NÃO rouba data
            null,
            parseQuickAdd("Enviar relatório toda sexta", today).dueDate,
        )
    }

    @Test
    fun monthlyInheritsDayFromDate() {
        val parse = parseQuickAdd("Pagar aluguel dia 5 todo mês", today)
        assertEquals(LocalDate(2026, 11, 5), parse.dueDate)
        assertEquals(5, parse.repeat?.monthDay)
    }

    @Test
    fun undoSpanRestoresText() {
        val text = "Comprar pão amanhã"
        val first = parseQuickAdd(text, today)
        val key = first.spans.first { it.kind == QuickSpanKind.DATE }.key
        val undone = parseQuickAdd(text, today, ignored = setOf(key))
        assertNull(undone.dueDate)
        assertEquals("Comprar pão amanhã", undone.title)
        assertEquals(0, undone.spans.size)
    }

    @Test
    fun plainTextUntouched() {
        val parse = parseQuickAdd("Ler 20 páginas", today)
        assertEquals(0, parse.spans.size)
        assertEquals("Ler 20 páginas", parse.title)
        assertNull(parse.dueDate)
        assertNull(parse.repeat)
    }

    // Fase A — R3.2: escape literal com crases ---------------------------
    @Test
    fun backtickSpanIsNeverParsed() {
        val parse = parseQuickAdd("Pagar `amanhã` p1", today)
        assertNull(parse.dueDate) // "amanhã" entre crases é texto literal
        assertEquals(1, parse.priority) // fora das crases, o parse é normal
        assertEquals(1, parse.spans.size)
        // As crases somem do título; o conteúdo continua lá, literal.
        assertEquals("Pagar amanhã", parse.title)

        val soCrases = parseQuickAdd("`sexta` comprar pão", today)
        assertNull(soCrases.dueDate)
        assertEquals(0, soCrases.spans.size)
        assertEquals("sexta comprar pão", soCrases.title)
    }

    // Fase C — R3.3: "!30m" (lembrete) e "por 30min" (duração) ----------
    @Test
    fun bangReminderIsParsed() {
        val parse = parseQuickAdd("Ligar para o banco amanhã às 10h !30m", today)
        assertEquals(30, parse.reminderMinutes)
        assertEquals("Ligar para o banco", parse.title)
        assertEquals(
            "!30m",
            parse.spans.first { it.kind == QuickSpanKind.REMINDER }.text,
        )
        assertEquals(
            60, parseQuickAdd("Reunião !1h", today).reminderMinutes,
        )
        assertEquals(
            45, parseQuickAdd("Reunião !45min", today).reminderMinutes,
        )
        assertEquals(
            15, parseQuickAdd("Reunião !15", today).reminderMinutes,
        )
        assertNull(parseQuickAdd("Tudo certo!", today).reminderMinutes)
    }

    @Test
    fun porDurationIsParsed() {
        val parse = parseQuickAdd("Estudar por 30min", today)
        assertEquals(30, parse.durationMinutes)
        assertEquals("Estudar", parse.title)
        assertEquals(
            "por 30min",
            parse.spans.first { it.kind == QuickSpanKind.DURATION }.text,
        )
        assertEquals(
            90, parseQuickAdd("Treinar por 90 min", today).durationMinutes,
        )
    }
}
