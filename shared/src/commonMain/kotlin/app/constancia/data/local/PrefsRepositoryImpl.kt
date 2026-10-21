package app.constancia.data.local

import app.constancia.data.PrefsRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Preferências via multiplatform-settings (no-arg). Os Flows são
 * MutableStateFlow com cache — o Settings puro não é observável, então
 * espelhamos cada chave num StateFlow atualizado nos setters.
 */
class PrefsRepositoryImpl : PrefsRepository {

    private val settings: Settings = Settings()

    private val darkThemeState =
        MutableStateFlow(settings.getBoolean(KEY_DARK_THEME, false))
    private val isProState =
        MutableStateFlow(settings.getBoolean(KEY_IS_PRO, false))
    private val onboardingDoneState =
        MutableStateFlow(settings.getBoolean(KEY_ONBOARDING_DONE, false))
    private val taskDailyGoalState =
        MutableStateFlow(settings.getInt(KEY_TASK_DAILY_GOAL, 0))
    private val taskWeeklyGoalState =
        MutableStateFlow(settings.getInt(KEY_TASK_WEEKLY_GOAL, 0))
    private val dailyReviewEnabledState =
        MutableStateFlow(settings.getBoolean(KEY_DAILY_REVIEW_ENABLED, false))
    private val dailyReviewTimeState =
        MutableStateFlow(settings.getString(KEY_DAILY_REVIEW_TIME, "08:00"))
    private val autoReminderMinutesState =
        MutableStateFlow(
            settings.getInt(KEY_AUTO_REMINDER_MINUTES, AUTO_REMINDER_OFF)
                .takeIf { it >= 0 },
        )
    private val goalIgnoreWeekdaysState =
        MutableStateFlow(
            settings.getString(KEY_GOAL_IGNORE_WEEKDAYS, "").toIsoDaySet(),
        )
    private val vacationModeState =
        MutableStateFlow(settings.getBoolean(KEY_VACATION_MODE, false))
    private val vacationHabitIdsState =
        MutableStateFlow(
            settings.getString(KEY_VACATION_HABIT_IDS, "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .toSet(),
        )
    private val vacationGoalPauseOpenedState =
        MutableStateFlow(
            settings.getBoolean(KEY_VACATION_GOAL_PAUSE_OPENED, false),
        )

    override fun darkThemeFlow(): Flow<Boolean> = darkThemeState

    override suspend fun setDarkTheme(dark: Boolean) {
        settings.putBoolean(KEY_DARK_THEME, dark)
        darkThemeState.value = dark
    }

    override fun isProFlow(): Flow<Boolean> = isProState

    override suspend fun setPro(pro: Boolean) {
        settings.putBoolean(KEY_IS_PRO, pro)
        isProState.value = pro
    }

    override fun onboardingDoneFlow(): Flow<Boolean> = onboardingDoneState

    override suspend fun setOnboardingDone() {
        settings.putBoolean(KEY_ONBOARDING_DONE, true)
        onboardingDoneState.value = true
    }

    override fun taskDailyGoalFlow(): Flow<Int> = taskDailyGoalState

    override suspend fun setTaskDailyGoal(goal: Int) {
        settings.putInt(KEY_TASK_DAILY_GOAL, goal)
        taskDailyGoalState.value = goal
    }

    override fun taskWeeklyGoalFlow(): Flow<Int> = taskWeeklyGoalState

    override suspend fun setTaskWeeklyGoal(goal: Int) {
        settings.putInt(KEY_TASK_WEEKLY_GOAL, goal)
        taskWeeklyGoalState.value = goal
    }

    override fun dailyReviewEnabledFlow(): Flow<Boolean> = dailyReviewEnabledState

    override suspend fun setDailyReviewEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DAILY_REVIEW_ENABLED, enabled)
        dailyReviewEnabledState.value = enabled
    }

    override fun dailyReviewTimeFlow(): Flow<String> = dailyReviewTimeState

    override suspend fun setDailyReviewTime(time: String) {
        settings.putString(KEY_DAILY_REVIEW_TIME, time)
        dailyReviewTimeState.value = time
    }

    override fun autoReminderMinutesFlow(): Flow<Int?> =
        autoReminderMinutesState

    override suspend fun setAutoReminderMinutes(minutes: Int?) {
        settings.putInt(
            KEY_AUTO_REMINDER_MINUTES, minutes ?: AUTO_REMINDER_OFF,
        )
        autoReminderMinutesState.value = minutes
    }

    override fun goalIgnoreWeekdaysFlow(): Flow<Set<Int>> =
        goalIgnoreWeekdaysState

    override suspend fun setGoalIgnoreWeekdays(days: Set<Int>) {
        val clean = days.filter { it in 1..7 }.toSet()
        settings.putString(
            KEY_GOAL_IGNORE_WEEKDAYS, clean.sorted().joinToString(","),
        )
        goalIgnoreWeekdaysState.value = clean
    }

    override fun vacationModeFlow(): Flow<Boolean> = vacationModeState

    override suspend fun setVacationMode(enabled: Boolean) {
        settings.putBoolean(KEY_VACATION_MODE, enabled)
        vacationModeState.value = enabled
    }

    override fun vacationHabitIdsFlow(): Flow<Set<String>> =
        vacationHabitIdsState

    override suspend fun setVacationHabitIds(ids: Set<String>) {
        settings.putString(KEY_VACATION_HABIT_IDS, ids.joinToString(","))
        vacationHabitIdsState.value = ids
    }

    override fun vacationGoalPauseOpenedFlow(): Flow<Boolean> =
        vacationGoalPauseOpenedState

    override suspend fun setVacationGoalPauseOpened(opened: Boolean) {
        settings.putBoolean(KEY_VACATION_GOAL_PAUSE_OPENED, opened)
        vacationGoalPauseOpenedState.value = opened
    }

    private companion object {
        // -1 = lembrete automático desligado (a chave guarda minutos >= 0).
        const val AUTO_REMINDER_OFF = -1
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_IS_PRO = "is_pro"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_TASK_DAILY_GOAL = "task_daily_goal"
        const val KEY_TASK_WEEKLY_GOAL = "task_weekly_goal"
        const val KEY_DAILY_REVIEW_ENABLED = "daily_review_enabled"
        const val KEY_DAILY_REVIEW_TIME = "daily_review_time"
        const val KEY_AUTO_REMINDER_MINUTES = "auto_reminder_minutes"
        const val KEY_GOAL_IGNORE_WEEKDAYS = "goal_ignore_weekdays"
        const val KEY_VACATION_MODE = "vacation_mode"
        const val KEY_VACATION_HABIT_IDS = "vacation_habit_ids"
        const val KEY_VACATION_GOAL_PAUSE_OPENED = "vacation_goal_pause_opened"
    }
}
