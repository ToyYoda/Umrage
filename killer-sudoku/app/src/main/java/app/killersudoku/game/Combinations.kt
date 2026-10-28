package app.killersudoku.game

/** Mögliche Ziffernkombinationen für eine Gruppe von Feldern mit fester Summe (Käfig oder 45er-Gruppe). */
object Combinations {
    class Analysis(
        /** Kombinationen (Bitmaske), die sich auf die Felder verteilen lassen. */
        val viable: List<Int>,
        /** Je Feld die Ziffern, die in mindestens einer verteilbaren Kombination dort stehen können. */
        val allowed: IntArray,
    ) {
        /** Ziffern, die in jeder verteilbaren Kombination vorkommen. */
        val required: Int get() = if (viable.isEmpty()) 0 else viable.reduce { acc, combo -> acc and combo }
    }

    /** Alle Kombinationen aus [count] verschiedenen Ziffern mit Summe [sum]. */
    fun all(count: Int, sum: Int): IntArray = CageSums.combinations(count, sum)

    /**
     * @param candidates Kandidaten der leeren Felder.
     * @param sum Summe, die diese Felder zusammen ergeben müssen.
     * @param mustContain Ziffern, die in jeder Kombination vorkommen müssen.
     */
    fun analyze(candidates: IntArray, sum: Int, mustContain: Int = 0): Analysis {
        val k = candidates.size
        val union = candidates.fold(0) { acc, c -> acc or c }
        val viable = mutableListOf<Int>()
        val allowed = IntArray(k)
        for (combo in CageSums.combinations(k, sum)) {
            if (combo and union.inv() != 0 || combo and mustContain != mustContain) continue
            val cellOptions = assignments(candidates, combo) ?: continue
            viable.add(combo)
            for (i in 0 until k) allowed[i] = allowed[i] or cellOptions[i]
        }
        return Analysis(viable, allowed)
    }

    /**
     * Prüft, ob sich die Ziffern von [combo] eindeutig auf die Felder verteilen lassen.
     * @return je Feld die Ziffern, die es in einer gültigen Verteilung bekommen kann, oder null.
     */
    private fun assignments(candidates: IntArray, combo: Int): IntArray? {
        val k = candidates.size
        // forward[i]: Mengen benutzter Ziffern, nachdem die Felder 0 until i belegt wurden.
        val forward = Array(k + 1) { BooleanArray(512) }
        forward[0][0] = true
        for (i in 0 until k) {
            for (mask in 0 until 512) {
                if (!forward[i][mask]) continue
                var options = candidates[i] and combo and mask.inv()
                while (options != 0) {
                    val b = options and -options
                    options = options xor b
                    forward[i + 1][mask or b] = true
                }
            }
        }
        if (!forward[k][combo]) return null
        // backward[i]: Mengen benutzter Ziffern vor Feld i, von denen aus sich der Rest vervollständigen lässt.
        val backward = Array(k + 1) { BooleanArray(512) }
        backward[k][combo] = true
        val result = IntArray(k)
        for (i in k - 1 downTo 0) {
            for (mask in 0 until 512) {
                if (!forward[i][mask]) continue
                var options = candidates[i] and combo and mask.inv()
                while (options != 0) {
                    val b = options and -options
                    options = options xor b
                    if (backward[i + 1][mask or b]) {
                        backward[i][mask] = true
                        result[i] = result[i] or b
                    }
                }
            }
        }
        return result
    }
}
