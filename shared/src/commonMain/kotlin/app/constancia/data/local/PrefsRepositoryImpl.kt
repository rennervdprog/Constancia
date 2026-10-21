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

    private companion object {
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_IS_PRO = "is_pro"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_TASK_DAILY_GOAL = "task_daily_goal"
        const val KEY_TASK_WEEKLY_GOAL = "task_weekly_goal"
        const val KEY_DAILY_REVIEW_ENABLED = "daily_review_enabled"
        const val KEY_DAILY_REVIEW_TIME = "daily_review_time"
    }
}
