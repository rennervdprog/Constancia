package app.constancia.notify

/**
 * Agendador de notificações locais (itens 3 e 9 do backlog Todoist +
 * Fase C do Plano de Elevação):
 * - lembretes de TAREFA como LISTA (R6.1): cada linha da tabela
 *   task_reminder tem o próprio alarme, identificado pelo id da linha;
 * - lembretes de HÁBITO (absorvidos do TickTick §9.4): vários horários
 *   diários por hábito, identificados pelo id da linha habit_reminder;
 * - Daily Review em canal próprio (R12.4) e o resumo de lembretes
 *   perdidos do catch-up (R6.3).
 * Implementações por plataforma: Android com AlarmManager + receivers;
 * iOS com UNUserNotificationCenter.
 *
 * A classe é instanciada sem contexto: no Android, o contexto chega via
 * [initNotificationContext] (MainActivity/receivers); sem contexto, as
 * chamadas são ignoradas silenciosamente (ex.: testes JVM).
 */
expect class NotificationScheduler() {

    /** Agenda (ou reagenda) UM lembrete de tarefa no instante dado. */
    fun scheduleTaskReminder(reminderId: String, title: String, triggerAtMillis: Long)

    /** Cancela UM lembrete de tarefa pelo id da linha. */
    fun cancelTaskReminder(reminderId: String)

    /** Agenda o lembrete diário de um hábito (repete todo dia em HH:mm). */
    fun scheduleHabitReminder(reminderId: String, habitName: String, hour: Int, minute: Int)

    /** Cancela o lembrete diário de um hábito pelo id da linha. */
    fun cancelHabitReminder(reminderId: String)

    /** Posta na hora o resumo "Você perdeu N lembretes" (catch-up R6.3). */
    fun notifyMissedReminders(count: Int)

    /** Liga a revisão diária no horário dado (repete todo dia). */
    fun scheduleDailyReview(hour: Int, minute: Int)

    /** Desliga a revisão diária. */
    fun cancelDailyReview()
}
