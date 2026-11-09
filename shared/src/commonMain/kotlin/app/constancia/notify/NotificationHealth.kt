package app.constancia.notify

/**
 * Saúde das notificações no aparelho (2026-11-07, rodada "funcional de
 * verdade" pedida pelo Renner): até aqui TODA a fiação de lembretes
 * existia (alarmes, receivers, boot, permissão pedida na abertura),
 * mas o app não tinha NENHUM lugar onde o usuário visse se o aparelho
 * está deixando os alertas passarem. No Android 13+, negar a permissão
 * uma vez (ou o fabricante bloquear) fazia todo lembrete morrer em
 * silêncio — o usuário concluía que "o app não funciona", que é a
 * reclamação nº 2 da categoria nas lojas. Estas funções alimentam o
 * cartão "Notificações do aparelho" em Mais → Ajustes.
 */

/** O sistema está deixando o app MOSTRAR notificações agora? */
expect fun notificationsGranted(): Boolean

/** Alarmes de horário exato liberados? (Android 12+: sem a concessão,
 *  lembrete de tarefa cai para alarme inexato e pode atrasar minutos.
 *  Fora do Android, sempre true.) */
expect fun exactAlarmsGranted(): Boolean

/** A otimização de bateria está deixando os alarmes do app em paz?
 *  (M3, Fase 2: em aparelho real, o fabricante fechar o app em segundo
 *  plano é a causa nº 1 de lembrete que não chega — e o estado é
 *  legível, então o cartão mostra e oferece o atalho.) true = isento
 *  ("sem restrições"). Fora do Android, sempre true. */
expect fun batteryOptimizationExempt(): Boolean

/** Abre a tela do sistema para tirar o app da otimização de bateria
 *  (Android); fora do Android, não faz nada. */
expect fun openBatteryOptimizationSettings()

/** Abre a tela do sistema onde o usuário ativa as notificações do app. */
expect fun openNotificationSettings()

/** Abre a tela de acesso especial "Alarmes e lembretes" (Android);
 *  fora do Android, não faz nada. */
expect fun openExactAlarmSettings()

/** Posta NA HORA uma notificação de teste — a prova que o usuário vê
 *  com os próprios olhos na barra de notificações. */
expect fun postTestNotification()
