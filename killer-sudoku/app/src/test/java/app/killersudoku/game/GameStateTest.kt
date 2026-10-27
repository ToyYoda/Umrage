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
        for (cell in 0 until 81) state = state.hint(cell)
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
    fun invalidSaveIsIgnored() {
        assertEquals(null, GameState.deserialize("kaputt"))
    }
}
