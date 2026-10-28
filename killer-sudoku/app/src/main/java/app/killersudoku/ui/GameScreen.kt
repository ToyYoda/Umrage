package app.killersudoku.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.killersudoku.game.CageHelp
import app.killersudoku.game.GameState
import app.killersudoku.game.LogicSolver

@Composable
fun GameScreen(vm: GameViewModel, game: GameState, onShare: (String) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
    ) {
        if (maxWidth > maxHeight) {
            val boardSize = minOf(maxHeight - 16.dp, maxWidth * 0.5f)
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                GameBoard(vm, game, boardSize)
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBar(vm, game, onShare)
                    Box(Modifier.weight(1f).fillMaxWidth()) { InfoArea(vm, game) }
                    Controls(vm, game, onShare)
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            val boardSize = minOf(maxWidth, maxHeight - 300.dp).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                StatusBar(vm, game, onShare)
                Spacer(Modifier.height(4.dp))
                GameBoard(vm, game, boardSize)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) { InfoArea(vm, game) }
                Controls(vm, game, onShare)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun GameBoard(vm: GameViewModel, game: GameState, size: Dp) {
    Board(
        game,
        vm.selected,
        vm::select,
        Modifier.size(size),
        coloredCages = vm.settings.coloredCages,
        highlight = vm.hint?.let { BoardHighlight(it.cell, it.focus) },
        flash = vm.flash,
    )
}

@Composable
private fun StatusBar(vm: GameViewModel, game: GameState, onShare: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::showHome) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zum Menü")
        }
        Column {
            Text(
                if (game.isDaily) "Tägliches Rätsel · ${game.puzzle.difficulty.label}" else game.puzzle.difficulty.label,
                style = MaterialTheme.typography.titleMedium,
            )
            if (game.puzzle.code.isNotEmpty()) {
                Text(
                    "Code ${game.puzzle.code}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        vm.shareText()?.let { text ->
            IconButton(onClick = { onShare(text) }) {
                Icon(Icons.Outlined.Share, contentDescription = "Rätsel teilen")
            }
        }
        if (!game.strict) {
            Text(
                "Fehler: ${game.mistakes}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (game.mistakes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(formatTime(game.elapsedSeconds), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
    }
}

/** Bereich zwischen Spielfeld und Bedienung: Tipp, Käfig-Kombinationen oder ein Hinweis. */
@Composable
private fun InfoArea(vm: GameViewModel, game: GameState) {
    val hint = vm.hint
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        game.isSolved -> Unit
        vm.hintLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Suche den nächsten Schritt …", color = muted)
        }
        hint != null -> HintCard(hint, onApply = vm::applyHint, onDismiss = vm::dismissHint)
        vm.selected >= 0 -> CageCombinationsView(game, vm.selected)
        else -> Text(
            "Feld antippen – hier erscheinen die möglichen Zahlenkombinationen seines Käfigs.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun HintCard(hint: HintUi, onApply: () -> Unit, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(hint.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hint.reasons.isNotEmpty()) {
                    Text("Vorher:", style = MaterialTheme.typography.labelMedium)
                    for (reason in hint.reasons) Text("• $reason", style = MaterialTheme.typography.bodySmall)
                    Text("Daraus folgt:", style = MaterialTheme.typography.labelMedium)
                }
                Text(hint.text, style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Schließen") }
                Button(onClick = onApply) { Text(hint.action) }
            }
        }
    }
}

/** Alle Kombinationen des Käfigs der gewählten Zelle; unmögliche sind durchgestrichen. */
@Composable
private fun CageCombinationsView(game: GameState, cell: Int) {
    val cageIndex = game.puzzle.cageOf[cell]
    val cage = game.puzzle.cages[cageIndex]
    val combinations = remember(game, cageIndex) { CageHelp.combinations(game, cageIndex) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val possible = combinations.count { it.possible }
        Text(
            "Käfig ${cage.sum} in ${cage.cells.size} Feldern · $possible von ${combinations.size} " +
                "Kombinationen möglich",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (combination in combinations) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (combination.possible) colors.secondaryContainer else Color.Transparent,
                    border = if (combination.possible) null else BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Text(
                        LogicSolver.comboText(combination.digits),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (combination.possible) colors.onSecondaryContainer else colors.onSurfaceVariant.copy(alpha = 0.6f),
                        textDecoration = if (combination.possible) null else TextDecoration.LineThrough,
                    )
                }
            }
        }
    }
}

@Composable
private fun Controls(vm: GameViewModel, game: GameState, onShare: (String) -> Unit) {
    if (game.isSolved) {
        SolvedCard(vm, game, onShare)
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
            ToolButton(Icons.Outlined.Lightbulb, "Tipp", vm::requestHint, enabled = !vm.hintLoading)
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
            val done = remaining <= 0
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
private fun SolvedCard(vm: GameViewModel, game: GameState, onShare: (String) -> Unit) {
    val best = vm.stats.of(game.puzzle.difficulty).bestSeconds
    val newBest = game.hintsUsed == 0 && best == game.elapsedSeconds
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Geschafft! 🎉", style = MaterialTheme.typography.headlineSmall)
            if (newBest) Text("Neue Bestzeit!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                buildString {
                    append("Zeit ${formatTime(game.elapsedSeconds)}")
                    if (!game.strict) append(" · Fehler ${game.mistakes}")
                    append(" · Tipps ${game.hintsUsed}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.shareText()?.let { text ->
                    OutlinedButton(onClick = { onShare("Geschafft in ${formatTime(game.elapsedSeconds)}! $text") }) {
                        Text("Teilen")
                    }
                }
                Button(onClick = vm::showHome) { Text("Neues Spiel") }
            }
        }
    }
}
