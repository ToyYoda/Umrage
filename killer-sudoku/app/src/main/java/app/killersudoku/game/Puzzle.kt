package app.killersudoku.game

/** Schwierigkeitsgrad: steuert die Käfiggrößen und die Anzahl vorgegebener Ziffern. */
enum class Difficulty(
    val label: String,
    /** Gewichtung je Käfiggröße, Index = Größe. */
    val cageSizeWeights: IntArray,
    val givens: Int,
) {
    EASY("Leicht", intArrayOf(0, 0, 45, 40, 15), 6),
    MEDIUM("Mittel", intArrayOf(0, 0, 30, 40, 22, 8), 0),
    HARD("Schwer", intArrayOf(0, 0, 15, 30, 30, 15, 10), 0),
    EXPERT("Experte", intArrayOf(0, 0, 8, 20, 25, 22, 15, 10), 0);

    val maxCageSize: Int get() = cageSizeWeights.size - 1
}

class Cage(val sum: Int, val cells: IntArray)

class Puzzle(
    val difficulty: Difficulty,
    /** Lösung, 81 Ziffern 1..9 (zeilenweise). */
    val solution: IntArray,
    val cages: List<Cage>,
    /** Vorgegebene Ziffern, 0 = leer. */
    val givens: IntArray,
) {
    /** Zelle -> Index des Käfigs. */
    val cageOf: IntArray = IntArray(81).also { map ->
        cages.forEachIndexed { index, cage -> cage.cells.forEach { map[it] = index } }
    }

    /** Zelle, in der die Käfigsumme angezeigt wird (oberste, dann linkeste Zelle). */
    val labelCellOfCage: IntArray = IntArray(cages.size) { cages[it].cells.min() }
}

object Grid {
    fun row(cell: Int) = cell / 9
    fun col(cell: Int) = cell % 9
    fun box(cell: Int) = (cell / 27) * 3 + (cell % 9) / 3

    /** Alle Zellen in derselben Zeile, Spalte oder demselben Block (ohne die Zelle selbst). */
    val peers: Array<IntArray> = Array(81) { cell ->
        (0 until 81).filter {
            it != cell && (row(it) == row(cell) || col(it) == col(cell) || box(it) == box(cell))
        }.toIntArray()
    }

    /** Orthogonale Nachbarn. */
    val neighbours: Array<IntArray> = Array(81) { cell ->
        val r = row(cell)
        val c = col(cell)
        buildList {
            if (r > 0) add(cell - 9)
            if (r < 8) add(cell + 9)
            if (c > 0) add(cell - 1)
            if (c < 8) add(cell + 1)
        }.toIntArray()
    }
}
