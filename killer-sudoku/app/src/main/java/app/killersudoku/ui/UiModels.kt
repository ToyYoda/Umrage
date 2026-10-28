package app.killersudoku.ui

enum class Screen { HOME, GAME, STATS, SETTINGS }

class Settings(
    /** Nur Regelverstöße markieren statt sofort richtig/falsch anzuzeigen. */
    val strict: Boolean = false,
    /** Käfige farbig statt gestrichelt darstellen. */
    val coloredCages: Boolean = false,
    val animations: Boolean = true,
)

/** Ein angezeigter Tipp. */
class HintUi(
    val title: String,
    val text: String,
    /** Begründungen aus vorherigen Schritten. */
    val reasons: List<String>,
    val action: String,
    val cell: Int,
    val focus: Set<Int>,
    val kind: Kind,
) {
    enum class Kind { PLACE, REMOVE_WRONG, REVEAL }
}

/** Kurzes Aufleuchten von Feldern, z. B. wenn eine Zeile fertig ist. */
class Flash(val id: Int, val cells: IntArray)
