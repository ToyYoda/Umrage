package app.killersudoku.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {
    private val puzzle = Generator.generate(Difficulty.MEDIUM, Random(42))
    private val emptyCell = (0 until 81).first { puzzle.givens[it] == 0 }
    private val correct = puzzle.solution[emptyCell]
    private val wrong = (1..9).first { it != correct }

    @Test
    fun wrongEntryIsFlaggedAndCounted() {
        val state = GameState.new(puzzle).enterDigit(emptyCell, wrong)
        assertTrue(state.isWrong(emptyCell))
        assertFalse(state.isLocked(emptyCell))
        assertEquals(1, state.mistakes)

        val erased = state.erase(emptyCell)
        assertEquals(0, erased.values[emptyCell])
        assertEquals(1, erased.mistakes)
    }

    @Test
    fun correctEntryLocksCellAndClearsPeerNotes() {
        val peer = Grid.peers[emptyCell].first { puzzle.givens[it] == 0 }
        val state = GameState.new(puzzle)
            .toggleNote(peer, correct)
            .toggleNote(peer, wrong)
            .enterDigit(emptyCell, correct)
        assertTrue(state.isLocked(emptyCell))
        assertEquals(0, state.mistakes)
        assertFalse(state.hasNote(peer, correct))
        assertTrue(state.hasNote(peer, wrong))
        assertEquals(state, state.enterDigit(emptyCell, wrong))
    }

    @Test
    fun notesCanBeToggledAndErased() {
        var state = GameState.new(puzzle).toggleNote(emptyCell, 3).toggleNote(emptyCell, 7)
        assertTrue(state.hasNote(emptyCell, 3) && state.hasNote(emptyCell, 7))
        state = state.toggleNote(emptyCell, 3)
        assertFalse(state.hasNote(emptyCell, 3))
        state = state.erase(emptyCell)
        assertEquals(0, state.notes[emptyCell])
    }

    @Test
    fun solvingWithHintsCompletesGame() {
        var state = GameState.new(puzzle)
        for (cell in 0 until 81) state = state.reveal(cell)
        assertTrue(state.isSolved)
        assertEquals(0, state.mistakes)
        assertEquals((1..9).map { 0 }, (1..9).map { state.remaining(it) })
    }

    @Test
    fun serializationRoundTrip() {
        val state = GameState.new(puzzle)
            .enterDigit(emptyCell, wrong)
            .toggleNote(80 - emptyCell, 5)
            .copy(elapsedSeconds = 123)
        val restored = GameState.deserialize(state.serialize())
        assertNotNull(restored)
        restored!!
        assertEquals(state.serialize(), restored.serialize())
        assertTrue(restored.puzzle.solution.contentEquals(puzzle.solution))
        assertEquals(1, restored.mistakes)
        assertEquals(123, restored.elapsedSeconds)
    }

    @Test
    fun strictModeOnlyFlagsRuleViolations() {
        val peer = Grid.peers[emptyCell].first { puzzle.givens[it] == 0 && puzzle.solution[it] != wrong }
        var state = GameState.new(puzzle, strict = true).enterDigit(emptyCell, wrong)
        assertFalse("falsche Ziffer ohne Konflikt wird nicht verraten", state.isError(emptyCell))
        assertEquals(0, state.mistakes)
        assertFalse(state.isLocked(emptyCell))
        state = state.enterDigit(peer, wrong)
        assertTrue(state.isError(emptyCell))
        assertTrue(state.isError(peer))
        // Richtige Ziffern bleiben im strengen Modus änderbar.
        val correctState = GameState.new(puzzle, strict = true).enterDigit(emptyCell, correct)
        assertFalse(correctState.isLocked(emptyCell))
        assertEquals(0, correctState.enterDigit(emptyCell, wrong).mistakes)
    }

    @Test
    fun wrongCageSumIsConflict() {
        val cage = puzzle.cages.first { c -> c.cells.size >= 2 && c.cells.none { puzzle.givens[it] != 0 } }
        var state = GameState.new(puzzle, strict = true)
        // Alle Felder bis auf eines richtig, das letzte mit einer anderen, im Käfig freien Ziffer.
        val last = cage.cells.last()
        cage.cells.dropLast(1).forEach { state = state.enterDigit(it, puzzle.solution[it]) }
        val used = cage.cells.dropLast(1).map { puzzle.solution[it] }.toSet()
        val other = (1..9).first { it != puzzle.solution[last] && it !in used }
        state = state.enterDigit(last, other)
        assertTrue(cage.cells.all { state.conflicts[it] })
    }

    @Test
    fun completedUnitsAreDetected() {
        var state = GameState.new(puzzle)
        val row = Grid.units[0]
        row.forEach { state = state.reveal(it) }
        assertTrue(0 in state.completedUnits(row[0]))
    }

    @Test
    fun readsFirstVersionSaves() {
        val v1 = listOf(
            "1", puzzle.difficulty.name, puzzle.solution.joinToString(""), puzzle.givens.joinToString(""),
            puzzle.cages.joinToString(";") { "${it.sum}:${it.cells.joinToString(".")}" },
            puzzle.givens.joinToString(""), IntArray(81).joinToString(","), 2, 1, 99,
        ).joinToString("|")
        val state = GameState.deserialize(v1)
        assertNotNull(state)
        assertEquals(2, state!!.mistakes)
        assertEquals(99, state.elapsedSeconds)
        assertFalse(state.isDaily)
    }

    @Test
    fun dailyAndCodeSurviveSerialization() {
        val coded = Generator.generate(Difficulty.EASY, 123)
        val state = GameState.deserialize(GameState.new(coded, dailyDay = 20_000).serialize())!!
        assertEquals("L-000123", state.puzzle.code)
        assertEquals(20_000, state.dailyDay)
    }

    @Test
    fun invalidSaveIsIgnored() {
        assertEquals(null, GameState.deserialize("kaputt"))
    }
}
