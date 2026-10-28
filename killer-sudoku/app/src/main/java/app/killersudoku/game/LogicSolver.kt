package app.killersudoku.game

/** Lösungstechniken, sortiert nach Schwierigkeit (Stufe 1 = leicht … 5 = Ausprobieren nötig). */
enum class Technique(val level: Int, val title: String) {
    LAST_CELL(1, "Letztes Feld im Käfig"),
    NAKED_SINGLE(1, "Einzige Möglichkeit"),
    HIDDEN_SINGLE(1, "Einziger Platz"),
    CAGE_COMBINATIONS(2, "Käfig-Kombinationen"),
    RULE_45(3, "45er-Regel"),
    CAGE_UNIT_DIGIT(3, "Ziffer muss in den Käfig"),
    CAGE_POINTING(3, "Pflichtziffer im Käfig"),
    LOCKED_CANDIDATES(3, "Block und Linie"),
    NAKED_SUBSET(4, "Paare und Tripel"),
    HIDDEN_PAIR(4, "Verstecktes Paar"),
    TRIAL(5, "Ausprobieren"),
}

/** Ein logischer Lösungsschritt: entweder eine Ziffer setzen oder Kandidaten streichen. */
class Step(
    val technique: Technique,
    val text: String,
    /** Gesetztes Feld, -1 bei reinen Streichungen. */
    val cell: Int = -1,
    val digit: Int = 0,
    /** Feld → Bitmaske der gestrichenen Kandidaten. */
    val eliminations: Map<Int, Int> = emptyMap(),
    /** Felder, auf denen die Begründung beruht (Käfig, Zeile, …). */
    val focus: IntArray = IntArray(0),
) {
    val isPlacement: Boolean get() = cell >= 0
}

/** Ein Tipp: der nächste Schritt, der eine Ziffer setzt, und die Streichungen, die dafür nötig waren. */
class Hint(val step: Step, val reasons: List<Step>)

/**
 * Löst Killer-Sudokus so, wie ein Mensch es tun würde: Schritt für Schritt mit benannten Techniken.
 * Wird für erklärende Tipps und für die Bewertung der Schwierigkeit verwendet.
 *
 * @param known bekannte, richtige Ziffern (Vorgaben und korrekte Eingaben), 0 = leer.
 */
class LogicSolver private constructor(
    private val puzzle: Puzzle,
    /** Zusätzliche Gruppen mit bekannter Summe aus der 45er-Regel. */
    private val groups45: List<Group>,
    known: IntArray,
) {
    constructor(puzzle: Puzzle, known: IntArray) : this(puzzle, buildGroups45(puzzle), known)

    val values = IntArray(81)
    val candidates = IntArray(81) { ALL }

    /** Felder mit bekannter Summe. [distinct]: alle Ziffern verschieden (gleiche Einheit oder gleicher Käfig). */
    private class Group(
        val cells: IntArray,
        val sum: Int,
        val reason: String,
        val focus: IntArray,
        val distinct: Boolean,
    )

    init {
        for (cell in 0 until 81) if (known[cell] != 0) place(cell, known[cell])
    }

    private fun copy() = LogicSolver(puzzle, groups45, values).also { candidates.copyInto(it.candidates) }

    val isSolved: Boolean get() = values.none { it == 0 }

    /**
     * Der einfachste mögliche nächste Schritt oder null, wenn die Techniken (bis Stufe [maxLevel])
     * nicht weiterhelfen.
     */
    fun nextStep(maxLevel: Int = Technique.TRIAL.level): Step? {
        val step = lastCell() ?: nakedSingle() ?: hiddenSingle() ?: (if (maxLevel < 2) null else cageCombinations())
        if (step != null || maxLevel < 3) return step
        return rule45() ?: cageUnitDigit() ?: cagePointing() ?: lockedCandidates()
            ?: (if (maxLevel < 4) null else nakedSubset() ?: hiddenPair())
            ?: (if (maxLevel < 5) null else trial())
    }

    fun apply(step: Step) {
        for ((cell, mask) in step.eliminations) candidates[cell] = candidates[cell] and mask.inv()
        if (step.isPlacement) place(step.cell, step.digit)
    }

    /** Sucht den nächsten Schritt, der eine Ziffer setzt. Nötige Streichungen davor werden als Begründung mitgeliefert. */
    fun hint(): Hint? {
        val history = mutableListOf<Step>()
        repeat(MAX_STEPS) {
            val step = nextStep() ?: return null
            if (step.isPlacement) return Hint(step, history.filter { dependsOn(step, it) }.takeLast(3))
            apply(step)
            history.add(step)
        }
        return null
    }

    private fun dependsOn(step: Step, prior: Step): Boolean = prior.eliminations.any { (cell, mask) ->
        when (step.technique) {
            Technique.HIDDEN_SINGLE -> cell in step.focus && mask and bit(step.digit) != 0
            else -> cell == step.cell
        }
    }

    private fun place(cell: Int, digit: Int) {
        val b = bit(digit)
        values[cell] = digit
        candidates[cell] = b
        for (peer in Grid.peers[cell]) if (values[peer] == 0) candidates[peer] = candidates[peer] and b.inv()
        for (other in cageCells(cell)) if (other != cell && values[other] == 0) candidates[other] = candidates[other] and b.inv()
    }

    private fun cageCells(cell: Int) = puzzle.cages[puzzle.cageOf[cell]].cells

    // ---------------------------------------------------------------- Techniken

    private fun lastCell(): Step? {
        for (cage in puzzle.cages) {
            val empty = cage.cells.filter { values[it] == 0 }
            if (empty.size != 1) continue
            val placed = cage.cells.sumOf { values[it] }
            val cell = empty[0]
            val digit = cage.sum - placed
            if (digit !in 1..9 || candidates[cell] and bit(digit) == 0) continue
            val text = if (cage.cells.size == 1) {
                "Dieses Feld ist ein eigener Käfig mit der Summe ${cage.sum}, also steht hier die $digit."
            } else {
                "Im Käfig mit der Summe ${cage.sum} ist nur noch dieses Feld frei: ${cage.sum} − $placed = $digit."
            }
            return Step(Technique.LAST_CELL, text, cell, digit, focus = cage.cells)
        }
        return null
    }

    private fun nakedSingle(): Step? {
        for (cell in 0 until 81) {
            if (values[cell] != 0 || Integer.bitCount(candidates[cell]) != 1) continue
            val digit = digitOf(candidates[cell])
            return Step(
                Technique.NAKED_SINGLE,
                "Für dieses Feld bleibt nur die $digit – alle anderen Ziffern sind durch Zeile, Spalte, Block " +
                    "oder Käfig ausgeschlossen.",
                cell,
                digit,
            )
        }
        return null
    }

    private fun hiddenSingle(): Step? {
        for ((u, unit) in Grid.units.withIndex()) {
            for (digit in 1..9) {
                if (unit.any { values[it] == digit }) continue
                val places = unit.filter { values[it] == 0 && candidates[it] and bit(digit) != 0 }
                if (places.size != 1) continue
                return Step(
                    Technique.HIDDEN_SINGLE,
                    "Die $digit kann ${Grid.inUnit(u)} nur noch in diesem Feld stehen.",
                    places[0],
                    digit,
                    focus = unit,
                )
            }
        }
        return null
    }

    private fun cageCombinations(): Step? {
        for (cage in puzzle.cages) {
            val analysis = analyzeGroup(cage.cells, cage.sum) ?: continue
            val eliminations = eliminationsFrom(analysis)
            if (eliminations.isEmpty()) continue
            val text = "${groupIntro(cage.sum, analysis)} ${eliminationText(eliminations)}"
            return Step(Technique.CAGE_COMBINATIONS, text, eliminations = eliminations, focus = cage.cells)
        }
        return null
    }

    private fun rule45(): Step? {
        for (group in groups45) {
            val empty = group.cells.filter { values[it] == 0 }.toIntArray()
            if (empty.isEmpty()) continue
            val placed = group.cells.filter { values[it] != 0 }.map { values[it] }
            val remaining = group.sum - placed.sum()
            if (empty.size == 1) {
                val cell = empty[0]
                if (remaining !in 1..9 || candidates[cell] and bit(remaining) == 0) continue
                val calc = if (placed.isEmpty()) "" else
                    " Abzüglich der schon gesetzten ${placed.joinToString(" + ")} bleibt die $remaining."
                return Step(Technique.RULE_45, group.reason + calc, cell, remaining, focus = group.focus)
            }
            val allowed = if (group.distinct) {
                val placedMask = placed.fold(0) { acc, d -> acc or bit(d) }
                Combinations.analyze(IntArray(empty.size) { candidates[empty[it]] and placedMask.inv() }, remaining)
                    .allowed.takeIf { it.any { mask -> mask != 0 } }
            } else {
                allowedAssignments(empty, remaining)
            } ?: continue
            val eliminations = buildMap {
                empty.forEachIndexed { i, cell ->
                    val removed = candidates[cell] and allowed[i].inv()
                    if (removed != 0) put(cell, removed)
                }
            }
            if (eliminations.isEmpty()) continue
            val rest = if (placed.isEmpty()) "" else " Abzüglich der schon gesetzten ${placed.joinToString(" + ")} " +
                "bleiben für die freien Felder $remaining."
            return Step(
                Technique.RULE_45,
                "${group.reason}$rest ${eliminationText(eliminations)}",
                eliminations = eliminations,
                focus = group.focus,
            )
        }
        return null
    }

    /**
     * Kann eine Ziffer in einer Einheit nur in Feldern eines Käfigs stehen, muss der Käfig sie enthalten.
     * Das schränkt seine Kombinationen ein.
     */
    private fun cageUnitDigit(): Step? {
        for ((index, cage) in puzzle.cages.withIndex()) {
            val group = analyzeGroup(cage.cells, cage.sum) ?: continue
            var must = 0
            var reasonUnit = -1
            var reasonDigit = 0
            for (u in cage.cells.flatMap { Grid.unitsOf(it).asList() }.distinct()) {
                val unit = Grid.units[u]
                for (digit in 1..9) {
                    if (must and bit(digit) != 0 || unit.any { values[it] == digit }) continue
                    val places = unit.filter { values[it] == 0 && candidates[it] and bit(digit) != 0 }
                    if (places.isNotEmpty() && places.all { puzzle.cageOf[it] == index }) {
                        must = must or bit(digit)
                        reasonUnit = u
                        reasonDigit = digit
                    }
                }
            }
            if (must == 0 || group.viable.all { it and must == must }) continue
            val restricted = Combinations.analyze(
                IntArray(group.empty.size) { candidates[group.empty[it]] and group.placed.inv() },
                group.remainingSum,
                mustContain = must,
            )
            if (restricted.viable.isEmpty()) continue
            val eliminations = buildMap {
                group.empty.forEachIndexed { i, cell ->
                    val removed = candidates[cell] and restricted.allowed[i].inv()
                    if (removed != 0) put(cell, removed)
                }
            }
            if (eliminations.isEmpty()) continue
            val text = "Die $reasonDigit kann ${Grid.inUnit(reasonUnit)} nur in Feldern des Käfigs mit der Summe " +
                "${cage.sum} stehen – der Käfig muss sie also enthalten. Damit bleibt nur " +
                "${combosText(restricted.viable)}. ${eliminationText(eliminations)}"
            return Step(Technique.CAGE_UNIT_DIGIT, text, eliminations = eliminations, focus = cage.cells)
        }
        return null
    }

    /** Erlaubte Ziffern je Feld für eine Summe, wobei sich gegenseitig sehende Felder verschieden sein müssen. */
    private fun allowedAssignments(cells: IntArray, sum: Int): IntArray? {
        val k = cells.size
        val allowed = IntArray(k)
        val current = IntArray(k)
        fun conflict(a: Int, b: Int) = b in Grid.peers[a] || puzzle.cageOf[a] == puzzle.cageOf[b]
        fun search(i: Int, remaining: Int): Boolean {
            if (i == k) {
                if (remaining != 0) return false
                for (j in 0 until k) allowed[j] = allowed[j] or bit(current[j])
                return true
            }
            var found = false
            for (digit in 1..minOf(9, remaining)) {
                if (candidates[cells[i]] and bit(digit) == 0) continue
                if ((0 until i).any { current[it] == digit && conflict(cells[it], cells[i]) }) continue
                current[i] = digit
                if (search(i + 1, remaining - digit)) found = true
            }
            return found
        }
        return if (search(0, sum)) allowed else null
    }

    /** Eine Ziffer, die in jeder Kombination eines Käfigs vorkommt, aber nur in einer Einheit Platz hat. */
    private fun cagePointing(): Step? {
        for (cage in puzzle.cages) {
            val analysis = analyzeGroup(cage.cells, cage.sum) ?: continue
            var required = analysis.analysis.required
            while (required != 0) {
                val b = required and -required
                required = required xor b
                val places = analysis.empty.filter { candidates[it] and b != 0 }
                for (u in commonUnits(places)) {
                    val eliminations = Grid.units[u]
                        .filter { values[it] == 0 && it !in cage.cells && candidates[it] and b != 0 }
                        .associateWith { b }
                    if (eliminations.isEmpty()) continue
                    val digit = digitOf(b)
                    val text = "Jede mögliche Kombination im Käfig mit der Summe ${cage.sum} " +
                        "(${combosText(analysis.viable)}) enthält die $digit. Im Käfig kann sie nur " +
                        "${Grid.inUnit(u)} stehen – also nirgends sonst ${Grid.inUnit(u)}. ${eliminationText(eliminations)}"
                    return Step(Technique.CAGE_POINTING, text, eliminations = eliminations, focus = cage.cells)
                }
            }
        }
        return null
    }

    private fun lockedCandidates(): Step? {
        for ((u, unit) in Grid.units.withIndex()) {
            for (digit in 1..9) {
                val b = bit(digit)
                val places = unit.filter { values[it] == 0 && candidates[it] and b != 0 }
                if (places.size < 2) continue
                for (other in commonUnits(places)) {
                    if (other == u || (u < 18) == (other < 18)) continue
                    val eliminations = Grid.units[other]
                        .filter { values[it] == 0 && it !in unit && candidates[it] and b != 0 }
                        .associateWith { b }
                    if (eliminations.isEmpty()) continue
                    val text = "Die $digit kann ${Grid.inUnit(u)} nur in Feldern stehen, die auch " +
                        "${Grid.inUnit(other)} liegen. Also steht sie ${Grid.inUnit(other)} sicher dort " +
                        "und nirgends sonst. ${eliminationText(eliminations)}"
                    return Step(Technique.LOCKED_CANDIDATES, text, eliminations = eliminations, focus = unit)
                }
            }
        }
        return null
    }

    private fun nakedSubset(): Step? {
        for (size in 2..3) {
            for ((u, unit) in Grid.units.withIndex()) {
                val empty = unit.filter { values[it] == 0 }
                if (empty.size <= size) continue
                for (subset in subsets(empty, size)) {
                    val union = subset.fold(0) { acc, c -> acc or candidates[c] }
                    if (Integer.bitCount(union) != size) continue
                    val eliminations = empty
                        .filter { it !in subset && candidates[it] and union != 0 }
                        .associateWith { candidates[it] and union }
                    if (eliminations.isEmpty()) continue
                    val text = "${Grid.inUnit(u).replaceFirstChar { it.uppercase() }} kommen in " +
                        "${subset.joinToString(", ") { Grid.cellName(it) }} nur die Ziffern ${digitsText(union)} " +
                        "infrage. Sie müssen genau dort stehen und fallen in den anderen Feldern weg. " +
                        eliminationText(eliminations)
                    return Step(Technique.NAKED_SUBSET, text, eliminations = eliminations, focus = subset.toIntArray())
                }
            }
        }
        return null
    }

    private fun hiddenPair(): Step? {
        for ((u, unit) in Grid.units.withIndex()) {
            val positions = IntArray(10) { digit ->
                if (digit == 0) 0 else unit.indices.fold(0) { acc, i ->
                    val cell = unit[i]
                    if (values[cell] == 0 && candidates[cell] and bit(digit) != 0) acc or (1 shl i) else acc
                }
            }
            for (d1 in 1..9) for (d2 in d1 + 1..9) {
                if (Integer.bitCount(positions[d1]) != 2 || positions[d1] != positions[d2]) continue
                val pair = bit(d1) or bit(d2)
                val cells = unit.filterIndexed { i, _ -> positions[d1] and (1 shl i) != 0 }
                val eliminations = cells
                    .filter { candidates[it] and pair.inv() != 0 }
                    .associateWith { candidates[it] and pair.inv() }
                if (eliminations.isEmpty()) continue
                val text = "Die $d1 und die $d2 können ${Grid.inUnit(u)} nur in " +
                    "${cells.joinToString(" und ") { Grid.cellName(it) }} stehen. Also stehen dort keine " +
                    "anderen Ziffern. ${eliminationText(eliminations)}"
                return Step(Technique.HIDDEN_PAIR, text, eliminations = eliminations, focus = cells.toIntArray())
            }
        }
        return null
    }

    /** Annahme durchspielen: Führt eine Ziffer mit einfachen Schritten zum Widerspruch, fällt sie weg. */
    private fun trial(): Step? {
        val cells = (0 until 81).filter { values[it] == 0 }.sortedBy { Integer.bitCount(candidates[it]) }
        for (cell in cells) {
            if (Integer.bitCount(candidates[cell]) > 3) break
            var cand = candidates[cell]
            while (cand != 0) {
                val b = cand and -cand
                cand = cand xor b
                val digit = digitOf(b)
                val contradiction = copy().contradictionAfter(cell, digit) ?: continue
                return Step(
                    Technique.TRIAL,
                    "Angenommen, in ${Grid.cellName(cell)} stünde die $digit: Mit einfachen Schritten " +
                        "($TRIAL_TECHNIQUES) folgt dann, dass $contradiction. Also kann dort keine $digit stehen.",
                    eliminations = mapOf(cell to b),
                    focus = intArrayOf(cell),
                )
            }
        }
        return null
    }

    /** Setzt die Ziffer und folgt einfachen Schritten. @return Beschreibung des Widerspruchs oder null. */
    private fun contradictionAfter(cell: Int, digit: Int): String? {
        place(cell, digit)
        repeat(MAX_STEPS) {
            contradiction()?.let { return it }
            val step = lastCell() ?: nakedSingle() ?: hiddenSingle() ?: cageCombinations() ?: rule45()
                ?: cageUnitDigit() ?: cagePointing() ?: lockedCandidates() ?: return null
            apply(step)
        }
        return null
    }

    private fun contradiction(): String? {
        for (cell in 0 until 81) {
            if (values[cell] == 0 && candidates[cell] == 0) return "für ${Grid.cellName(cell)} keine Ziffer mehr bleibt"
        }
        for ((u, unit) in Grid.units.withIndex()) {
            val seen = unit.fold(0) { acc, c -> acc or (if (values[c] != 0) bit(values[c]) else candidates[c]) }
            if (seen != ALL) return "die ${digitOf(ALL and seen.inv())} ${Grid.inUnit(u)} keinen Platz mehr hat"
        }
        for (cage in puzzle.cages) {
            val empty = cage.cells.filter { values[it] == 0 }
            val remaining = cage.sum - cage.cells.sumOf { values[it] }
            val placed = cage.cells.fold(0) { acc, c -> if (values[c] != 0) acc or bit(values[c]) else acc }
            val impossible = if (empty.isEmpty()) remaining != 0 else
                Combinations.analyze(IntArray(empty.size) { candidates[empty[it]] and placed.inv() }, remaining).viable.isEmpty()
            if (impossible) return "sich der Käfig mit der Summe ${cage.sum} nicht mehr füllen lässt"
        }
        return null
    }

    // ---------------------------------------------------------------- Hilfsfunktionen

    private class GroupAnalysis(
        val empty: IntArray,
        val placed: Int,
        val remainingSum: Int,
        val analysis: Combinations.Analysis,
    ) {
        val viable: List<Int> get() = analysis.viable
    }

    /** Kombinationen für die freien Felder einer Gruppe. null, wenn die Gruppe schon voll ist. */
    private fun analyzeGroup(cells: IntArray, sum: Int): GroupAnalysis? {
        val empty = cells.filter { values[it] == 0 }.toIntArray()
        if (empty.isEmpty()) return null
        val placed = cells.fold(0) { acc, c -> if (values[c] != 0) acc or bit(values[c]) else acc }
        val remaining = sum - cells.sumOf { values[it] }
        val analysis = Combinations.analyze(IntArray(empty.size) { candidates[empty[it]] and placed.inv() }, remaining)
        if (analysis.viable.isEmpty()) return null
        return GroupAnalysis(empty, placed, remaining, analysis)
    }

    private fun eliminationsFrom(group: GroupAnalysis): Map<Int, Int> = buildMap {
        group.empty.forEachIndexed { i, cell ->
            val removed = candidates[cell] and group.analysis.allowed[i].inv()
            if (removed != 0) put(cell, removed)
        }
    }

    private fun groupIntro(sum: Int, group: GroupAnalysis): String {
        val k = group.empty.size
        val felder = if (k == 1) "1 Feld" else "$k Felder"
        val start = if (group.placed == 0) {
            "Der Käfig mit der Summe $sum hat $felder."
        } else {
            "Im Käfig mit der Summe $sum steht schon ${digitsText(group.placed)}; die übrigen $felder " +
                "ergeben zusammen ${group.remainingSum}."
        }
        return "$start Möglich ist nur ${combosText(group.viable)}."
    }

    companion object {
        private const val ALL = 0x1FF
        private const val MAX_STEPS = 1000
        private const val TRIAL_TECHNIQUES = "einzige Möglichkeiten, Käfig-Kombinationen und 45er-Regel"

        /** Einheiten (Zeile, Spalte, Block), die alle angegebenen Felder enthalten. */
        private fun commonUnits(cells: List<Int>): List<Int> {
            if (cells.isEmpty()) return emptyList()
            return Grid.unitsOf(cells[0]).filter { u -> cells.all { it in Grid.units[u] } }
        }

        /** Gruppen aus der 45er-Regel für einzelne und benachbarte Zeilen, Spalten und Blöcke. */
        private fun buildGroups45(puzzle: Puzzle): List<Group> {
            val seen = mutableSetOf<List<Int>>()
            val result = mutableListOf<Group>()
            for ((name, units) in regions()) {
                val region = units.flatMap { Grid.units[it].asList() }.toSet()
                val total = 45 * units.size
                val touching = region.map { puzzle.cageOf[it] }.distinct()
                val inside = touching.filter { c -> puzzle.cages[c].cells.all { it in region } }
                val insideSum = inside.sumOf { puzzle.cages[it].sum }
                val alle = "45er-Regel: $name ergeben alle Felder zusammen $total."

                val innies = region.filter { puzzle.cageOf[it] !in inside }.sorted()
                if (inside.isNotEmpty() && innies.size in 1..4 && seen.add(innies)) {
                    val n = if (innies.size == 1) "Das übrige Feld ist" else "Die übrigen ${innies.size} Felder ergeben"
                    result += Group(
                        innies.toIntArray(),
                        total - insideSum,
                        "$alle Die Käfige, die ganz darin liegen, ergeben $insideSum. $n daher $total − $insideSum = " +
                            "${total - insideSum}.",
                        region.toIntArray(),
                        distinct(puzzle, innies),
                    )
                }

                val touchingSum = touching.sumOf { puzzle.cages[it].sum }
                val outies = touching.flatMap { c -> puzzle.cages[c].cells.filter { it !in region } }.sorted()
                if (outies.size in 1..4 && seen.add(outies)) {
                    val n = if (outies.size == 1) "Das eine herausragende Feld ist" else
                        "Die ${outies.size} herausragenden Felder ergeben"
                    result += Group(
                        outies.toIntArray(),
                        touchingSum - total,
                        "$alle Die Käfige, die dort liegen oder hineinragen, ergeben $touchingSum. $n daher " +
                            "$touchingSum − $total = ${touchingSum - total}.",
                        (region + outies).toIntArray(),
                        distinct(puzzle, outies),
                    )
                }
            }
            return result
        }

        private fun distinct(puzzle: Puzzle, cells: List<Int>) =
            commonUnits(cells).isNotEmpty() || cells.all { puzzle.cageOf[it] == puzzle.cageOf[cells[0]] }


        /** Bereiche für die 45er-Regel: Name („In Zeile 3“) und Einheiten. */
        private fun regions(): List<Pair<String, List<Int>>> = buildList {
            for (size in 1..4) {
                for (start in 0..9 - size) {
                    val range = if (size == 1) "${start + 1}" else "${start + 1} bis ${start + size}"
                    add((if (size == 1) "In Zeile $range" else "In den Zeilen $range") to List(size) { start + it })
                    add((if (size == 1) "In Spalte $range" else "In den Spalten $range") to List(size) { 9 + start + it })
                }
            }
            for (box in 0 until 9) {
                add(Grid.inUnit(18 + box).replaceFirstChar { it.uppercase() } to listOf(18 + box))
            }
        }


        /**
         * Schwierigkeit 1–5 nach der schwersten nötigen Technik, [UNSOLVABLE], wenn die Techniken nicht ausreichen.
         * Mit [maxLevel] bricht die Bewertung ab, sobald mehr nötig ist, und liefert dann `maxLevel + 1`.
         */
        fun rate(puzzle: Puzzle, maxLevel: Int = Technique.TRIAL.level): Int {
            val solver = LogicSolver(puzzle, puzzle.givens)
            var level = 1
            repeat(MAX_STEPS) {
                if (solver.isSolved) return level
                val step = solver.nextStep(maxLevel)
                    ?: return if (maxLevel < Technique.TRIAL.level) maxLevel + 1 else UNSOLVABLE
                level = maxOf(level, step.technique.level)
                solver.apply(step)
            }
            return UNSOLVABLE
        }

        const val UNSOLVABLE = 6

        private fun bit(digit: Int) = 1 shl (digit - 1)

        private fun digitOf(mask: Int) = Integer.numberOfTrailingZeros(mask) + 1

        private fun digits(mask: Int) = (1..9).filter { mask and bit(it) != 0 }

        /** „die 5“ bzw. „2, 5 und 7“. */
        fun digitsText(mask: Int): String {
            val list = digits(mask)
            return if (list.size == 1) "die ${list[0]}" else list.dropLast(1).joinToString(", ") + " und " + list.last()
        }

        fun comboText(mask: Int) = digits(mask).joinToString("+")

        private fun combosText(combos: List<Int>): String {
            val shown = combos.take(6).map(::comboText)
            return when {
                combos.size > 6 -> shown.joinToString(", ") + " …"
                shown.size == 1 -> shown[0]
                else -> shown.dropLast(1).joinToString(", ") + " oder " + shown.last()
            }
        }

        private fun eliminationText(eliminations: Map<Int, Int>): String {
            val single = eliminations.size == 1 && Integer.bitCount(eliminations.values.first()) == 1
            if (single) {
                val (cell, mask) = eliminations.entries.first()
                return "Damit fällt die ${digitOf(mask)} in ${Grid.cellName(cell)} weg."
            }
            val parts = eliminations.entries.groupBy({ it.value }, { it.key }).map { (mask, cells) ->
                "${listText(digits(mask).map { it.toString() })} in ${listText(cells.sorted().map(Grid::cellName))}"
            }
            return "Damit fallen weg: ${parts.joinToString("; ")}."
        }

        private fun listText(items: List<String>) =
            if (items.size == 1) items[0] else items.dropLast(1).joinToString(", ") + " und " + items.last()

        private fun <T> subsets(items: List<T>, size: Int): List<List<T>> {
            if (size == 0) return listOf(emptyList())
            if (items.size < size) return emptyList()
            val first = items[0]
            val rest = items.drop(1)
            return subsets(rest, size - 1).map { listOf(first) + it } + subsets(rest, size)
        }
    }
}
