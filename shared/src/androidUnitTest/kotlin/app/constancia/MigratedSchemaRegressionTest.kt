package app.constancia

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.constancia.data.local.CheckinRepositoryImpl
import app.constancia.data.local.HabitRepositoryImpl
import app.constancia.db.ConstanciaDb
import app.constancia.domain.isDueOn
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regressão do reteste do Renner (2026-10-21, APK da Fase D): um hábito
 * diário criado no próprio dia NÃO aparecia na tela Hoje (herói
 * "Hábitos 0 de 0", sem a sessão Manhã), embora existisse na aba
 * Hábitos. Suspeita: nos bancos MIGRADOS (criados nas versões 1–5 e
 * atualizados até a v9), a ordem FÍSICA das colunas de `habit` difere
 * da ordem declarada no .sq atual — quantity_step / show_in_today /
 * show_in_calendar (6.sqm) e ignore_weekdays (8.sqm) entraram por
 * ALTER TABLE (anexadas no FIM da tabela), mas o .sq as declara no
 * MEIO. Se alguma leitura for posicional (`SELECT *`), os campos
 * voltam trocados. Este teste reconstrói a ordem física migrada,
 * salva e relê pelo repositório de verdade.
 */
class MigratedSchemaRegressionTest {

    private fun migratedDb(): ConstanciaDb {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ConstanciaDb.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=OFF", 0)
        // Ordem física real de um banco migrado: CREATE TABLE da v1
        // (17 colunas) + ALTERs da 6.sqm e da 8.sqm anexando no fim.
        driver.execute(
            null,
            "CREATE TABLE habit_migrated(" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "icon TEXT NOT NULL, " +
                "color INTEGER NOT NULL, " +
                "goal_type TEXT NOT NULL, " +
                "target_quantity REAL NOT NULL DEFAULT 1.0, " +
                "quantity_unit TEXT NOT NULL DEFAULT '', " +
                "frequency_type TEXT NOT NULL, " +
                "days_of_week TEXT NOT NULL DEFAULT '', " +
                "weekly_target INTEGER NOT NULL DEFAULT 7, " +
                "session TEXT NOT NULL, " +
                "reminder_enabled INTEGER NOT NULL DEFAULT 0, " +
                "reminder_time TEXT NOT NULL DEFAULT '', " +
                "paused INTEGER NOT NULL DEFAULT 0, " +
                "archived INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, " +
                "quantity_step REAL NOT NULL DEFAULT 1.0, " +
                "show_in_today INTEGER NOT NULL DEFAULT 0, " +
                "show_in_calendar INTEGER NOT NULL DEFAULT 0, " +
                "ignore_weekdays TEXT NOT NULL DEFAULT '')",
            0,
        )
        driver.execute(null, "DROP TABLE habit", 0)
        driver.execute(null, "ALTER TABLE habit_migrated RENAME TO habit", 0)
        return ConstanciaDb(driver)
    }

    @Test
    fun dailyHabitCreatedTodayIsDueAndReadsBackIntactOnMigratedDb() = runBlocking {
        val db = migratedDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        habits.save(
            Habit(
                id = "h-migrated",
                name = "Beber água",
                icon = "water",
                color = 0xFF0B7A55L,
                goalType = GoalType.BINARY,
                targetQuantity = 1.0,
                quantityUnit = "copos",
                frequencyType = FrequencyType.DAILY,
                daysOfWeek = emptyList(),
                weeklyTarget = 7,
                session = Session.MANHA,
                reminderEnabled = false,
                reminderTime = "",
                paused = false,
                createdAt = "",
                sortOrder = 0,
            ),
        )
        val read = habits.habitsFlow().first().single()
        assertEquals("Beber água", read.name)
        assertEquals("water", read.icon)
        assertEquals(0xFF0B7A55L, read.color)
        assertEquals(FrequencyType.DAILY, read.frequencyType)
        assertEquals(Session.MANHA, read.session)
        assertEquals(7, read.weeklyTarget)
        assertFalse(read.paused, "hábito novo não pode nascer pausado")
        assertTrue(
            read.ignoreWeekdays.isEmpty(),
            "dias de descanso devem vir vazios, veio: " + read.ignoreWeekdays,
        )
        val today = todayYmd()
        val created = runCatching { read.createdAt.take(10).toLocalDate() }
            .getOrNull()
        assertEquals(today, created?.toString(), "createdAt deve ser hoje")
        assertTrue(
            isDueOn(read, today.toLocalDate(), created),
            "hábito diário criado hoje é devido hoje (aparece na Hoje)",
        )
        // O fluxo de status da Hoje também o inclui, não-pausado.
        val status = habits.todayStatusFlow().first().single()
        assertFalse(status.habit.paused)
        assertEquals("h-migrated", status.habit.id)
    }
}
