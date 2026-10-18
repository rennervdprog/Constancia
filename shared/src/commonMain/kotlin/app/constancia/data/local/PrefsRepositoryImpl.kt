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

    private companion object {
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_IS_PRO = "is_pro"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
