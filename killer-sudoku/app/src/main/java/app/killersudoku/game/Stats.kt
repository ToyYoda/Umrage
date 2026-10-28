package app.killersudoku.game

/** Ein gelöstes Spiel. */
class GameRecord(
    val difficulty: Difficulty,
    val seconds: Long,
    val mistakes: Int,
    val hints: Int,
    /** Tag (Epoch-Day), an dem das Spiel gelöst wurde. */
    val day: Long,
    /** Tag des täglichen Rätsels, -1 für normale Spiele. */
    val dailyDay: Long = -1,
) {
    /** Ohne Fehler und ohne Tipps gelöst. */
    val flawless: Boolean get() = mistakes == 0 && hints == 0
}

class DifficultyStats(
    val started: Int,
    val solved: Int,
    /** Bestzeit ohne Tipps, null wenn es keine gibt. */
    val bestSeconds: Long?,
    val averageSeconds: Long?,
    val flawless: Int,
    val currentFlawlessStreak: Int,
    val bestFlawlessStreak: Int,
    /** Lösezeiten der letzten Spiele, älteste zuerst. */
    val recentSeconds: List<Long>,
)

/** Statistik über alle Spiele. Unveränderlich; Änderungen liefern eine neue Instanz. */
class Stats(
    private val started: Map<Difficulty, Int> = emptyMap(),
    val records: List<GameRecord> = emptyList(),
) {
    fun withStarted(difficulty: Difficulty) =
        Stats(started + (difficulty to (started[difficulty] ?: 0) + 1), records)

    fun withRecord(record: GameRecord) = Stats(started, (records + record).takeLast(MAX_RECORDS))

    fun of(difficulty: Difficulty): DifficultyStats {
        val solved = records.filter { it.difficulty == difficulty }
        var current = 0
        var best = 0
        for (record in solved) {
            current = if (record.flawless) current + 1 else 0
            best = maxOf(best, current)
        }
        return DifficultyStats(
            started = maxOf(started[difficulty] ?: 0, solved.size),
            solved = solved.size,
            bestSeconds = solved.filter { it.hints == 0 }.minOfOrNull { it.seconds },
            averageSeconds = if (solved.isEmpty()) null else solved.sumOf { it.seconds } / solved.size,
            flawless = solved.count { it.flawless },
            currentFlawlessStreak = current,
            bestFlawlessStreak = best,
            recentSeconds = solved.takeLast(RECENT).map { it.seconds },
        )
    }

    fun dailyRecord(day: Long): GameRecord? = records.lastOrNull { it.dailyDay == day }

    val dailySolved: Int get() = records.map { it.dailyDay }.filter { it >= 0 }.distinct().size

    /** Tage in Folge mit gelöstem täglichem Rätsel – bis heute oder, falls heute noch offen, bis gestern. */
    fun dailyStreak(today: Long): Int {
        val days = records.map { it.dailyDay }.filter { it >= 0 }.toSet()
        var day = if (today in days) today else today - 1
        var streak = 0
        while (day in days) {
            streak++
            day--
        }
        return streak
    }

    fun serialize(): String = listOf(
        VERSION,
        started.entries.joinToString(",") { "${it.key.name}=${it.value}" },
        records.joinToString(";") {
            "${it.difficulty.name},${it.seconds},${it.mistakes},${it.hints},${it.day},${it.dailyDay}"
        },
    ).joinToString("|")

    companion object {
        private const val VERSION = "1"
        private const val MAX_RECORDS = 2000
        const val RECENT = 20

        fun deserialize(text: String): Stats = runCatching {
            val parts = text.split("|")
            require(parts.size == 3 && parts[0] == VERSION)
            val started = parts[1].split(",").filter { it.isNotEmpty() }.associate {
                val (name, count) = it.split("=")
                Difficulty.valueOf(name) to count.toInt()
            }
            val records = parts[2].split(";").filter { it.isNotEmpty() }.map {
                val f = it.split(",")
                GameRecord(Difficulty.valueOf(f[0]), f[1].toLong(), f[2].toInt(), f[3].toInt(), f[4].toLong(), f[5].toLong())
            }
            Stats(started, records)
        }.getOrDefault(Stats())
    }
}
