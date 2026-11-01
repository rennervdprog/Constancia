package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.ChallengeRepository
import app.constancia.db.ConstanciaDb
import app.constancia.model.Challenge
import app.constancia.model.ChallengeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChallengeRepositoryImpl(private val db: ConstanciaDb) : ChallengeRepository {

    private val queries get() = db.constanciaDbQueries

    override fun challengesFlow(): Flow<List<Challenge>> =
        queries.selectChallenges()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun start(id: String, startDateYmd: String, habitId: String): Unit =
        withContext(Dispatchers.Default) {
            queries.startChallenge(ChallengeStatus.ACTIVE.name, startDateYmd, habitId, id)
        }

    override suspend fun setStatus(id: String, status: ChallengeStatus): Unit =
        withContext(Dispatchers.Default) {
            // Preserva a data de inicio ja registrada, se houver.
            val current = queries.selectChallengeById(id).executeAsOneOrNull()
            queries.updateChallengeStatus(status.name, current?.start_date, id)
        }

    // Idempotente: so insere os pacotes padrao quando a tabela esta vazia.
    // A normalização Pro roda SEMPRE (barata e idempotente): bancos
    // semeados antes da definição de 2026-11-01 ganham o selo certo nos
    // pacotes de 21/30 dias sem perder status nem progresso.
    override suspend fun ensureSeed(): Unit = withContext(Dispatchers.Default) {
        queries.markProChallenges()
        if (queries.selectChallenges().executeAsList().isEmpty()) {
            queries.insertChallenge(
                "ch-meditar-7",
                "7 dias de meditação",
                "Comece com 5 minutos por dia.",
                7,
                "Meditar",
                "meditate",
                null,
                ChallengeStatus.AVAILABLE.name,
                0,
                null,
                5.0,
                "min",
            )
            queries.insertChallenge(
                "ch-agua-7",
                "7 dias de hidratação",
                "2 litros de água por dia.",
                7,
                "Beber água",
                "water",
                null,
                ChallengeStatus.AVAILABLE.name,
                0,
                null,
                2.0,
                "litros",
            )
            queries.insertChallenge(
                "ch-acordar-21",
                "21 dias acordando cedo",
                "Fixe o hábito em 21 dias.",
                21,
                "Acordar cedo",
                "sleep",
                null,
                ChallengeStatus.AVAILABLE.name,
                1,
                null,
                null,
                "",
            )
            queries.insertChallenge(
                "ch-ler-30",
                "30 dias de leitura",
                "20 páginas por dia durante um mês.",
                30,
                "Ler",
                "book",
                null,
                ChallengeStatus.AVAILABLE.name,
                1,
                null,
                20.0,
                "páginas",
            )
        }
    }
}
