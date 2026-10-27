package app.killersudoku.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorTest {

    @Test
    fun generatedPuzzlesAreValidAndUnique() {
        for (difficulty in Difficulty.entries) {
            repeat(5) { seed ->
                val puzzle = Generator.generate(difficulty, Random(seed * 31 + difficulty.ordinal))
                assertValidSolution(puzzle.solution)

                val covered = puzzle.cages.flatMap { it.cells.asList() }
                assertEquals((0 until 81).toList(), covered.sorted())

                for (cage in puzzle.cages) {
                    assertTrue("Käfig zu groß", cage.cells.size <= difficulty.maxCageSize + 1)
                    val digits = cage.cells.map { puzzle.solution[it] }
                    assertEquals("Doppelte Ziffer im Käfig", digits.size, digits.toSet().size)
                    assertEquals(cage.sum, digits.sum())
                    assertConnected(cage.cells)
                }

                for (cell in 0 until 81) {
                    if (puzzle.givens[cell] != 0) assertEquals(puzzle.solution[cell], puzzle.givens[cell])
                }

                val result = Solver(puzzle.cages, puzzle.givens)
                    .solve(limit = 2, nodeLimit = Long.MAX_VALUE, preferred = puzzle.solution)
                assertEquals("Rätsel nicht eindeutig", 1, result.count)
                assertTrue(result.solutions[0].contentEquals(puzzle.solution))
            }
        }
    }

    @Test
    fun solverDetectsMultipleSolutions() {
        // Ein einziger Käfig pro Zeile lässt viele Lösungen zu.
        val cages = (0 until 9).map { r -> Cage(45, IntArray(9) { r * 9 + it }) }
        val result = Solver(cages, IntArray(81)).solve(limit = 2)
        assertEquals(2, result.count)
        result.solutions.forEach(::assertValidSolution)
        assertFalse(result.solutions[0].contentEquals(result.solutions[1]))
    }

    private fun assertValidSolution(grid: IntArray) {
        for (cell in 0 until 81) {
            assertTrue(grid[cell] in 1..9)
            for (peer in Grid.peers[cell]) assertTrue(grid[cell] != grid[peer])
        }
    }

    private fun assertConnected(cells: IntArray) {
        val set = cells.toSet()
        val seen = mutableSetOf(cells[0])
        val queue = ArrayDeque(listOf(cells[0]))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (n in Grid.neighbours[cell]) if (n in set && seen.add(n)) queue.add(n)
        }
        assertEquals("Käfig nicht zusammenhängend", set, seen)
    }
}
