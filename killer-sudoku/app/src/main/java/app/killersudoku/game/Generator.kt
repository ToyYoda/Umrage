package app.killersudoku.game

import kotlin.random.Random

/** Erzeugt Killer-Sudokus mit eindeutiger Lösung. */
object Generator {
    private const val REPAIR_STEPS = 60
    private const val RATING_ATTEMPTS = 40

    /** Erzeugt das Rätsel zu einem Code-Seed – gleicher Seed, gleiches Rätsel. */
    fun generate(difficulty: Difficulty, seed: Int): Puzzle {
        val puzzle = generate(difficulty, Random(seed.toLong()))
        return Puzzle(puzzle.difficulty, puzzle.solution, puzzle.cages, puzzle.givens, PuzzleCode.format(difficulty, seed))
    }

    /** Erzeugt Rätsel, bis eines die gewünschte Bewertung hat (sonst das nächstbeste). */
    fun generate(difficulty: Difficulty, random: Random = Random.Default): Puzzle {
        var best: Puzzle? = null
        var bestDistance = Int.MAX_VALUE
        repeat(RATING_ATTEMPTS) {
            val puzzle = generateUnique(difficulty, random)
            val rating = LogicSolver.rate(puzzle, maxLevel = difficulty.rating.last)
            val distance = when {
                rating in difficulty.rating -> return puzzle
                rating == LogicSolver.UNSOLVABLE -> 100
                rating < difficulty.rating.first -> difficulty.rating.first - rating
                else -> rating - difficulty.rating.last
            }
            if (distance < bestDistance) {
                best = puzzle
                bestDistance = distance
            }
        }
        return best!!
    }

    /** Ein Rätsel mit eindeutiger Lösung und dem Käfigprofil des Schwierigkeitsgrads, ohne Bewertung. */
    fun generateUnique(difficulty: Difficulty, random: Random): Puzzle {
        while (true) {
            val solution = randomSolvedGrid(random)
            val givens = IntArray(81)
            (0 until 81).shuffled(random).take(difficulty.givens).forEach { givens[it] = solution[it] }

            var cages = randomCages(solution, difficulty, random) ?: continue
            // Gibt es mehrere Lösungen, werden die Käfige rund um die abweichenden Zellen neu gewürfelt.
            for (step in 0 until REPAIR_STEPS) {
                val result = Solver(cages, givens).solve(preferred = solution)
                if (result.aborted) break
                if (result.count == 1) return Puzzle(difficulty, solution, cages, givens)
                val other = result.solutions.first { !it.contentEquals(solution) }
                val differing = (0 until 81).filter { other[it] != solution[it] }.toSet()
                val kept = cages.filter { cage -> cage.cells.none { it in differing } }
                cages = randomCages(solution, difficulty, random, kept) ?: break
            }
        }
    }

    fun randomSolvedGrid(random: Random): IntArray {
        val grid = IntArray(81)
        fill(grid, 0, random)
        return grid
    }

    private fun fill(grid: IntArray, cell: Int, random: Random): Boolean {
        if (cell == 81) return true
        for (d in (1..9).shuffled(random)) {
            if (Grid.peers[cell].none { grid[it] == d }) {
                grid[cell] = d
                if (fill(grid, cell + 1, random)) return true
            }
        }
        grid[cell] = 0
        return false
    }

    /**
     * Teilt das Gitter in zusammenhängende Käfige ohne doppelte Ziffern auf. Bereits feste Käfige
     * ([kept]) bleiben erhalten, nur die übrigen Zellen werden neu aufgeteilt.
     * @return null, wenn Einzelzellen übrig bleiben, die nicht zusammengelegt werden konnten.
     */
    fun randomCages(
        solution: IntArray,
        difficulty: Difficulty,
        random: Random,
        kept: List<Cage> = emptyList(),
    ): List<Cage>? {
        val cageId = IntArray(81) { -1 }
        val cages = kept.map { it.cells.toMutableList() }.toMutableList()
        cages.forEachIndexed { index, cells -> cells.forEach { cageId[it] = index } }

        fun freeNeighbours(cell: Int) = Grid.neighbours[cell].count { cageId[it] == -1 }
        fun digitMask(cells: List<Int>) = cells.fold(0) { acc, c -> acc or (1 shl solution[c]) }

        while (true) {
            val free = (0 until 81).filter { cageId[it] == -1 }
            if (free.isEmpty()) break
            // Mit Zellen beginnen, die kaum noch freie Nachbarn haben, damit keine Einzelzellen übrig bleiben.
            val start = free.minBy { freeNeighbours(it) * 1000 + random.nextInt(1000) }
            val target = pickCageSize(difficulty, random)
            val cage = mutableListOf(start)
            cageId[start] = cages.size
            var used = 1 shl solution[start]
            while (cage.size < target) {
                val options = cage.flatMap { Grid.neighbours[it].asList() }
                    .distinct()
                    .filter { cageId[it] == -1 && used and (1 shl solution[it]) == 0 }
                if (options.isEmpty()) break
                val next = options.random(random)
                cage.add(next)
                cageId[next] = cages.size
                used = used or (1 shl solution[next])
            }
            cages.add(cage)
        }

        // Einzelzellen mit einem Nachbarkäfig zusammenlegen.
        for (index in cages.indices) {
            val cage = cages[index]
            if (cage.size != 1) continue
            val cell = cage[0]
            val target = Grid.neighbours[cell]
                .map { cageId[it] }
                .distinct()
                .filter { other ->
                    other != index && cages[other].size in 1 until difficulty.maxCageSize + 1 &&
                        digitMask(cages[other]) and (1 shl solution[cell]) == 0
                }
                .minByOrNull { cages[it].size }
                ?: return null
            cages[target].add(cell)
            cageId[cell] = target
            cage.clear()
        }

        return cages.filter { it.isNotEmpty() }.map { cells ->
            Cage(cells.sumOf { solution[it] }, cells.sorted().toIntArray())
        }
    }

    private fun pickCageSize(difficulty: Difficulty, random: Random): Int {
        val weights = difficulty.cageSizeWeights
        var r = random.nextInt(weights.sum())
        for (size in weights.indices) {
            r -= weights[size]
            if (r < 0) return size
        }
        return weights.lastIndex
    }
}
