package app.constancia.domain

import app.constancia.model.isoDayNumberCompat
import kotlinx.datetime.LocalDate

// Convenção do app: semana começa no domingo (1=domingo .. 7=sábado).
val DOW_SHORT = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
val DOW_FULL =
    listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")
val MONTHS =
    listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

// Nomes por extenso, índice ISO (1=segunda .. 7=domingo).
val WEEKDAYS_ISO_PT = listOf(
    "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira",
    "sexta-feira", "sábado", "domingo",
)
val MONTHS_PT = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

/** Saudação por período do dia ("Bom dia" | "Boa tarde" | "Boa noite"). */
fun greetingFor(hour: Int): String = when {
    hour < 12 -> "Bom dia"
    hour < 18 -> "Boa tarde"
    else -> "Boa noite"
}

/** Linha de data em PT-BR: "domingo, 18 de outubro". */
fun dateLinePt(date: LocalDate): String =
    "${WEEKDAYS_ISO_PT[date.isoDayNumberCompat() - 1]}, " +
        "${date.dayOfMonth} de ${MONTHS_PT[date.monthNumber - 1]}"
