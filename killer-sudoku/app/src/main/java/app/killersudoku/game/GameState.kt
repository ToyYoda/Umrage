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
    /** Tag (Epoch-Day) des täglichen Rätsels, -1 für normale Spiele. */
    val dailyDay: Long = -1,
    /**
     * Strenger Modus: Es wird nicht verraten, ob eine Ziffer richtig ist – markiert werden nur
     * Regelverstöße. Kommt aus den Einstellungen und wird nicht mitgespeichert.
     */
    val strict: Boolean = false,
) {
    val isSolved: Boolean get() = values.contentEquals(puzzle.solution)

    val isDaily: Boolean get() = dailyDay >= 0

    fun isGiven(cell: Int) = puzzle.givens[cell] != 0

    /** Die Ziffer weicht von der Lösung ab (im strengen Modus wird das nicht angezeigt). */
    fun isWrong(cell: Int) = values[cell] != 0 && values[cell] != puzzle.solution[cell]

    /** Rot markieren: falsche Ziffer, im strengen Modus nur Regelverstöße. */
    fun isError(cell: Int) = if (strict) conflicts[cell] else isWrong(cell)

    /** Vorgaben und (außer im strengen Modus) richtig eingetragene Ziffern können nicht mehr verändert werden. */
    fun isLocked(cell: Int) = isGiven(cell) || (!strict && values[cell] != 0 && !isWrong(cell))

    fun hasNote(cell: Int, digit: Int) = notes[cell] and (1 shl (digit - 1)) != 0

    /** Wie oft eine Ziffer noch gesetzt werden muss (ohne strengen Modus: richtig gesetzt). */
    fun remaining(digit: Int): Int =
        9 - (0 until 81).count { values[it] == digit && (strict || !isWrong(it)) }

    /** Felder, die gegen eine Regel verstoßen: doppelte Ziffer in Zeile, Spalte, Block oder Käfig, falsche Käfigsumme. */
    val conflicts: BooleanArray by lazy {
        val result = BooleanArray(81)
        for (cell in 0 until 81) {
            val v = values[cell]
            if (v == 0) continue
            val cage = puzzle.cages[puzzle.cageOf[cell]].cells
            if (Grid.peers[cell].any { values[it] == v } || cage.any { it != cell && values[it] == v }) result[cell] = true
        }
        for (cage in puzzle.cages) {
            val sum = cage.cells.sumOf { values[it] }
            val full = cage.cells.all { values[it] != 0 }
            if (sum > cage.sum || (full && sum != cage.sum)) {
                cage.cells.forEach { if (values[it] != 0 && !isGiven(it)) result[it] = true }
            }
        }
        result
    }

    /** Setzt eine Ziffer. Dieselbe Ziffer erneut zu tippen entfernt sie wieder. */
    fun enterDigit(cell: Int, digit: Int): GameState {
        if (isLocked(cell)) return this
        if (values[cell] == digit) return erase(cell)
        val newValues = values.copyOf().also { it[cell] = digit }
        val newNotes = notes.copyOf()
        val correct = puzzle.solution[cell] == digit
        if (correct || strict) {
            newNotes[cell] = 0
            val b = (1 shl (digit - 1)).inv()
            Grid.peers[cell].forEach { newNotes[it] = newNotes[it] and b }
        }
        return copy(
            values = newValues,
            notes = newNotes,
            mistakes = if (correct || strict) mistakes else mistakes + 1,
        )
    }

    fun toggleNote(cell: Int, digit: Int): GameState {
        if (values[cell] != 0) return this
        val newNotes = notes.copyOf().also { it[cell] = it[cell] xor (1 shl (digit - 1)) }
        return copy(notes = newNotes)
    }

    /** Entfernt eine (falsche) Ziffer bzw. alle Notizen der Zelle. */
    fun erase(cell: Int): GameState = when {
        isLocked(cell) -> this
        values[cell] != 0 -> copy(values = values.copyOf().also { it[cell] = 0 })
        notes[cell] != 0 -> copy(notes = notes.copyOf().also { it[cell] = 0 })
        else -> this
    }

    /** Setzt die richtige Ziffer (für Tipps). */
    fun reveal(cell: Int): GameState {
        if (values[cell] == puzzle.solution[cell]) return this
        val digit = puzzle.solution[cell]
        val state = copy(values = values.copyOf().also { it[cell] = 0 }, strict = false).enterDigit(cell, digit)
        return state.copy(mistakes = mistakes, hintsUsed = hintsUsed + 1, strict = strict)
    }

    /** Ein Feld mit falscher Ziffer, falls vorhanden. */
    fun firstWrongCell(): Int = (0 until 81).firstOrNull { isWrong(it) } ?: -1

    /** Einheiten (Zeile, Spalte, Block) der Zelle, die vollständig und richtig ausgefüllt sind. */
    fun completedUnits(cell: Int): List<Int> =
        Grid.unitsOf(cell).filter { u -> Grid.units[u].all { values[it] == puzzle.solution[it] } }

    /** Bekannte richtige Ziffern (Vorgaben und korrekte Eingaben) – Grundlage für Tipps. */
    fun knownCorrect(): IntArray = IntArray(81) { if (isWrong(it)) 0 else values[it] }

    fun copy(
        values: IntArray = this.values,
        notes: IntArray = this.notes,
        mistakes: Int = this.mistakes,
        hintsUsed: Int = this.hintsUsed,
        elapsedSeconds: Long = this.elapsedSeconds,
        strict: Boolean = this.strict,
    ) = GameState(puzzle, values, notes, mistakes, hintsUsed, elapsedSeconds, dailyDay, strict)

    fun serialize(): String = listOf(
        VERSION,
        puzzle.serialize(),
        values.joinToString(""),
        notes.joinToString(","),
        mistakes,
        hintsUsed,
        elapsedSeconds,
        dailyDay,
    ).joinToString("|")

    companion object {
        private const val VERSION = "2"

        fun new(puzzle: Puzzle, dailyDay: Long = -1, strict: Boolean = false) =
            GameState(puzzle, puzzle.givens.copyOf(), IntArray(81), dailyDay = dailyDay, strict = strict)

        /** @return null, wenn der Text kein gültiger Spielstand ist. */
        fun deserialize(text: String): GameState? = runCatching {
            val parts = text.split("|")
            fun notes(s: String) = s.split(",").map { it.toInt() }.toIntArray().also { require(it.size == 81) }
            when (parts[0]) {
                "1" -> {
                    // Spielstände der ersten Version (ohne Code und tägliches Rätsel).
                    require(parts.size == 10)
                    val puzzle = Puzzle(
                        Difficulty.valueOf(parts[1]),
                        Puzzle.digits(parts[2]),
                        Puzzle.cages(parts[4]),
                        Puzzle.digits(parts[3]),
                    )
                    GameState(
                        puzzle, Puzzle.digits(parts[5]), notes(parts[6]),
                        parts[7].toInt(), parts[8].toInt(), parts[9].toLong(),
                    )
                }
                VERSION -> {
                    require(parts.size == 8)
                    GameState(
                        Puzzle.deserialize(parts[1]), Puzzle.digits(parts[2]), notes(parts[3]),
                        parts[4].toInt(), parts[5].toInt(), parts[6].toLong(), parts[7].toLong(),
                    )
                }
                else -> error("Unbekannte Version")
            }
        }.getOrNull()
    }
}
