package app.killersudoku.game

/** Eine Kombination eines Käfigs für die Anzeige: Ziffern (Bitmaske) und ob sie noch möglich ist. */
class CageCombination(val digits: Int, val possible: Boolean)

object CageHelp {
    /**
     * Alle Kombinationen des Käfigs, jeweils mit der Angabe, ob sie zu den bisher eingetragenen Ziffern passt.
     * Verwendet nur das, was der Spieler sieht: eingetragene Ziffern (ohne strengen Modus nur die richtigen).
     */
    fun combinations(state: GameState, cageIndex: Int): List<CageCombination> {
        val cage = state.puzzle.cages[cageIndex]
        fun visible(cell: Int) = if (!state.strict && state.isWrong(cell)) 0 else state.values[cell]

        val filled = cage.cells.filter { visible(it) != 0 }
        val placed = filled.fold(0) { acc, c -> acc or (1 shl (visible(c) - 1)) }
        val empty = cage.cells.filter { visible(it) == 0 }
        val remaining = cage.sum - filled.sumOf { visible(it) }
        val duplicates = Integer.bitCount(placed) != filled.size

        val viableRest = if (duplicates || remaining < 0) emptySet() else {
            val candidates = IntArray(empty.size) { i ->
                val cell = empty[i]
                val seen = Grid.peers[cell].fold(placed) { acc, p -> if (visible(p) != 0) acc or (1 shl (visible(p) - 1)) else acc }
                0x1FF and seen.inv()
            }
            if (empty.isEmpty()) setOf(0).takeIf { remaining == 0 } ?: emptySet()
            else Combinations.analyze(candidates, remaining).viable.toSet()
        }

        return Combinations.all(cage.cells.size, cage.sum).map { combo ->
            val possible = combo and placed == placed && (combo xor placed) in viableRest
            CageCombination(combo, possible)
        }
    }
}
