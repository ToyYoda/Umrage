package app.killersudoku.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicSolverTest {

    /** Jeder Schritt muss zur echten Lösung passen: richtige Ziffern setzen, nie die Lösungsziffer streichen. */
    @Test
    fun stepsAreConsistentWithSolution() {
        for (difficulty in Difficulty.entries) {
            repeat(4) { seed ->
                val puzzle = Generator.generate(difficulty, Random(seed + 100L * difficulty.ordinal))
                val solver = LogicSolver(puzzle, puzzle.givens)
                var steps = 0
                while (!solver.isSolved) {
                    val step = solver.nextStep() ?: break
                    if (step.isPlacement) assertEquals(step.text, puzzle.solution[step.cell], step.digit)
                    assertTrue(step.isPlacement || step.eliminations.isNotEmpty())
                    for ((cell, mask) in step.eliminations) {
                        assertEquals(step.text, 0, mask and (1 shl (puzzle.solution[cell] - 1)))
                    }
                    assertTrue(step.text.isNotBlank())
                    solver.apply(step)
                    steps++
                }
                for (cell in 0 until 81) {
                    if (solver.values[cell] != 0) assertEquals(puzzle.solution[cell], solver.values[cell])
                }
                assertTrue(steps > 0)
            }
        }
    }

    @Test
    fun hintPlacesCorrectDigit() {
        val puzzle = Generator.generate(Difficulty.MEDIUM, Random(3))
        val hint = LogicSolver(puzzle, puzzle.givens).hint()
        assertNotNull(hint)
        hint!!
        assertTrue(hint.step.isPlacement)
        assertEquals(puzzle.solution[hint.step.cell], hint.step.digit)
    }

    @Test
    fun completedGridHasNoHint() {
        val puzzle = Generator.generate(Difficulty.EASY, Random(5))
        assertEquals(null, LogicSolver(puzzle, puzzle.solution).hint())
    }

    @Test
    fun combinationsRespectCandidates() {
        // Zwei Felder mit Summe 10, erstes Feld nur 1 oder 2 → nur 1+9 und 2+8.
        val analysis = Combinations.analyze(intArrayOf(0b11, 0x1FF), 10)
        assertEquals(listOf("1+9", "2+8"), analysis.viable.map { LogicSolver.comboText(it) })
        assertEquals(0b11, analysis.allowed[0])
        assertEquals((1 shl 8) or (1 shl 7), analysis.allowed[1])
        assertEquals(0, analysis.required)
    }
}
