package app.constancia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.constancia.data.ChallengeRepository
import app.constancia.data.CheckinRepository
import app.constancia.data.DatabaseDriverFactory
import app.constancia.data.DiaryRepository
import app.constancia.data.HabitRepository
import app.constancia.data.LocalSyncRepository
import app.constancia.data.PrefsRepository
import app.constancia.data.SyncRepository
import app.constancia.data.TaskRepository
import app.constancia.data.appDatabase
import app.constancia.data.local.ChallengeRepositoryImpl
import app.constancia.data.local.CheckinRepositoryImpl
import app.constancia.data.local.DiaryRepositoryImpl
import app.constancia.data.local.HabitRepositoryImpl
import app.constancia.data.local.PrefsRepositoryImpl
import app.constancia.data.local.TaskRepositoryImpl
import app.constancia.notify.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Dependências do app montadas uma vez: banco local + repositórios.
 * Quando o Supabase for configurado, trocar [LocalSyncRepository] pela
 * implementação remota aqui — nenhuma tela muda.
 */
class AppDeps(
    val habits: HabitRepository,
    val checkins: CheckinRepository,
    val tasks: TaskRepository,
    val diary: DiaryRepository,
    val challenges: ChallengeRepository,
    val sync: SyncRepository,
    val prefs: PrefsRepository,
) {
    /**
     * R13.4 (Fase D) — MODO FÉRIAS GLOBAL: um interruptor que congela
     * os dois sistemas de sequência ao mesmo tempo. Ligar abre a pausa
     * da meta de tarefas (se nenhuma estiver aberta) e pausa todos os
     * hábitos ATIVOS, guardando nas prefs exatamente o que ELE mexeu;
     * desligar desfaz só isso — pausa manual anterior às férias (da
     * meta ou de um hábito) fica intocada. Os dias de férias viram dias
     * perdoados pelos mecanismos que já existem (task_goal_pause +
     * pause_interval), então nenhum streak quebra.
     */
    suspend fun setVacationMode(enabled: Boolean, todayYmd: String) {
        if (enabled) {
            if (prefs.vacationModeFlow().first()) return
            val goalAlreadyPaused = tasks.goalPausesFlow().first()
                .any { it.endDate == null }
            if (!goalAlreadyPaused) {
                tasks.setGoalPaused(true, todayYmd)
                prefs.setVacationGoalPauseOpened(true)
            } else {
                prefs.setVacationGoalPauseOpened(false)
            }
            val toPause = habits.habitsFlow().first().filter { !it.paused }
            toPause.forEach { habits.setPaused(it.id, true) }
            prefs.setVacationHabitIds(toPause.map { it.id }.toSet())
            prefs.setVacationMode(true)
        } else {
            if (!prefs.vacationModeFlow().first()) return
            val ids = prefs.vacationHabitIdsFlow().first()
            ids.forEach { id ->
                // Só retoma quem as férias pausaram e segue pausado
                // (o usuário pode ter mexido no meio do caminho).
                if (habits.getById(id)?.paused == true) {
                    habits.setPaused(id, false)
                }
            }
            prefs.setVacationHabitIds(emptySet())
            if (prefs.vacationGoalPauseOpenedFlow().first()) {
                tasks.setGoalPaused(false, todayYmd)
                prefs.setVacationGoalPauseOpened(false)
            }
            prefs.setVacationMode(false)
        }
    }
}

@Composable
fun rememberAppDeps(driverFactory: DatabaseDriverFactory): AppDeps {
    return remember {
        val db = appDatabase(driverFactory)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val checkins = CheckinRepositoryImpl(db)
        val prefs = PrefsRepositoryImpl()
        val habits = HabitRepositoryImpl(db, checkins, NotificationScheduler())
        val deps = AppDeps(
            habits = habits,
            checkins = checkins,
            tasks = TaskRepositoryImpl(
                db, checkins, NotificationScheduler(), prefs,
            ),
            diary = DiaryRepositoryImpl(db),
            challenges = ChallengeRepositoryImpl(db),
            sync = LocalSyncRepository(),
            prefs = prefs,
        )
        scope.launch { deps.challenges.ensureSeed() }
        scope.launch { deps.tasks.ensureFactoryTemplates() }
        deps
    }
}
