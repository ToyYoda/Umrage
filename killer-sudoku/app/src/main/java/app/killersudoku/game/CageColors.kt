package app.killersudoku.game

/** Färbt die Käfige so, dass benachbarte Käfige verschiedene Farben haben. */
object CageColors {
    const val COUNT = 6

    /** @return je Käfig ein Farbindex 0 until [COUNT]. */
    fun assign(puzzle: Puzzle): IntArray {
        val n = puzzle.cages.size
        val neighbours = Array(n) { mutableSetOf<Int>() }
        for (cell in 0 until 81) {
            for (other in Grid.neighbours[cell]) {
                val a = puzzle.cageOf[cell]
                val b = puzzle.cageOf[other]
                if (a != b) neighbours[a].add(b)
            }
        }
        // Reihenfolge „kleinster Grad zuletzt“: Für ebene Nachbarschaftsgraphen reichen damit 6 Farben.
        val remaining = (0 until n).toMutableSet()
        val degree = IntArray(n) { neighbours[it].size }
        val order = ArrayDeque<Int>()
        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { degree[it] * n + it }
            remaining.remove(next)
            order.addFirst(next)
            for (b in neighbours[next]) if (b in remaining) degree[b]--
        }
        val colors = IntArray(n) { -1 }
        for (cage in order) {
            val used = neighbours[cage].mapNotNull { colors[it].takeIf { c -> c >= 0 } }.toSet()
            // Gleichmäßig verteilen: unter den freien Farben die bisher seltenste nehmen.
            colors[cage] = (0 until COUNT).filter { it !in used }.minBy { c -> colors.count { it == c } * COUNT + c }
        }
        return colors
    }
}
