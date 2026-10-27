package app.killersudoku.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.killersudoku.game.Difficulty
import app.killersudoku.game.GameState

@Composable
fun HomeScreen(vm: GameViewModel) {
    val game = vm.game
    val generating = vm.generating
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Killer Sudoku", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (generating != null) {
            CircularProgressIndicator()
            Text("Rätsel (${generating.label}) wird erzeugt …")
        } else {
            if (game != null && !game.isSolved) {
                Button(onClick = vm::continueGame, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Fortsetzen · ${game.puzzle.difficulty.label} · ${formatTime(game.elapsedSeconds)}",
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            Text("Neues Spiel", style = MaterialTheme.typography.titleMedium)
            for (difficulty in Difficulty.entries) {
                OutlinedButton(onClick = { vm.newGame(difficulty) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(difficulty.label, style = MaterialTheme.typography.titleMedium)
                        Text(difficulty.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Feld antippen, dann Ziffer wählen. Im Notizmodus – oder per langem Druck auf eine Ziffer – " +
                "werden Notizen eingetragen. Nochmals tippen entfernt sie wieder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun GameScreen(vm: GameViewModel, game: GameState) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
    ) {
        if (maxWidth > maxHeight) {
            val boardSize = minOf(maxHeight - 16.dp, maxWidth * 0.55f)
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                Board(game, vm.selected, vm::select, Modifier.size(boardSize))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBar(vm, game)
                    Controls(vm, game)
                }
            }
        } else {
            val boardSize = minOf(maxWidth, maxHeight - 240.dp).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                StatusBar(vm, game)
                Spacer(Modifier.height(8.dp))
                Board(game, vm.selected, vm::select, Modifier.size(boardSize))
                Spacer(Modifier.weight(1f))
                Controls(vm, game)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StatusBar(vm: GameViewModel, game: GameState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::showHome) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zum Menü")
        }
        Text(game.puzzle.difficulty.label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text(
            "Fehler: ${game.mistakes}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (game.mistakes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Text(formatTime(game.elapsedSeconds), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun Controls(vm: GameViewModel, game: GameState) {
    if (game.isSolved) {
        SolvedCard(vm, game)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(Icons.AutoMirrored.Filled.Undo, "Rückgängig", vm::undo, enabled = vm.canUndo)
            ToolButton(Icons.AutoMirrored.Outlined.Backspace, "Löschen", vm::erase)
            ToolButton(
                Icons.Outlined.Edit,
                if (vm.notesMode) "Notizen: an" else "Notizen: aus",
                vm::toggleNotesMode,
                active = vm.notesMode,
            )
            ToolButton(Icons.Outlined.Lightbulb, "Tipp", vm::hint)
        }
        NumberPad(
            game = game,
            notesMode = vm.notesMode,
            onDigit = { vm.input(it) },
            onLongPress = { vm.input(it, asNote = !vm.notesMode) },
        )
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(if (active) colors.primary else colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) colors.onPrimary else contentColor)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberPad(
    game: GameState,
    notesMode: Boolean,
    onDigit: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (digit in 1..9) {
            val remaining = game.remaining(digit)
            val done = remaining == 0
            val contentColor = when {
                done -> colors.onSurface.copy(alpha = 0.3f)
                notesMode -> colors.primary
                else -> colors.onSecondaryContainer
            }
            Column(
                Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (done) colors.surfaceVariant.copy(alpha = 0.4f) else colors.secondaryContainer)
                    .combinedClickable(
                        enabled = !done,
                        onClick = { onDigit(digit) },
                        onLongClick = { onLongPress(digit) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    digit.toString(),
                    fontSize = if (notesMode) 20.sp else 28.sp,
                    fontWeight = if (notesMode) FontWeight.Normal else FontWeight.Medium,
                    color = contentColor,
                )
                Text(
                    if (done) "✓" else remaining.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun SolvedCard(vm: GameViewModel, game: GameState) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Geschafft! 🎉", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Zeit ${formatTime(game.elapsedSeconds)} · Fehler ${game.mistakes} · Tipps ${game.hintsUsed}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = vm::showHome) { Text("Neues Spiel") }
        }
    }
}

private val Difficulty.description: String
    get() = when (this) {
        Difficulty.EASY -> "Kleine Käfige, einige Ziffern vorgegeben"
        Difficulty.MEDIUM -> "Käfige mit 2 bis 5 Feldern"
        Difficulty.HARD -> "Größere Käfige, keine Vorgaben"
        Difficulty.EXPERT -> "Große Käfige, kaum Anhaltspunkte"
    }

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
