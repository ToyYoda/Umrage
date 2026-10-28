package app.killersudoku.game

import java.time.DayOfWeek
import java.time.LocalDate

/** Rätsel-Codes wie „M-123456“: Schwierigkeitsgrad + Seed. Gleicher Code ergibt auf jedem Gerät dasselbe Rätsel. */
object PuzzleCode {
    const val SEEDS = 1_000_000

    fun format(difficulty: Difficulty, seed: Int) = "${difficulty.letter}-${seed.toString().padStart(6, '0')}"

    /** @return Schwierigkeitsgrad und Seed oder null, wenn der Text kein gültiger Code ist. */
    fun parse(text: String): Pair<Difficulty, Int>? {
        val match = Regex("""^\s*([A-Za-z])\s*-?\s*(\d{1,6})\s*$""").find(text) ?: return null
        val letter = match.groupValues[1].uppercase()[0]
        val difficulty = Difficulty.entries.firstOrNull { it.letter == letter } ?: return null
        return difficulty to match.groupValues[2].toInt()
    }

    /** Tägliches Rätsel: unter der Woche leichter, am Wochenende schwerer. */
    fun daily(date: LocalDate): Pair<Difficulty, Int> {
        val difficulty = when (date.dayOfWeek) {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> Difficulty.EASY
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> Difficulty.MEDIUM
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> Difficulty.HARD
            DayOfWeek.SUNDAY -> Difficulty.EXPERT
        }
        val seed = Math.floorMod(date.toEpochDay() * 7_919L + 104_729L, SEEDS.toLong()).toInt()
        return difficulty to seed
    }
}
