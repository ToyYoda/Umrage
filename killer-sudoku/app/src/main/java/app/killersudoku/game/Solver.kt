package app.killersudoku.game

/**
 * Löser für Killer-Sudoku (Backtracking mit Constraint-Propagation). Wird vom Generator benutzt,
 * um zu prüfen, ob ein Rätsel genau eine Lösung hat.
 *
 * Neben den Käfigen werden „Innies“ als zusätzliche Summengruppen verwendet: Liegen in einer Zeile,
 * Spalte oder einem Block mehrere Käfige vollständig, müssen die übrigen Zellen dieser Einheit
 * zusammen 45 minus deren Summe ergeben.
 */
class Solver(
    cages: List<Cage>,
    givens: IntArray,
) {
    class Result(
        /** Anzahl gefundener Lösungen (höchstens das übergebene Limit). */
        val count: Int,
        val solutions: List<IntArray>,
        /** true, wenn die Suche wegen des Knotenlimits abgebrochen wurde. */
        val aborted: Boolean,
    )

    private val groupCells: Array<IntArray>
    private val groupsOfCell: Array<IntArray>

    private val grid = IntArray(81)
    private val rowMask = IntArray(9)
    private val colMask = IntArray(9)
    private val boxMask = IntArray(9)
    private val groupUsed: IntArray
    private val groupRemainingSum: IntArray
    private val groupRemainingCells: IntArray

    private val basic = IntArray(81)
    private val candidates = IntArray(81)
    private val trail = IntArray(81)
    private var trailSize = 0

    private var nodes = 0L
    private var nodeLimit = 0L
    private var limit = 0
    private var preferred: IntArray? = null
    private val found = mutableListOf<IntArray>()
    private var consistent = true

    init {
        val groups = cages.map { it.sum to it.cells }.toMutableList()
        val cageOf = IntArray(81)
        cages.forEachIndexed { index, cage -> cage.cells.forEach { cageOf[it] = index } }
        for (unit in UNITS) {
            val unitSet = unit.toSet()
            val inside = unit.map { cageOf[it] }.distinct().filter { c -> cages[c].cells.all { it in unitSet } }
            val rest = unit.filter { cageOf[it] !in inside }
            if (inside.isNotEmpty() && rest.isNotEmpty()) {
                groups.add(45 - inside.sumOf { cages[it].sum } to rest.toIntArray())
            }
        }
        groupCells = Array(groups.size) { groups[it].second }
        groupRemainingSum = IntArray(groups.size) { groups[it].first }
        groupRemainingCells = IntArray(groups.size) { groups[it].second.size }
        groupUsed = IntArray(groups.size)
        groupsOfCell = Array(81) { cell -> groupCells.indices.filter { cell in groupCells[it] }.toIntArray() }

        for (cell in 0 until 81) {
            val d = givens[cell]
            if (d != 0 && !place(cell, d)) consistent = false
        }
    }

    /**
     * @param preferred wird beim Verzweigen zuerst probiert (z. B. die bekannte Lösung). Die erste Lösung
     *   ist dann sofort gefunden, und eine zweite Lösung unterscheidet sich möglichst wenig von ihr.
     */
    fun solve(limit: Int = 2, nodeLimit: Long = 200_000, preferred: IntArray? = null): Result {
        this.limit = limit
        this.nodeLimit = nodeLimit
        this.preferred = preferred
        nodes = 0
        found.clear()
        val completed = !consistent || search()
        return Result(found.size, found.toList(), aborted = !completed)
    }

    /** @return false, wenn wegen des Knotenlimits abgebrochen wurde. */
    private fun search(): Boolean {
        if (++nodes > nodeLimit) return false
        val mark = trailSize
        try {
            if (!propagate()) return true

            var bestCell = -1
            var bestCount = 10
            for (cell in 0 until 81) {
                if (grid[cell] != 0) continue
                val count = Integer.bitCount(candidates[cell])
                if (count < bestCount) {
                    bestCount = count
                    bestCell = cell
                }
            }
            if (bestCell == -1) {
                found.add(grid.copyOf())
                return true
            }

            var cand = candidates[bestCell]
            val first = preferred?.let { 1 shl (it[bestCell] - 1) } ?: 0
            while (cand != 0) {
                val b = if (cand and first != 0) first else cand and -cand
                cand = cand xor b
                val branchMark = trailSize
                if (place(bestCell, Integer.numberOfTrailingZeros(b) + 1)) {
                    if (!search()) return false
                }
                undo(branchMark)
                if (found.size >= limit) return true
            }
            return true
        } finally {
            undo(mark)
        }
    }

    /** Setzt erzwungene Ziffern (Naked und Hidden Singles). @return false bei Widerspruch. */
    private fun propagate(): Boolean {
        while (true) {
            if (!computeCandidates()) return false
            var progress = false

            for (cell in 0 until 81) {
                if (grid[cell] != 0) continue
                val cand = candidates[cell]
                if (Integer.bitCount(cand) == 1) {
                    if (!place(cell, Integer.numberOfTrailingZeros(cand) + 1)) return false
                    progress = true
                }
            }
            if (progress) continue

            // Ziffern, die in jeder möglichen Kombination einer Gruppe vorkommen, aber nur in eine Zelle passen.
            for (g in groupCells.indices) {
                val remainingCells = groupRemainingCells[g]
                if (remainingCells == 0) continue
                var union = 0
                for (cell in groupCells[g]) if (grid[cell] == 0) union = union or candidates[cell]
                var required = ALL
                var possible = 0
                for (combo in CageSums.combinations(remainingCells, groupRemainingSum[g])) {
                    if (combo and union.inv() == 0) {
                        required = required and combo
                        possible = possible or combo
                    }
                }
                if (possible == 0) return false
                while (required != 0) {
                    val b = required and -required
                    required = required xor b
                    var target = -1
                    var count = 0
                    for (cell in groupCells[g]) {
                        if (grid[cell] == 0 && candidates[cell] and b != 0) {
                            target = cell
                            count++
                        }
                    }
                    if (count == 0) return false
                    if (count == 1) {
                        if (!place(target, Integer.numberOfTrailingZeros(b) + 1)) return false
                        progress = true
                    }
                }
                if (progress) break
            }
            if (progress) continue

            for (unit in UNITS) {
                var placed = 0
                var once = 0
                var twice = 0
                for (cell in unit) {
                    if (grid[cell] != 0) {
                        placed = placed or (1 shl (grid[cell] - 1))
                    } else {
                        val cand = candidates[cell]
                        twice = twice or (once and cand)
                        once = once or cand
                    }
                }
                if ((placed or once) != ALL) return false
                var single = once and twice.inv() and placed.inv()
                while (single != 0) {
                    val b = single and -single
                    single = single xor b
                    // Ist die einzige mögliche Zelle schon belegt, muss sie zwei Ziffern aufnehmen: Widerspruch.
                    val cell = unit.firstOrNull { grid[it] == 0 && candidates[it] and b != 0 } ?: return false
                    if (!place(cell, Integer.numberOfTrailingZeros(b) + 1)) return false
                    progress = true
                }
                if (progress) break
            }
            if (!progress) return true
        }
    }

    /** @return false, wenn eine leere Zelle keinen Kandidaten mehr hat. */
    private fun computeCandidates(): Boolean {
        for (cell in 0 until 81) {
            if (grid[cell] != 0) continue
            var mask = ALL and (rowMask[Grid.row(cell)] or colMask[Grid.col(cell)] or boxMask[Grid.box(cell)]).inv()
            for (g in groupsOfCell[cell]) mask = mask and groupUsed[g].inv()
            basic[cell] = mask
        }
        for (cell in 0 until 81) {
            if (grid[cell] != 0) continue
            var cand = basic[cell]
            for (g in groupsOfCell[cell]) {
                // Ziffern, die in den übrigen leeren Zellen der Gruppe noch möglich sind.
                var others = 0
                for (other in groupCells[g]) if (other != cell && grid[other] == 0) others = others or basic[other]
                val remainingCells = groupRemainingCells[g] - 1
                val remainingSum = groupRemainingSum[g]
                var c = cand
                while (c != 0) {
                    val b = c and -c
                    c = c xor b
                    val d = Integer.numberOfTrailingZeros(b) + 1
                    if (!CageSums.isPossible(others and b.inv(), remainingCells, remainingSum - d)) cand = cand xor b
                }
            }
            if (cand == 0) return false
            candidates[cell] = cand
        }
        return true
    }

    /** @return false, wenn die Ziffer gegen eine Regel verstößt (der Zug wird trotzdem eingetragen). */
    private fun place(cell: Int, d: Int): Boolean {
        val b = 1 shl (d - 1)
        var ok = (rowMask[Grid.row(cell)] or colMask[Grid.col(cell)] or boxMask[Grid.box(cell)]) and b == 0
        grid[cell] = d
        trail[trailSize++] = cell
        rowMask[Grid.row(cell)] = rowMask[Grid.row(cell)] or b
        colMask[Grid.col(cell)] = colMask[Grid.col(cell)] or b
        boxMask[Grid.box(cell)] = boxMask[Grid.box(cell)] or b
        for (g in groupsOfCell[cell]) {
            if (groupUsed[g] and b != 0) ok = false
            groupUsed[g] = groupUsed[g] or b
            groupRemainingSum[g] -= d
            groupRemainingCells[g]--
            val sum = groupRemainingSum[g]
            if (sum < 0 || (groupRemainingCells[g] == 0 && sum != 0)) ok = false
        }
        return ok
    }

    private fun undo(mark: Int) {
        while (trailSize > mark) {
            val cell = trail[--trailSize]
            val d = grid[cell]
            val b = (1 shl (d - 1)).inv()
            grid[cell] = 0
            rowMask[Grid.row(cell)] = rowMask[Grid.row(cell)] and b
            colMask[Grid.col(cell)] = colMask[Grid.col(cell)] and b
            boxMask[Grid.box(cell)] = boxMask[Grid.box(cell)] and b
            for (g in groupsOfCell[cell]) {
                // Bit nur entfernen, wenn es von dieser Zelle stammt (bei Konflikten kann es doppelt gesetzt sein).
                if (groupCells[g].none { it != cell && grid[it] == d }) groupUsed[g] = groupUsed[g] and b
                groupRemainingSum[g] += d
                groupRemainingCells[g]++
            }
        }
    }

    private companion object {
        const val ALL = 0x1FF

        val UNITS: List<IntArray> = buildList {
            for (i in 0 until 9) {
                add(IntArray(9) { i * 9 + it })
                add(IntArray(9) { it * 9 + i })
                add(IntArray(9) { (i / 3) * 27 + (i % 3) * 3 + (it / 3) * 9 + it % 3 })
            }
        }
    }
}

/** Vorberechnete Tabelle: welche Summen lassen sich mit k verschiedenen Ziffern aus einer Ziffernmenge bilden? */
internal object CageSums {
    private val table = LongArray(512 * 10).also { table ->
        for (subset in 0 until 512) {
            val k = Integer.bitCount(subset)
            var sum = 0
            for (d in 1..9) if (subset and (1 shl (d - 1)) != 0) sum += d
            // Alle Obermengen von subset durchlaufen.
            var mask = subset
            while (mask < 512) {
                table[mask * 10 + k] = table[mask * 10 + k] or (1L shl sum)
                mask = (mask + 1) or subset
            }
        }
    }

    fun isPossible(digits: Int, count: Int, sum: Int): Boolean =
        sum in 0..45 && (table[digits * 10 + count] ushr sum) and 1L == 1L

    /** Alle Ziffernmengen (als Bitmaske) mit [count] Ziffern und der Summe [sum]. */
    fun combinations(count: Int, sum: Int): IntArray = if (sum in 0..45) combos[count * 46 + sum] else EMPTY

    private val EMPTY = IntArray(0)

    private val combos: Array<IntArray> = Array(10 * 46) { index ->
        val count = index / 46
        val sum = index % 46
        (0 until 512).filter { mask ->
            Integer.bitCount(mask) == count && (1..9).sumOf { if (mask and (1 shl (it - 1)) != 0) it else 0 } == sum
        }.toIntArray()
    }
}
