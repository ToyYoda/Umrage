package app.killersudoku.game

/**
 * Schwierigkeitsgrad: steuert Käfiggrößen und Vorgaben sowie die Bewertung durch den [LogicSolver]
 * (welche Techniken zum Lösen nötig sind).
 */
enum class Difficulty(
    val label: String,
    /** Buchstabe im Rätsel-Code. */
    val letter: Char,
    /** Gewichtung je Käfiggröße, Index = Größe. */
    val cageSizeWeights: IntArray,
    val givens: Int,
    /** Gewünschte Bewertung (Stufe der schwersten nötigen Technik). */
    val rating: IntRange,
) {
    EASY("Leicht", 'L', intArrayOf(0, 0, 45, 40, 15), 6, 1..2),
    MEDIUM("Mittel", 'M', intArrayOf(0, 0, 30, 40, 22, 8), 0, 3..3),
    HARD("Schwer", 'S', intArrayOf(0, 0, 15, 30, 30, 15, 10), 0, 3..5),
    EXPERT("Experte", 'E', intArrayOf(0, 0, 8, 20, 25, 22, 15, 10), 0, 5..5);

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
    /** Code, mit dem sich das Rätsel erneut erzeugen lässt (z. B. „M-123456“), leer wenn unbekannt. */
    val code: String = "",
) {
    /** Zelle -> Index des Käfigs. */
    val cageOf: IntArray = IntArray(81).also { map ->
        cages.forEachIndexed { index, cage -> cage.cells.forEach { map[it] = index } }
    }

    /** Zelle, in der die Käfigsumme angezeigt wird (oberste, dann linkeste Zelle). */
    val labelCellOfCage: IntArray = IntArray(cages.size) { cages[it].cells.min() }

    fun serialize(): String = listOf(
        difficulty.name,
        solution.joinToString(""),
        givens.joinToString(""),
        cages.joinToString(";") { "${it.sum}:${it.cells.joinToString(".")}" },
        code,
    ).joinToString("/")

    companion object {
        /** @throws IllegalArgumentException bei ungültigem Text. */
        fun deserialize(text: String): Puzzle {
            val parts = text.split("/")
            require(parts.size == 5)
            return Puzzle(Difficulty.valueOf(parts[0]), digits(parts[1]), cages(parts[3]), digits(parts[2]), parts[4])
        }

        internal fun digits(s: String) = s.map { it.digitToInt() }.toIntArray().also { require(it.size == 81) }

        internal fun cages(s: String) = s.split(";").map { cage ->
            val (sum, cells) = cage.split(":")
            Cage(sum.toInt(), cells.split(".").map { it.toInt() }.toIntArray())
        }.also { cages -> require(cages.sumOf { it.cells.size } == 81) }
    }
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

    /** Die 27 Einheiten: Index 0–8 Zeilen, 9–17 Spalten, 18–26 Blöcke. */
    val units: List<IntArray> = List(27) { u ->
        val i = u % 9
        when (u / 9) {
            0 -> IntArray(9) { i * 9 + it }
            1 -> IntArray(9) { it * 9 + i }
            else -> IntArray(9) { (i / 3) * 27 + (i % 3) * 3 + (it / 3) * 9 + it % 3 }
        }
    }

    /** Einheiten (Zeile, Spalte, Block) einer Zelle. */
    fun unitsOf(cell: Int) = intArrayOf(row(cell), 9 + col(cell), 18 + box(cell))

    private val boxNames = listOf(
        "oben links", "oben Mitte", "oben rechts",
        "Mitte links", "Mitte", "Mitte rechts",
        "unten links", "unten Mitte", "unten rechts",
    )

    /** „in Zeile 3“, „in Spalte 5“, „im Block oben links“. */
    fun inUnit(u: Int): String = when (u / 9) {
        0 -> "in Zeile ${u % 9 + 1}"
        1 -> "in Spalte ${u % 9 + 1}"
        else -> if (u % 9 == 4) "im mittleren Block" else "im Block ${boxNames[u % 9]}"
    }

    /** Kurzname eines Feldes, z. B. „Z3/S5“ (Zeile 3, Spalte 5). */
    fun cellName(cell: Int) = "Z${row(cell) + 1}/S${col(cell) + 1}"
}
