package app.constancia.ui.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.domain.ProGate
import app.constancia.domain.doneDatesFor
import app.constancia.domain.donePairs
import app.constancia.model.Challenge
import app.constancia.model.ChallengeStatus
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.Checkin
import app.constancia.model.newId
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import app.constancia.model.todayYmd
import app.constancia.theme.HabitPalette
import app.constancia.ui.common.argbLongOf
import app.constancia.ui.common.habitIcon
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

@Composable
fun ChallengesScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
) {
    val challenges by deps.challenges.challengesFlow().collectAsState(initial = emptyList())
    // Fase F: entitlement vem da fonte única (EntitlementManager).
    val isPro by deps.entitlement.isProFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val today = remember { todayYmd() }
    // A4 (varredura): o progresso do desafio é medido pelos check-ins
    // do hábito vinculado — coleta hábitos e uma janela larga de
    // check-ins (400 dias, como os relatórios).
    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val wideFrom = remember {
        today.toLocalDate().minus(DatePeriod(days = 400)).toYmd()
    }
    val allCheckins by deps.checkins.checkinsInRangeFlow(wideFrom, today)
        .collectAsState(initial = emptyList())
    // Trava de toque duplo (B14): um começo por vez.
    var starting by remember { mutableStateOf(false) }

    fun buildHabitFor(challenge: Challenge): Habit = Habit(
        id = newId(),
        name = challenge.habitName,
        icon = challenge.habitIcon,
        // ARGB Long canônico (0xFF0B7A55) — o formato que Habit.color
        // guarda e lê.
        color = argbLongOf(HabitPalette[0]),
        // M5: a descrição promete quantidade ("2 litros", "20 páginas",
        // "5 minutos") — o hábito nasce com essa meta, não binário.
        goalType = if (challenge.goalQuantity != null) {
            GoalType.QUANTITY
        } else {
            GoalType.BINARY
        },
        targetQuantity = challenge.goalQuantity ?: 1.0,
        quantityUnit = challenge.goalUnit,
        frequencyType = FrequencyType.DAILY,
        daysOfWeek = emptyList(),
        weeklyTarget = 7,
        session = Session.MANHA,
        reminderEnabled = false,
        reminderTime = "",
        paused = false,
        createdAt = "",
        sortOrder = 0,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Column {
                Text("Desafios", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pacotes prontos de 7, 21 e 30 dias para criar impulso",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
            }
        }
        items(challenges, key = { it.id }) { challenge ->
            val linked = linkedHabit(challenge, habits)
            ChallengeCard(
                challenge = challenge,
                isPro = isPro,
                doneDays = challengeDoneDays(challenge, linked, allCheckins),
                startEnabled = !starting,
                onOpenPaywall = onOpenPaywall,
                onStart = {
                    if (!starting) {
                        starting = true
                        scope.launch {
                            try {
                                val habit = buildHabitFor(challenge)
                                deps.habits.save(habit)
                                deps.challenges.start(challenge.id, today, habit.id)
                            } finally {
                                starting = false
                            }
                        }
                    }
                },
                onRestart = {
                    if (!starting) {
                        starting = true
                        scope.launch {
                            try {
                                // M6: recomeçar reaproveita o hábito
                                // do pacote (retoma se estava
                                // pausado); se ele sumiu, cria um novo
                                // com a mesma meta.
                                val habit = linked ?: buildHabitFor(challenge)
                                    .also { deps.habits.save(it) }
                                if (habit.paused) {
                                    deps.habits.setPaused(habit.id, false)
                                }
                                deps.challenges.start(challenge.id, today, habit.id)
                            } finally {
                                starting = false
                            }
                        }
                    }
                },
                onFinish = { scope.launch { deps.challenges.setStatus(challenge.id, ChallengeStatus.DONE) } },
                onGiveUp = {
                    scope.launch {
                        deps.challenges.setStatus(challenge.id, ChallengeStatus.GIVEN_UP)
                        // M6: o hábito do pacote não fica órfão ativo —
                        // ele pausa junto (dá para retomar na aba
                        // Hábitos ou recomeçando o desafio).
                        linked?.let { deps.habits.setPaused(it.id, true) }
                    }
                },
                todayYmd = today,
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: Challenge,
    isPro: Boolean,
    doneDays: Int,
    startEnabled: Boolean,
    onOpenPaywall: () -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onFinish: () -> Unit,
    onGiveUp: () -> Unit,
    todayYmd: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    habitIcon(challenge.habitIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(challenge.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${challenge.durationDays} dias",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (challenge.isPro) {
                ProBadge()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            challenge.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (challenge.status) {
            ChallengeStatus.AVAILABLE -> {
                Spacer(Modifier.height(12.dp))
                // Fase F: pacote Pro só COMEÇA com entitlement — quem já
                // começou segue usando mesmo após downgrade (ProGate).
                if (ProGate.canStartChallenge(challenge.isPro, challenge.status, isPro)) {
                    CtaButton(text = "Começar", onClick = onStart, enabled = startEnabled)
                } else {
                    CtaButton(
                        text = "Desbloquear no Pro",
                        onClick = onOpenPaywall,
                    )
                }
            }
            ChallengeStatus.ACTIVE -> {
                Spacer(Modifier.height(12.dp))
                // A4: o progresso é por MÉRITO — dias com check-in do
                // hábito vinculado desde o início — e o "Concluir" só
                // libera quando todos os dias estão feitos. O dia
                // corrido continua visível, como contexto.
                val currentDay = activeDay(challenge, todayYmd)
                val complete = doneDays >= challenge.durationDays
                Text(
                    "$doneDays de ${challenge.durationDays} dias feitos · " +
                        "dia corrido $currentDay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        (doneDays.toFloat() / challenge.durationDays.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CtaButton(
                        text = "Concluir",
                        onClick = onFinish,
                        modifier = Modifier.weight(1f),
                        enabled = complete,
                    )
                    SecondaryButton(text = "Desistir", onClick = onGiveUp, modifier = Modifier.weight(1f))
                }
                if (!complete) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Faltam ${challenge.durationDays - doneDays} " +
                            "dias de check-in para concluir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ChallengeStatus.DONE -> {
                Spacer(Modifier.height(12.dp))
                StatusSeal(
                    text = "Concluído",
                    icon = Icons.Filled.EmojiEvents,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Você fez $doneDays de ${challenge.durationDays} dias. " +
                        "O hábito continua ativo na sua rotina.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Fazer de novo",
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ChallengeStatus.GIVEN_UP -> {
                Spacer(Modifier.height(12.dp))
                StatusSeal(
                    text = "Desistiu",
                    icon = Icons.Filled.Close,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Você tinha feito $doneDays de ${challenge.durationDays} " +
                        "dias. O hábito do pacote ficou pausado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Tentar de novo",
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * O hábito de um desafio: pelo vínculo gravado (schema v11); desafios
 * legados sem vínculo caem para o hábito de mesmo nome mais antigo.
 */
private fun linkedHabit(challenge: Challenge, habits: List<Habit>): Habit? {
    challenge.habitId?.let { id -> habits.firstOrNull { it.id == id }?.let { return it } }
    return habits.filter { it.name == challenge.habitName }
        .minByOrNull { it.createdAt }
}

/** Dias FEITOS (check-in batendo a meta) do hábito desde o início. */
private fun challengeDoneDays(
    challenge: Challenge,
    habit: Habit?,
    checkins: List<Checkin>,
): Int {
    if (habit == null) return 0
    val start = challenge.startDate ?: return 0
    val mine = checkins.filter { it.habitId == habit.id }
    return doneDatesFor(habit, donePairs(mine)).count { it >= start }
}

private fun activeDay(challenge: Challenge, todayYmd: String): Int {
    val startDate = challenge.startDate ?: return 1
    // kotlinx-datetime não tem daysUntil em common: usa epoch days.
    val elapsed = (todayYmd.toLocalDate().toEpochDays() - startDate.toLocalDate().toEpochDays())
        .coerceAtLeast(0)
    return (elapsed + 1).coerceAtMost(challenge.durationDays)
}

@Composable
private fun ProBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "Pro",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun StatusSeal(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** Botão primário (cta): largura total, raio 18dp, padding vertical 16dp,
 *  fundo primary, texto Sora 16/700. */
@Composable
private fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Botão secundário: borda 1.5dp, raio 12dp, fundo bg, texto ink. */
@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
