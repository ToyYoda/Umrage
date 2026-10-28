package app.killersudoku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.killersudoku.game.Difficulty
import app.killersudoku.game.DifficultyStats

@Composable
fun StatsScreen(vm: GameViewModel) {
    val stats = vm.stats
    val today = vm.today.toEpochDay()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Statistik", vm::showHome)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tägliches Rätsel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth()) {
                    StatItem("Serie (Tage)", stats.dailyStreak(today).toString(), Modifier.weight(1f))
                    StatItem("Gelöst", stats.dailySolved.toString(), Modifier.weight(1f))
                    StatItem("Heute", if (stats.dailyRecord(today) != null) "✓" else "–", Modifier.weight(1f))
                }
            }
        }
        for (difficulty in Difficulty.entries) DifficultyCard(difficulty, stats.of(difficulty))
        Text(
            "Bestzeiten zählen nur Spiele ohne Tipps. „Ohne Fehler“ heißt: keine falsche Ziffer und kein Tipp.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DifficultyCard(difficulty: Difficulty, stats: DifficultyStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(difficulty.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth()) {
                StatItem("Gespielt", stats.started.toString(), Modifier.weight(1f))
                StatItem("Gelöst", stats.solved.toString(), Modifier.weight(1f))
                StatItem("Ohne Fehler", stats.flawless.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatItem("Bestzeit", stats.bestSeconds?.let(::formatTime) ?: "–", Modifier.weight(1f))
                StatItem("Ø Zeit", stats.averageSeconds?.let(::formatTime) ?: "–", Modifier.weight(1f))
                StatItem(
                    "Serie ohne Fehler",
                    "${stats.currentFlawlessStreak} (beste ${stats.bestFlawlessStreak})",
                    Modifier.weight(1f),
                )
            }
            if (stats.recentSeconds.size >= 2) {
                Text(
                    "Lösezeiten der letzten ${stats.recentSeconds.size} Spiele",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimesChart(stats.recentSeconds)
                Text(
                    "Schnellste ${formatTime(stats.recentSeconds.min())} · Langsamste " +
                        "${formatTime(stats.recentSeconds.max())} · Letzte ${formatTime(stats.recentSeconds.last())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Säulen für die letzten Lösezeiten (älteste links), Nulllinie unten. */
@Composable
private fun TimesChart(seconds: List<Long>) {
    val bar = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val description = "Lösezeiten: " + seconds.joinToString(", ") { formatTime(it) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = description },
    ) {
        val max = seconds.max().coerceAtLeast(1)
        val gap = 2.dp.toPx()
        val slot = size.width / seconds.size
        val width = (slot - gap).coerceAtMost(24.dp.toPx())
        val radius = minOf(4.dp.toPx(), width / 2)
        seconds.forEachIndexed { i, value ->
            val height = (value.toFloat() / max * size.height).coerceAtLeast(radius)
            val left = i * slot + (slot - width) / 2
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = size.height - height,
                        right = left + width,
                        bottom = size.height,
                        topLeftCornerRadius = CornerRadius(radius),
                        topRightCornerRadius = CornerRadius(radius),
                        bottomRightCornerRadius = CornerRadius.Zero,
                        bottomLeftCornerRadius = CornerRadius.Zero,
                    ),
                )
            }
            drawPath(path, bar)
        }
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
}
