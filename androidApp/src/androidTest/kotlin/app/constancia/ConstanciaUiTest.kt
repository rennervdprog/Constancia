package app.constancia

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.constancia.data.LocalSyncRepository
import app.constancia.data.local.ChallengeRepositoryImpl
import app.constancia.data.local.CheckinRepositoryImpl
import app.constancia.data.local.DiaryRepositoryImpl
import app.constancia.data.local.HabitRepositoryImpl
import app.constancia.data.local.PrefsRepositoryImpl
import app.constancia.data.local.TaskRepositoryImpl
import app.constancia.db.ConstanciaDb
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File

/**
 * Testes de UI instrumentados: tocam nos fluxos de verdade num emulador
 * e tiram screenshots (artefatos do CI). Banco zerado a cada teste.
 *
 * Roda no CI via :androidApp:connectedDebugAndroidTest (job "UI tests").
 */
@RunWith(AndroidJUnit4::class)
class ConstanciaUiTest {

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @get:Rule(order = 0)
    val shotOnFailure = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description?) {
            runCatching { shot("FAIL_" + (description?.methodName ?: "unknown")) }
        }
    }

    private lateinit var appContext: Context
    private lateinit var deps: AppDeps

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext.deleteDatabase("constancia.db")
        val driver = AndroidSqliteDriver(ConstanciaDb.Schema, appContext, "constancia.db")
        val db = ConstanciaDb(driver)
        val checkins = CheckinRepositoryImpl(db)
        deps = AppDeps(
            habits = HabitRepositoryImpl(db, checkins),
            checkins = checkins,
            tasks = TaskRepositoryImpl(db),
            diary = DiaryRepositoryImpl(db),
            challenges = ChallengeRepositoryImpl(db),
            sync = LocalSyncRepository(),
            prefs = PrefsRepositoryImpl(),
        )
        runBlocking { deps.challenges.ensureSeed() }
        composeRule.setContent { AppContent(deps) }
    }

    private fun shot(name: String) {
        val dir = File(appContext.getExternalFilesDir("uitests"), "")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { out ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun nodesWith(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun appAbre_eNavegaNas5Abas() {
        shot("01_home")
        composeRule.onNodeWithTag("tab_RELATORIOS").performClick()
        composeRule.onNodeWithText("Constância · sua evolução em números").assertIsDisplayed()
        shot("01_relatorios")
        composeRule.onNodeWithTag("tab_DIARIO").performClick()
        composeRule.onNodeWithText("Um minuto por dia: como você está e como foi o dia")
            .assertIsDisplayed()
        shot("01_diario")
        composeRule.onNodeWithTag("tab_DESAFIOS").performClick()
        composeRule.onNodeWithText("Pacotes prontos de 7, 21 e 30 dias para criar impulso")
            .assertIsDisplayed()
        shot("01_desafios")
        composeRule.onNodeWithTag("tab_MAIS").performClick()
        composeRule.onNodeWithText("Conta, nuvem, saúde do celular e seus dados").assertIsDisplayed()
        shot("01_mais")
        composeRule.onNodeWithTag("tab_HOJE").performClick()
        composeRule.onNodeWithText("Tarefas de hoje").assertIsDisplayed()
    }

    @Test
    fun criarHabitoComSessao_apareceNaHoje() {
        composeRule.onNodeWithTag("fab_add_habit").performClick()
        composeRule.onNodeWithTag("habit_name_field").performTextInput("Beber água")
        composeRule.onNodeWithText("Tarde").performClick()
        shot("02_criando_habito")
        composeRule.onNodeWithTag("habit_save").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith("Beber água") }
        composeRule.onNodeWithText("Tarde").assertIsDisplayed()
        shot("02_habito_criado")
    }

    @Test
    fun checkinNoCirculo_marcaHabito() {
        composeRule.onNodeWithTag("fab_add_habit").performClick()
        composeRule.onNodeWithTag("habit_name_field").performTextInput("Ler")
        composeRule.onNodeWithTag("habit_save").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith("Ler") }

        composeRule.onNodeWithText("0 de 1 feitos").assertIsDisplayed()
        composeRule.onAllNodesWithTag("habit_check")[0].performClick()
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith("1 de 1 feitos") }
        shot("03_checkin_marcado")
    }

    @Test
    fun adicionarTarefa_apareceNaLista() {
        composeRule.onNodeWithTag("task_input").performScrollTo().performTextInput("Comprar pão")
        shot("04_digitando_tarefa")
        composeRule.onNodeWithTag("task_add").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith("Comprar pão") }
        composeRule.onNodeWithText("Comprar pão").assertIsDisplayed()
        shot("04_tarefa_adicionada")
    }

    @Test
    fun diario_salvaHumorENota() {
        composeRule.onNodeWithTag("tab_DIARIO").performClick()
        composeRule.onNodeWithTag("mood_4").performClick()
        composeRule.onNodeWithTag("diary_note").performTextInput("Dia bom")
        shot("05_diario_preenchido")
        composeRule.onNodeWithText("Salvar no diário").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith("Atualizar diário") }
        shot("05_diario_salvo")
    }

    @Test
    fun stepperQuantidade_ajustaSemQuebrar() {
        composeRule.onNodeWithTag("fab_add_habit").performClick()
        composeRule.onNodeWithTag("habit_name_field").performTextInput("Água")
        composeRule.onNodeWithText("Quantidade").performClick()
        composeRule.onNodeWithTag("habit_save").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith("0/8 copos") }

        composeRule.onNodeWithTag("qty_inc").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith("1/8 copos") }
        shot("06_stepper_1")
        composeRule.onNodeWithTag("qty_dec").performClick()
        composeRule.waitUntil(timeoutMillis = 8000) { nodesWith("0/8 copos") }
        shot("06_stepper_0")
    }

    @Test
    fun paywall_abrePeloConstanciaPro() {
        composeRule.onNodeWithTag("tab_MAIS").performClick()
        composeRule.onNodeWithText("Constância Pro").performClick()
        composeRule.onNodeWithText("Começar 7 dias grátis").assertIsDisplayed()
        shot("07_paywall")
    }
}
