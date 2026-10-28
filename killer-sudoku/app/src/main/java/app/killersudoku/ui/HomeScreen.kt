package app.killersudoku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.killersudoku.game.Difficulty
import app.killersudoku.game.PuzzleCode
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(vm: GameViewModel) {
    val generating = vm.generating
    var showCodeDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = vm::showStats) { Icon(Icons.Outlined.BarChart, contentDescription = "Statistik") }
            IconButton(onClick = vm::showSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen") }
        }
        Text("Killer Sudoku", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)

        if (generating != null) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
            Text("$generating wird erzeugt …", textAlign = TextAlign.Center)
        } else {
            GameChoices(vm, onEnterCode = { showCodeDialog = true })
        }

        Text(
            "Feld antippen, dann Ziffer wählen. Im Notizmodus – oder per langem Druck auf eine Ziffer – " +
                "werden Notizen eingetragen. Nochmals tippen entfernt sie wieder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    if (showCodeDialog) {
        CodeDialog(onDismiss = { showCodeDialog = false }, onSubmit = vm::startCode)
    }
}

@Composable
private fun GameChoices(vm: GameViewModel, onEnterCode: () -> Unit) {
    val game = vm.game
    DailyCard(vm)

    if (game != null && !game.isSolved) {
        Button(onClick = vm::continueGame, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Fortsetzen · ${game.puzzle.difficulty.label} · ${formatTime(game.elapsedSeconds)}",
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
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
    TextButton(onClick = onEnterCode) { Text("Rätsel-Code eingeben") }
}

@Composable
private fun DailyCard(vm: GameViewModel) {
    val today = vm.today
    val day = today.toEpochDay()
    val (difficulty, seed) = PuzzleCode.daily(today)
    val record = vm.stats.dailyRecord(day)
    val current = vm.game?.takeIf { it.dailyDay == day && !it.isSolved }
    val streak = vm.stats.dailyStreak(day)
    val date = today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tägliches Rätsel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$date · ${difficulty.label} · Code ${PuzzleCode.format(difficulty, seed)}", style = MaterialTheme.typography.bodySmall)
            val status = when {
                record != null -> "Gelöst in ${formatTime(record.seconds)} ✓"
                current != null -> "Angefangen · ${formatTime(current.elapsedSeconds)}"
                else -> "Noch offen"
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (streak > 0) {
                Text(
                    "Serie: $streak ${if (streak == 1) "Tag" else "Tage"} in Folge",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = vm::startDaily) {
                Text(
                    when {
                        current != null -> "Fortsetzen"
                        record != null -> "Nochmal spielen"
                        else -> "Spielen"
                    },
                )
            }
        }
    }
}

@Composable
private fun CodeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rätsel-Code eingeben") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mit demselben Code bekommt jeder dasselbe Rätsel, z. B. M-123456.")
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = false
                    },
                    singleLine = true,
                    isError = error,
                    label = { Text("Code") },
                    supportingText = if (error) {
                        { Text("Ungültiger Code – Buchstabe L, M, S oder E und bis zu 6 Ziffern.") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onSubmit(text)) onDismiss() else error = true }) { Text("Starten") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Rückfrage, bevor ein angefangenes Spiel ersetzt wird. */
@Composable
fun ConfirmDiscardDialog(vm: GameViewModel) {
    if (vm.pendingStart == null) return
    AlertDialog(
        onDismissRequest = vm::cancelStart,
        title = { Text("Laufendes Spiel verwerfen?") },
        text = { Text("Das angefangene Rätsel geht verloren, wenn du ein neues startest.") },
        confirmButton = { TextButton(onClick = vm::confirmStart) { Text("Neues Spiel") } },
        dismissButton = { TextButton(onClick = vm::cancelStart) { Text("Abbrechen") } },
    )
}
