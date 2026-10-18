package app.constancia.ui.challenges

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.model.Challenge
import app.constancia.model.ChallengeStatus
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.newId
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.ui.common.habitIcon
import kotlinx.coroutines.launch

@Composable
fun ChallengesScreen(deps: AppDeps, modifier: Modifier = Modifier) {
    val challenges by deps.challenges.challengesFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val today = remember { todayYmd() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Desafios", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pacotes prontos de 7, 21 e 30 dias para criar impulso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(challenges, key = { it.id }) { challenge ->
            ChallengeCard(
                challenge = challenge,
                onStart = {
                    scope.launch {
                        deps.challenges.start(challenge.id, today)
                        deps.habits.save(
                            Habit(
                                newId(),
                                challenge.habitName,
                                challenge.habitIcon,
                                0xFF0B7A55,
                                GoalType.BINARY,
                                1.0,
                                "",
                                FrequencyType.DAILY,
                                emptyList(),
                                7,
                                Session.MANHA,
                                false,
                                "",
                                false,
                                "",
                                0,
                            )
                        )
                    }
                },
                onFinish = { scope.launch { deps.challenges.setStatus(challenge.id, ChallengeStatus.DONE) } },
                onGiveUp = { scope.launch { deps.challenges.setStatus(challenge.id, ChallengeStatus.GIVEN_UP) } },
                todayYmd = today,
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: Challenge,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onGiveUp: () -> Unit,
    todayYmd: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("Começar")
                    }
                }
                ChallengeStatus.ACTIVE -> {
                    Spacer(Modifier.height(12.dp))
                    val currentDay = activeDay(challenge, todayYmd)
                    Text(
                        "Dia $currentDay de ${challenge.durationDays}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { currentDay.toFloat() / challenge.durationDays.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onFinish, modifier = Modifier.weight(1f)) {
                            Text("Concluir")
                        }
                        OutlinedButton(onClick = onGiveUp, modifier = Modifier.weight(1f)) {
                            Text("Desistir")
                        }
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
                }
                ChallengeStatus.GIVEN_UP -> {
                    Spacer(Modifier.height(12.dp))
                    StatusSeal(
                        text = "Desistiu",
                        icon = Icons.Filled.Close,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "Pro",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun StatusSeal(
    text: String,
    icon: ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
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
