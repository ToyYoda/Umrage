package app.killersudoku.game

/**
 * Unveränderlicher Spielstand. Jede Aktion liefert einen neuen Zustand zurück.
 * Notizen werden als Bitmaske gespeichert (Bit 0 = Ziffer 1 … Bit 8 = Ziffer 9).
 */
class GameState(
    val puzzle: Puzzle,
    /** Eingetragene Ziffern inkl. Vorgaben, 0 = leer. */
    val values: IntArray,
    val notes: IntArray,
    val mistakes: Int = 0,
    val hintsUsed: Int = 0,
    val elapsedSeconds: Long = 0,
) {
    val isSolved: Boolean get() = values.contentEquals(puzzle.solution)

    fun isGiven(cell: Int) = puzzle.givens[cell] != 0

    fun isWrong(cell: Int) = values[cell] != 0 && values[cell] != puzzle.solution[cell]

    /** Vorgaben und richtig eingetragene Ziffern können nicht mehr verändert werden. */
    fun isLocked(cell: Int) = values[cell] != 0 && values[cell] == puzzle.solution[cell]

    fun hasNote(cell: Int, digit: Int) = notes[cell] and (1 shl (digit - 1)) != 0

    /** Wie oft eine Ziffer noch richtig gesetzt werden muss. */
    fun remaining(digit: Int): Int = 9 - (0 until 81).count { values[it] == digit && !isWrong(it) }

    /** Setzt eine Ziffer. Dieselbe falsche Ziffer erneut zu tippen entfernt sie wieder. */
    fun enterDigit(cell: Int, digit: Int): GameState {
        if (isLocked(cell)) return this
        if (values[cell] == digit) return erase(cell)
        val newValues = values.copyOf().also { it[cell] = digit }
        val newNotes = notes.copyOf()
        val correct = puzzle.solution[cell] == digit
        if (correct) {
            newNotes[cell] = 0
            val b = (1 shl (digit - 1)).inv()
            Grid.peers[cell].forEach { newNotes[it] = newNotes[it] and b }
        }
        return copy(values = newValues, notes = newNotes, mistakes = if (correct) mistakes else mistakes + 1)
    }

    fun toggleNote(cell: Int, digit: Int): GameState {
        if (values[cell] != 0) return this
        val newNotes = notes.copyOf().also { it[cell] = it[cell] xor (1 shl (digit - 1)) }
        return copy(notes = newNotes)
    }

    /** Entfernt eine falsche Ziffer bzw. alle Notizen der Zelle. */
    fun erase(cell: Int): GameState = when {
        isLocked(cell) -> this
        values[cell] != 0 -> copy(values = values.copyOf().also { it[cell] = 0 })
        notes[cell] != 0 -> copy(notes = notes.copyOf().also { it[cell] = 0 })
        else -> this
    }

    /** Deckt die richtige Ziffer einer Zelle auf. */
    fun hint(cell: Int): GameState {
        if (isLocked(cell)) return this
        val digit = puzzle.solution[cell]
        val state = copy(values = values.copyOf().also { it[cell] = 0 }).enterDigit(cell, digit)
        return state.copy(mistakes = mistakes, hintsUsed = hintsUsed + 1)
    }

    fun copy(
        values: IntArray = this.values,
        notes: IntArray = this.notes,
        mistakes: Int = this.mistakes,
        hintsUsed: Int = this.hintsUsed,
        elapsedSeconds: Long = this.elapsedSeconds,
    ) = GameState(puzzle, values, notes, mistakes, hintsUsed, elapsedSeconds)

    fun serialize(): String = listOf(
        VERSION,
        puzzle.difficulty.name,
        puzzle.solution.joinToString(""),
        puzzle.givens.joinToString(""),
        puzzle.cages.joinToString(";") { "${it.sum}:${it.cells.joinToString(".")}" },
        values.joinToString(""),
        notes.joinToString(","),
        mistakes,
        hintsUsed,
        elapsedSeconds,
    ).joinToString("|")

    companion object {
        private const val VERSION = "1"

        fun new(puzzle: Puzzle) = GameState(puzzle, puzzle.givens.copyOf(), IntArray(81))

        /** @return null, wenn der Text kein gültiger Spielstand ist. */
        fun deserialize(text: String): GameState? = runCatching {
            val parts = text.split("|")
            require(parts.size == 10 && parts[0] == VERSION)
            fun digits(s: String) = s.map { it.digitToInt() }.toIntArray().also { require(it.size == 81) }
            val cages = parts[4].split(";").map { cage ->
                val (sum, cells) = cage.split(":")
                Cage(sum.toInt(), cells.split(".").map { it.toInt() }.toIntArray())
            }
            val puzzle = Puzzle(Difficulty.valueOf(parts[1]), digits(parts[2]), cages, digits(parts[3]))
            val notes = parts[6].split(",").map { it.toInt() }.toIntArray()
            require(notes.size == 81)
            GameState(puzzle, digits(parts[5]), notes, parts[7].toInt(), parts[8].toInt(), parts[9].toLong())
        }.getOrNull()
    }
}
