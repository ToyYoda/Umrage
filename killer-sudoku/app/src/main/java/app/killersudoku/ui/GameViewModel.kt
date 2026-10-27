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
import app.killersudoku.game.GameState
import app.killersudoku.game.Generator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, GAME }

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("killer_sudoku", Context.MODE_PRIVATE)

    var game: GameState? by mutableStateOf(prefs.getString(KEY_GAME, null)?.let { GameState.deserialize(it) })
        private set
    var screen by mutableStateOf(Screen.HOME)
        private set
    var selected by mutableIntStateOf(-1)
        private set
    var notesMode by mutableStateOf(false)
        private set
    var generating: Difficulty? by mutableStateOf(null)
        private set
    var canUndo by mutableStateOf(false)
        private set

    private val history = ArrayDeque<GameState>()

    fun newGame(difficulty: Difficulty) {
        if (generating != null) return
        generating = difficulty
        viewModelScope.launch {
            val puzzle = withContext(Dispatchers.Default) { Generator.generate(difficulty) }
            history.clear()
            canUndo = false
            selected = -1
            notesMode = false
            game = GameState.new(puzzle)
            save()
            generating = null
            screen = Screen.GAME
        }
    }

    fun continueGame() {
        if (game != null) screen = Screen.GAME
    }

    fun showHome() {
        save()
        screen = Screen.HOME
    }

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
        update(if (asNote) current.toggleNote(cell, digit) else current.enterDigit(cell, digit))
    }

    fun erase() {
        val current = game ?: return
        if (selected >= 0) update(current.erase(selected))
    }

    /** Deckt das gewählte Feld auf – oder ein zufälliges noch offenes Feld. */
    fun hint() {
        val current = game ?: return
        if (current.isSolved) return
        val cell = selected.takeIf { it >= 0 && !current.isLocked(it) }
            ?: (0 until 81).filter { !current.isLocked(it) }.randomOrNull()
            ?: return
        selected = cell
        update(current.hint(cell))
    }

    fun undo() {
        val current = game ?: return
        val previous = history.removeLastOrNull() ?: return
        // Fehler, Tipps und Zeit werden durch Rückgängig nicht zurückgesetzt.
        game = previous.copy(
            mistakes = current.mistakes,
            hintsUsed = current.hintsUsed,
            elapsedSeconds = current.elapsedSeconds,
        )
        canUndo = history.isNotEmpty()
        save()
    }

    fun tick() {
        val current = game ?: return
        if (current.isSolved) return
        game = current.copy(elapsedSeconds = current.elapsedSeconds + 1)
        if (current.elapsedSeconds % 15 == 0L) save()
    }

    fun save() {
        prefs.edit().putString(KEY_GAME, game?.serialize()).apply()
    }

    private fun update(next: GameState) {
        val current = game ?: return
        if (next === current) return
        history.addLast(current)
        if (history.size > MAX_HISTORY) history.removeFirst()
        canUndo = true
        game = next
        save()
    }

    private companion object {
        const val KEY_GAME = "game"
        const val MAX_HISTORY = 500
    }
}
