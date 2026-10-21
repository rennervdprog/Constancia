package app.constancia.notify

/**
 * Agendador de notificações locais (itens 3 e 9 do backlog Todoist):
 * lembretes de tarefa com alarme (exato quando o sistema permite) e a
 * notificação diária do Daily Review. Implementações por plataforma:
 * Android com AlarmManager + receivers; iOS com UNUserNotificationCenter.
 *
 * A classe é instanciada sem contexto: no Android, o contexto chega via
 * [initNotificationContext] (MainActivity/receivers); sem contexto, as
 * chamadas são ignoradas silenciosamente (ex.: testes JVM).
 */
expect class NotificationScheduler() {

    /** Agenda (ou reagenda) o lembrete de uma tarefa no instante dado. */
    fun scheduleTaskReminder(taskId: String, title: String, triggerAtMillis: Long)

    /** Cancela o lembrete pendente de uma tarefa. */
    fun cancelTaskReminder(taskId: String)

    /** Liga a revisão diária no horário dado (repete todo dia). */
    fun scheduleDailyReview(hour: Int, minute: Int)

    /** Desliga a revisão diária. */
    fun cancelDailyReview()
}
