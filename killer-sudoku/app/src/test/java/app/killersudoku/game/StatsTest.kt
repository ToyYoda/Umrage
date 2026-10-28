package app.killersudoku.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private fun record(seconds: Long, mistakes: Int = 0, hints: Int = 0, day: Long = 100, daily: Long = -1) =
        GameRecord(Difficulty.MEDIUM, seconds, mistakes, hints, day, daily)

    @Test
    fun computesPerDifficulty() {
        val stats = Stats()
            .withStarted(Difficulty.MEDIUM).withStarted(Difficulty.MEDIUM).withStarted(Difficulty.MEDIUM)
            .withRecord(record(300))
            .withRecord(record(200, hints = 1))
            .withRecord(record(400, mistakes = 2))
            .withRecord(record(250))
            .withRecord(record(350))
        val medium = stats.of(Difficulty.MEDIUM)
        assertEquals(5, medium.started)
        assertEquals(5, medium.solved)
        assertEquals(250L, medium.bestSeconds)
        assertEquals(300L, medium.averageSeconds)
        assertEquals(3, medium.flawless)
        assertEquals(2, medium.currentFlawlessStreak)
        assertEquals(2, medium.bestFlawlessStreak)
        assertEquals(listOf(300L, 200L, 400L, 250L, 350L), medium.recentSeconds)
        assertNull(stats.of(Difficulty.EXPERT).bestSeconds)
    }

    @Test
    fun dailyStreak() {
        val stats = Stats()
            .withRecord(record(1, daily = 10))
            .withRecord(record(1, daily = 11))
            .withRecord(record(1, daily = 12))
            .withRecord(record(1, daily = 14))
        assertEquals(1, stats.dailyStreak(14))
        assertEquals(1, stats.dailyStreak(15))
        assertEquals(3, stats.dailyStreak(13))
        assertEquals(0, stats.dailyStreak(17))
        assertEquals(4, stats.dailySolved)
        assertTrue(stats.dailyRecord(12) != null)
    }

    @Test
    fun serializationRoundTrip() {
        val stats = Stats().withStarted(Difficulty.HARD).withRecord(record(77, 1, 2, 5, 5))
        val restored = Stats.deserialize(stats.serialize())
        assertEquals(stats.serialize(), restored.serialize())
        assertEquals(0, Stats.deserialize("kaputt").records.size)
        assertEquals(0, Stats.deserialize(Stats().serialize()).records.size)
    }

    @Test
    fun neighbouringCagesHaveDifferentColors() {
        repeat(5) { seed ->
            val puzzle = Generator.generateUnique(Difficulty.HARD, Random(seed))
            val colors = CageColors.assign(puzzle)
            assertTrue(colors.all { it in 0 until CageColors.COUNT })
            for (cell in 0 until 81) for (n in Grid.neighbours[cell]) {
                val a = puzzle.cageOf[cell]
                val b = puzzle.cageOf[n]
                if (a != b) assertTrue(colors[a] != colors[b])
            }
        }
    }
}
