package app.killersudoku.game

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleCodeTest {
    @Test
    fun formatAndParse() {
        assertEquals("M-004711", PuzzleCode.format(Difficulty.MEDIUM, 4711))
        assertEquals(Difficulty.MEDIUM to 4711, PuzzleCode.parse("M-004711"))
        assertEquals(Difficulty.EXPERT to 12, PuzzleCode.parse(" e 12 "))
        assertEquals(Difficulty.EASY to 999_999, PuzzleCode.parse("L999999"))
        assertNull(PuzzleCode.parse("X-123"))
        assertNull(PuzzleCode.parse("M-1234567"))
        assertNull(PuzzleCode.parse(""))
    }

    @Test
    fun sameCodeGivesSamePuzzle() {
        val a = Generator.generate(Difficulty.MEDIUM, 4711)
        val b = Generator.generate(Difficulty.MEDIUM, 4711)
        assertEquals("M-004711", a.code)
        assertTrue(a.solution.contentEquals(b.solution))
        assertEquals(a.cages.map { it.cells.toList() }, b.cages.map { it.cells.toList() })
    }

    @Test
    fun dailyPuzzleDependsOnDate() {
        val monday = LocalDate.of(2026, 10, 26)
        assertEquals(Difficulty.EASY, PuzzleCode.daily(monday).first)
        assertEquals(Difficulty.EXPERT, PuzzleCode.daily(monday.plusDays(6)).first)
        assertEquals(PuzzleCode.daily(monday), PuzzleCode.daily(LocalDate.of(2026, 10, 26)))
        assertTrue(PuzzleCode.daily(monday).second != PuzzleCode.daily(monday.plusDays(7)).second)
        assertTrue(PuzzleCode.daily(monday).second in 0 until PuzzleCode.SEEDS)
    }

    @Test
    fun generatedPuzzleMatchesDifficultyRating() {
        for (difficulty in Difficulty.entries) {
            val puzzle = Generator.generate(difficulty, 17)
            assertTrue(difficulty.name, LogicSolver.rate(puzzle) in difficulty.rating)
        }
    }
}
