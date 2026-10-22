package app.constancia.domain

/**
 * Modelo de edição dos campos de horário (reteste 2, 2026-10-21).
 *
 * A rodada anterior formatava o TEXTO a cada tecla (`maskTimeText`
 * sobre o estado String) e o campo desabava no uso real:
 *  - o cursor voltava ao fim a cada mudança de valor (estado String),
 *    então corrigir um dígito no meio era impossível;
 *  - a função recalculava os dígitos a partir do texto formatado:
 *    apagar o ":" não mudava nada (a tecla parecia engolida) e o
 *    "clamp" reescrevia dígitos digitados ("29" virava "23", "24:00"
 *    virava "23:00", minuto "61" virava "59") sem o usuário perceber.
 *
 * Modelo novo — a fonte da verdade é o BUFFER DE DÍGITOS (0–4 chars):
 *  - hora de UM dígito quando o primeiro é 3–9 ("9" = 09h; buffer
 *    máximo 3: H + MM); senão hora de DOIS dígitos (buffer máximo 4);
 *  - dígito digitado NUNCA é reescrito nem engolido: faixa inválida
 *    (hora 24+, minuto 60+) é problema de VALIDAÇÃO no uso, via
 *    [parseTimeOrNull] — como já era nos pontos de consumo;
 *  - o ":" é só apresentação ([timeDisplayText] +
 *    [timeOriginalToTransformed]/[timeTransformedToOriginal] para a
 *    VisualTransformation): backspace logo após o ":" apaga o dígito
 *    anterior, como em qualquer máscara bem-comportada.
 */

/** Dígitos de um texto qualquer, já normalizados ao tamanho do buffer. */
fun timeDigitsOf(text: String): String =
    normalizeTimeDigits(text.filter { it.isDigit() })

/** Recorta o buffer ao máximo conforme a regra da hora de um dígito. */
fun normalizeTimeDigits(digits: String): String =
    if (isSingleDigitHour(digits)) digits.take(3) else digits.take(4)

/** Hora de um dígito só: primeiro dígito 3–9 ("9" -> 09h). */
fun isSingleDigitHour(digits: String): Boolean =
    digits.isNotEmpty() && digits[0] in '3'..'9'

/** Texto exibido para um buffer de dígitos ("930" -> "09:30"). */
fun timeDisplayText(digits: String): String {
    if (digits.isEmpty()) return ""
    return if (isSingleDigitHour(digits)) {
        val hour = "0" + digits[0]
        if (digits.length == 1) hour else hour + ":" + digits.substring(1)
    } else {
        if (digits.length <= 2) {
            digits
        } else {
            digits.substring(0, 2) + ":" + digits.substring(2)
        }
    }
}

/** Quantos dígitos do buffer formam a hora (1 ou 2). */
private fun hourDigitCount(digits: String): Int =
    if (isSingleDigitHour(digits)) 1 else minOf(2, digits.length)

/** Quanto mede a hora no texto exibido (a hora de um dígito ganha o "0"). */
private fun hourDisplayLength(digits: String): Int =
    if (isSingleDigitHour(digits)) 2 else hourDigitCount(digits)

/**
 * Cursor: posição no buffer (dígitos antes do cursor) -> posição no
 * texto exibido. O cursor logo após a hora, havendo minutos, fica
 * DEPOIS do ":" (é onde a próxima tecla continua).
 */
fun timeOriginalToTransformed(digits: String, offset: Int): Int {
    val len = digits.length
    val o = offset.coerceIn(0, len)
    if (len == 0) return 0
    val hourDigits = hourDigitCount(digits)
    val hasColon = len > hourDigits
    return if (o <= hourDigits) {
        var base = if (isSingleDigitHour(digits)) {
            if (o == 0) 0 else hourDisplayLength(digits)
        } else {
            o
        }
        if (o == hourDigits && hasColon) base += 1
        base
    } else {
        hourDisplayLength(digits) + 1 + (o - hourDigits)
    }
}

/**
 * Cursor: posição no texto exibido -> posição no buffer. As duas
 * posições ao redor do ":" (antes e depois) valem "fim da hora" —
 * por isso backspace encostado no ":" apaga o último dígito da hora.
 */
fun timeTransformedToOriginal(digits: String, offset: Int): Int {
    val display = timeDisplayText(digits)
    val t = offset.coerceIn(0, display.length)
    if (digits.isEmpty()) return 0
    val hourDigits = hourDigitCount(digits)
    val hourLen = hourDisplayLength(digits)
    return if (t <= hourLen) {
        if (isSingleDigitHour(digits)) {
            if (t < hourLen) 0 else hourDigits
        } else {
            t
        }
    } else {
        hourDigits + (t - hourLen - 1).coerceAtLeast(0)
    }
}

/** Resultado de uma edição: novo buffer + cursor (em dígitos). */
data class TimeEdit(val digits: String, val cursor: Int)

/**
 * Processa o que a plataforma entrega no onValueChange (texto e
 * cursor em coordenadas do BUFFER — é assim que campos com
 * VisualTransformation trabalham): filtra não-dígitos que o teclado
 * possa ter enviado (ex.: ":"), normaliza o tamanho e posiciona o
 * cursor após o mesmo número de dígitos que havia antes dele.
 */
fun applyTimeEdit(newText: String, newCursor: Int): TimeEdit {
    val clamped = newCursor.coerceIn(0, newText.length)
    val digitsBefore = newText.take(clamped).count { it.isDigit() }
    val digits = normalizeTimeDigits(newText.filter { it.isDigit() })
    return TimeEdit(digits, minOf(digitsBefore, digits.length))
}
