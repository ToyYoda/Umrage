package app.killersudoku.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.killersudoku.game.Difficulty
import app.killersudoku.game.GameRecord
import app.killersudoku.game.GameState
import app.killersudoku.game.Generator
import app.killersudoku.game.Grid
import app.killersudoku.game.LogicSolver
import app.killersudoku.game.Puzzle
import app.killersudoku.game.PuzzleCode
import app.killersudoku.game.Stats
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("killer_sudoku", Context.MODE_PRIVATE)

    var settings by mutableStateOf(loadSettings())
        private set
    var game: GameState? by mutableStateOf(
        prefs.getString(KEY_GAME, null)?.let { GameState.deserialize(it) }?.copy(strict = settings.strict),
    )
        private set
    var stats by mutableStateOf(Stats.deserialize(prefs.getString(KEY_STATS, "") ?: ""))
        private set
    var screen by mutableStateOf(Screen.HOME)
        private set
    var selected by mutableIntStateOf(-1)
        private set
    var notesMode by mutableStateOf(false)
        private set
    /** Beschreibung des Rätsels, das gerade erzeugt wird, sonst null. */
    var generating: String? by mutableStateOf(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var hint: HintUi? by mutableStateOf(null)
        private set
    var hintLoading by mutableStateOf(false)
        private set
    var flash: Flash? by mutableStateOf(null)
        private set
    /** Wartet auf Bestätigung, weil ein laufendes Spiel verworfen würde. */
    var pendingStart: (() -> Unit)? by mutableStateOf(null)
        private set

    private val history = ArrayDeque<GameState>()
    private val prefetched = mutableMapOf<Difficulty, Puzzle>()
    private var flashCounter = 0
    private var prefetching = false

    init {
        for (difficulty in Difficulty.entries) {
            prefs.getString(prefetchKey(difficulty), null)
                ?.let { runCatching { Puzzle.deserialize(it) }.getOrNull() }
                ?.let { prefetched[difficulty] = it }
        }
        viewModelScope.launch { prefetch() }
    }

    val today: LocalDate get() = LocalDate.now()

    // ---------------------------------------------------------------- Spiel starten

    fun newGame(difficulty: Difficulty) = confirmThen {
        if (generating != null) return@confirmThen
        val cached = prefetched.remove(difficulty)
        if (cached != null) prefs.edit().remove(prefetchKey(difficulty)).apply()
        start("Rätsel (${difficulty.label})") {
            cached ?: Generator.generate(difficulty, Random.nextInt(PuzzleCode.SEEDS))
        }
    }

    fun startDaily() {
        val day = today.toEpochDay()
        val current = game
        if (current != null && current.dailyDay == day) {
            continueGame()
            return
        }
        confirmThen {
            val (difficulty, seed) = PuzzleCode.daily(today)
            start("Tägliches Rätsel (${difficulty.label})", dailyDay = day) {
                cachedDaily(day) ?: Generator.generate(difficulty, seed)
            }
        }
    }

    /** @return false, wenn der Code ungültig ist. */
    fun startCode(code: String): Boolean {
        val (difficulty, seed) = PuzzleCode.parse(code) ?: return false
        confirmThen {
            start("Rätsel ${PuzzleCode.format(difficulty, seed)}") { Generator.generate(difficulty, seed) }
        }
        return true
    }

    fun confirmStart() {
        val action = pendingStart ?: return
        pendingStart = null
        action()
    }

    fun cancelStart() {
        pendingStart = null
    }

    /** Fragt nach, bevor ein angefangenes, ungelöstes Spiel ersetzt wird. */
    private fun confirmThen(action: () -> Unit) {
        val current = game
        val inProgress = current != null && !current.isSolved &&
            (0 until 81).any { current.values[it] != current.puzzle.givens[it] || current.notes[it] != 0 }
        if (inProgress) pendingStart = action else action()
    }

    private fun start(label: String, dailyDay: Long = -1, create: () -> Puzzle) {
        if (generating != null) return
        generating = label
        viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) { create() }
            history.clear()
            canUndo = false
            selected = -1
            notesMode = false
            hint = null
            game = GameState.new(puzzle, dailyDay, settings.strict)
            stats = stats.withStarted(puzzle.difficulty)
            save()
            generating = null
            screen = Screen.GAME
            prefetch()
        }
    }

    /** Erzeugt im Hintergrund je Schwierigkeitsgrad ein Rätsel und das heutige tägliche Rätsel vorab. */
    private suspend fun prefetch() {
        if (prefetching) return
        prefetching = true
        try {
            val date = today
            if (cachedDaily(date.toEpochDay()) == null) {
                val (difficulty, seed) = PuzzleCode.daily(date)
                val puzzle = withContext(Dispatchers.Default) { Generator.generate(difficulty, seed) }
                prefs.edit().putString(KEY_DAILY, "${date.toEpochDay()}#${puzzle.serialize()}").apply()
            }
            while (true) {
                val difficulty = Difficulty.entries.firstOrNull { it !in prefetched } ?: break
                val puzzle = withContext(Dispatchers.Default) {
                    Generator.generate(difficulty, Random.nextInt(PuzzleCode.SEEDS))
                }
                prefetched[difficulty] = puzzle
                prefs.edit().putString(prefetchKey(difficulty), puzzle.serialize()).apply()
            }
        } finally {
            prefetching = false
        }
    }

    private fun cachedDaily(day: Long): Puzzle? {
        val stored = prefs.getString(KEY_DAILY, null) ?: return null
        val (storedDay, puzzle) = stored.split("#", limit = 2).takeIf { it.size == 2 } ?: return null
        if (storedDay != day.toString()) return null
        return runCatching { Puzzle.deserialize(puzzle) }.getOrNull()
    }

    // ---------------------------------------------------------------- Navigation

    fun continueGame() {
        if (game != null) screen = Screen.GAME
    }

    fun showHome() {
        save()
        hint = null
        screen = Screen.HOME
    }

    fun showStats() {
        screen = Screen.STATS
    }

    fun showSettings() {
        screen = Screen.SETTINGS
    }

    fun updateSettings(new: Settings) {
        settings = new
        game = game?.copy(strict = new.strict)
        prefs.edit()
            .putBoolean(KEY_STRICT, new.strict)
            .putBoolean(KEY_COLORED, new.coloredCages)
            .putBoolean(KEY_ANIMATIONS, new.animations)
            .apply()
    }

    // ---------------------------------------------------------------- Spielzüge

    fun select(cell: Int) {
        selected = cell
    }

    fun toggleNotesMode() {
        notesMode = !notesMode
    }

    /** Trägt eine Ziffer ein – je nach Modus als Lösung oder als Notiz. */
    fun input(digit: Int, asNote: Boolean = notesMode) {
        val current = game ?: return
        val cell = selected
        if (cell < 0 || current.isSolved) return
        hint = null
        update(if (asNote) current.toggleNote(cell, digit) else current.enterDigit(cell, digit))
    }

    fun erase() {
        val current = game ?: return
        if (selected < 0) return
        hint = null
        update(current.erase(selected))
    }

    fun undo() {
        val current = game ?: return
        val previous = history.removeLastOrNull() ?: return
        hint = null
        // Fehler, Tipps und Zeit werden durch Rückgängig nicht zurückgesetzt.
        game = previous.copy(
            mistakes = current.mistakes,
            hintsUsed = current.hintsUsed,
            elapsedSeconds = current.elapsedSeconds,
            strict = current.strict,
        )
        canUndo = history.isNotEmpty()
        save()
    }

    // ---------------------------------------------------------------- Tipps

    /** Sucht den nächsten logischen Schritt und erklärt ihn. */
    fun requestHint() {
        val current = game ?: return
        if (current.isSolved || hintLoading) return
        val wrong = current.firstWrongCell()
        if (wrong >= 0) {
            selected = wrong
            showHint(
                HintUi(
                    title = "Falsche Ziffer",
                    text = "Die ${current.values[wrong]} in ${Grid.cellName(wrong)} passt nicht zur Lösung. " +
                        "Entferne sie zuerst – darauf aufbauende Schlüsse wären sonst falsch.",
                    reasons = emptyList(),
                    action = "Entfernen",
                    cell = wrong,
                    focus = emptySet(),
                    kind = HintUi.Kind.REMOVE_WRONG,
                ),
            )
            return
        }
        hintLoading = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { LogicSolver(current.puzzle, current.knownCorrect()).hint() }
            hintLoading = false
            // Der Timer ersetzt den Zustand jede Sekunde; verworfen wird der Tipp nur, wenn sich Ziffern geändert haben.
            if (game?.values?.contentEquals(current.values) != true) return@launch
            if (result != null) {
                val step = result.step
                selected = step.cell
                showHint(
                    HintUi(
                        title = step.technique.title,
                        text = step.text,
                        reasons = result.reasons.map { "${it.technique.title}: ${it.text}" },
                        action = "Die ${step.digit} setzen",
                        cell = step.cell,
                        focus = step.focus.toSet(),
                        kind = HintUi.Kind.PLACE,
                    ),
                )
            } else {
                val cell = selected.takeIf { it >= 0 && current.values[it] == 0 }
                    ?: (0 until 81).filter { current.values[it] == 0 }.randomOrNull()
                    ?: return@launch
                selected = cell
                showHint(
                    HintUi(
                        title = "Kein einfacher Schritt",
                        text = "Hier kommen die eingebauten Techniken nicht weiter – es wären längere Gedankenketten " +
                            "nötig. Soll ich ${Grid.cellName(cell)} aufdecken?",
                        reasons = emptyList(),
                        action = "Aufdecken",
                        cell = cell,
                        focus = emptySet(),
                        kind = HintUi.Kind.REVEAL,
                    ),
                )
            }
        }
    }

    private fun showHint(ui: HintUi) {
        val current = game ?: return
        hint = ui
        // Schon das Anzeigen zählt als Tipp.
        game = current.copy(hintsUsed = current.hintsUsed + 1)
        save()
    }

    fun applyHint() {
        val ui = hint ?: return
        val current = game ?: return
        hint = null
        selected = ui.cell
        when (ui.kind) {
            HintUi.Kind.REMOVE_WRONG -> update(current.erase(ui.cell))
            HintUi.Kind.PLACE, HintUi.Kind.REVEAL -> {
                val revealed = current.reveal(ui.cell)
                update(revealed.copy(hintsUsed = current.hintsUsed))
            }
        }
    }

    fun dismissHint() {
        hint = null
    }

    // ---------------------------------------------------------------- Zeit und Speichern

    fun tick() {
        val current = game ?: return
        if (current.isSolved) return
        game = current.copy(elapsedSeconds = current.elapsedSeconds + 1)
        if (current.elapsedSeconds % 15 == 0L) save()
    }

    fun save() {
        prefs.edit()
            .putString(KEY_GAME, game?.serialize())
            .putString(KEY_STATS, stats.serialize())
            .apply()
    }

    /** Text zum Teilen des aktuellen Rätsels. */
    fun shareText(): String? {
        val current = game ?: return null
        val code = current.puzzle.code.ifEmpty { return null }
        val daily = if (current.isDaily) " (das tägliche Rätsel)" else ""
        return "Löse dieses Killer-Sudoku$daily: Code $code – in der App unter „Code eingeben“."
    }

    private fun update(next: GameState) {
        val current = game ?: return
        if (next === current) return
        history.addLast(current)
        if (history.size > MAX_HISTORY) history.removeFirst()
        canUndo = true
        game = next
        onChanged(current, next)
        save()
    }

    /** Statistik beim Lösen, Aufleuchten fertiger Zeilen, Spalten und Blöcke. */
    private fun onChanged(before: GameState, after: GameState) {
        if (after.isSolved && !before.isSolved) {
            stats = stats.withRecord(
                GameRecord(
                    after.puzzle.difficulty,
                    after.elapsedSeconds,
                    after.mistakes,
                    after.hintsUsed,
                    today.toEpochDay(),
                    after.dailyDay,
                ),
            )
            // Diagonale Welle über das ganze Feld.
            if (settings.animations) {
                flash = Flash(++flashCounter, (0 until 81).sortedBy { Grid.row(it) + Grid.col(it) }.toIntArray())
            }
            return
        }
        if (!settings.animations || after.strict || selected < 0) return
        val cell = selected
        if (after.values[cell] == before.values[cell] || after.isWrong(cell)) return
        val newlyCompleted = after.completedUnits(cell).filter { it !in before.completedUnits(cell) }
        if (newlyCompleted.isEmpty()) return
        // Von der gesetzten Zelle aus nach außen.
        val cells = newlyCompleted.flatMap { Grid.units[it].asList() }.distinct()
            .sortedBy { maxOf(kotlin.math.abs(Grid.row(it) - Grid.row(cell)), kotlin.math.abs(Grid.col(it) - Grid.col(cell))) }
        flash = Flash(++flashCounter, cells.toIntArray())
    }

    private fun loadSettings() = Settings(
        strict = prefs.getBoolean(KEY_STRICT, false),
        coloredCages = prefs.getBoolean(KEY_COLORED, false),
        animations = prefs.getBoolean(KEY_ANIMATIONS, true),
    )

    private companion object {
        const val KEY_GAME = "game"
        const val KEY_STATS = "stats"
        const val KEY_DAILY = "daily"
        const val KEY_STRICT = "strict"
        const val KEY_COLORED = "colored_cages"
        const val KEY_ANIMATIONS = "animations"
        const val MAX_HISTORY = 500

        fun prefetchKey(difficulty: Difficulty) = "prefetch_${difficulty.name}"
    }
}
