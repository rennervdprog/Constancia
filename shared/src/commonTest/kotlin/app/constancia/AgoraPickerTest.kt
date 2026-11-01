package app.constancia

import app.constancia.model.Checkin
import app.constancia.model.CheckinStatus
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.HabitStatus
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.ui.home.AgoraTarget
import app.constancia.ui.home.pickAgoraTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cartão "Agora" da Hoje: a escolha do próximo item pendente segue a
 * sessão atual primeiro, pula feitos/pulados e deixa o diário por
 * último na Noite.
 */
class AgoraPickerTest {

    private fun habit(
        id: String,
        session: Session,
        reminderTime: String = "",
    ) = Habit(
        id = id,
        name = id,
        icon = "check",
        color = 0L,
        goalType = GoalType.BINARY,
        targetQuantity = 1.0,
        quantityUnit = "vezes",
        frequencyType = FrequencyType.DAILY,
        daysOfWeek = emptyList(),
        weeklyTarget = 3,
        session = session,
        reminderEnabled = reminderTime.isNotEmpty(),
        reminderTime = reminderTime,
        paused = false,
        createdAt = "",
        sortOrder = 0,
    )

    private fun status(
        habit: Habit,
        done: Boolean = false,
        skipped: Boolean = false,
    ) = HabitStatus(
        habit = habit,
        todayCheckin = if (skipped) {
            Checkin(
                id = "c-${habit.id}",
                habitId = habit.id,
                date = "2026-10-31",
                quantity = 0.0,
                status = CheckinStatus.SKIPPED,
            )
        } else {
            null
        },
        isDoneToday = done,
        currentStreak = 0,
        weekDone = 0,
    )

    private fun task(
        id: String,
        session: Session,
        done: Boolean = false,
        dueTime: String? = null,
    ) = TaskItem(
        id = id,
        title = id,
        dueTime = dueTime,
        done = done,
        date = "2026-10-31",
        session = session,
    )

    @Test
    fun sessaoAtualTemPrioridade() {
        val manha = status(habit("manha", Session.MANHA))
        val tarde = status(habit("tarde", Session.TARDE))
        val picked = pickAgoraTarget(
            habits = listOf(manha, tarde),
            tasks = emptyList(),
            diaryDone = true,
            current = Session.TARDE,
        )
        assertTrue(picked is AgoraTarget.HabitTarget)
        assertEquals("tarde", (picked as AgoraTarget.HabitTarget).status.habit.id)
    }

    @Test
    fun voltaParaSessoesAnterioresQuandoORestoFecha() {
        val noite = status(habit("noite", Session.NOITE))
        val picked = pickAgoraTarget(
            habits = listOf(noite),
            tasks = emptyList(),
            diaryDone = true,
            current = Session.MANHA,
        )
        assertTrue(picked is AgoraTarget.HabitTarget)
        assertEquals("noite", (picked as AgoraTarget.HabitTarget).status.habit.id)
    }

    @Test
    fun ignoraFeitosEPulados() {
        val feito = status(habit("feito", Session.MANHA), done = true)
        val pulado = status(habit("pulado", Session.MANHA), skipped = true)
        val tarefa = task("tarefa", Session.MANHA)
        val picked = pickAgoraTarget(
            habits = listOf(feito, pulado),
            tasks = listOf(tarefa),
            diaryDone = true,
            current = Session.MANHA,
        )
        assertTrue(picked is AgoraTarget.TaskTarget)
        assertEquals("tarefa", (picked as AgoraTarget.TaskTarget).task.id)
    }

    @Test
    fun dentroDaSessaoQuemTemHoraVemPrimeiro() {
        val semHora = task("sem-hora", Session.TARDE)
        val comHora = task("com-hora", Session.TARDE, dueTime = "15:00")
        val picked = pickAgoraTarget(
            habits = emptyList(),
            tasks = listOf(semHora, comHora),
            diaryDone = true,
            current = Session.TARDE,
        )
        assertTrue(picked is AgoraTarget.TaskTarget)
        assertEquals("com-hora", (picked as AgoraTarget.TaskTarget).task.id)
    }

    @Test
    fun diarioEUltimoRecursoDaNoiteENuloQuandoTudoFeito() {
        val picked = pickAgoraTarget(
            habits = emptyList(),
            tasks = emptyList(),
            diaryDone = false,
            current = Session.NOITE,
        )
        assertTrue(picked is AgoraTarget.DiaryTarget)
        assertNull(
            pickAgoraTarget(
                habits = emptyList(),
                tasks = emptyList(),
                diaryDone = true,
                current = Session.NOITE,
            ),
        )
    }
}
