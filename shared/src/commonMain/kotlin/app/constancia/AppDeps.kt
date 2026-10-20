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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
)

@Composable
fun rememberAppDeps(driverFactory: DatabaseDriverFactory): AppDeps {
    return remember {
        val db = appDatabase(driverFactory)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        val deps = AppDeps(
            habits = habits,
            checkins = checkins,
            tasks = TaskRepositoryImpl(db, checkins),
            diary = DiaryRepositoryImpl(db),
            challenges = ChallengeRepositoryImpl(db),
            sync = LocalSyncRepository(),
            prefs = PrefsRepositoryImpl(),
        )
        scope.launch { deps.challenges.ensureSeed() }
        deps
    }
}
