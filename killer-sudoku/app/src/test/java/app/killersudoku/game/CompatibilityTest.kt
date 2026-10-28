package app.killersudoku.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Web-Version (killer-sudoku-web) erzeugt aus einem Code dasselbe Rätsel wie die App. Beide prüfen
 * dieselben Referenzdaten: Ändert sich hier etwas am Generator oder Löser, schlägt dieser Test fehl,
 * und die Referenzdaten sowie die Web-Version müssen mit angepasst werden.
 */
class CompatibilityTest {
    @Test
    fun matchesWebFixtures() {
        val file = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "killer-sudoku-web/test/fixtures.tsv") }
            .first { it.exists() }
        for (line in file.readLines().filter { it.isNotBlank() }) {
            val fields = line.split("\t")
            val (difficulty, seed) = PuzzleCode.parse(fields[0])!!
            val puzzle = Generator.generate(difficulty, seed)
            assertEquals(fields[0], fields[2], puzzle.serialize())
            assertEquals(fields[0], fields[1].toInt(), LogicSolver.rate(puzzle))
            val hint = LogicSolver(puzzle, puzzle.givens).hint()
            assertEquals(fields[0], fields[3], hint?.step?.text ?: "")
            assertEquals(fields[0], fields.getOrElse(4) { "" }, hint?.reasons?.joinToString(" || ") { it.text } ?: "")
        }
    }
}
