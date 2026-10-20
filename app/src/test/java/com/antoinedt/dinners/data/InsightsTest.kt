package com.antoinedt.dinners.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val pasta = Recipe(id = 1, name = "Pasta")
    private val curry = Recipe(id = 2, name = "Curry")
    private val tacos = Recipe(id = 3, name = "Tacos")

    private fun entry(id: Long, daysAgo: Long, r: Recipe) =
        DinnerEntry(id, today.minusDays(daysAgo).toEpochDay(), r.id, r.name)

    @Test
    fun statsComputesPercentages() {
        val dinners = listOf(entry(1, 1, pasta), entry(2, 2, pasta), entry(3, 3, pasta), entry(4, 4, curry))
        val stats = Insights.stats(dinners, today.minusDays(30), today, monthsBack = 3)
        assertEquals(4, stats.totalDinners)
        assertEquals("Pasta", stats.shares.first().name)
        assertEquals(75.0, stats.shares.first().pct, 0.001)
        assertEquals(3, stats.months.size)
    }

    @Test
    fun suggestionsAvoidRecentAndFavourDue() {
        val dinners = listOf(
            // Curry every ~7 days, last 14 days ago: overdue.
            entry(1, 35, curry), entry(2, 28, curry), entry(3, 21, curry), entry(4, 14, curry),
            // Pasta eaten yesterday.
            entry(5, 1, pasta), entry(6, 8, pasta), entry(7, 15, pasta),
        )
        val ranked = Insights.suggest(listOf(pasta, curry, tacos), dinners, today)
        assertEquals("Curry", ranked.first().recipe.name)
        assertEquals("Pasta", ranked.last().recipe.name)
        assertTrue(ranked.any { it.recipe.name == "Tacos" })
    }

    @Test
    fun isoDurations() {
        assertEquals("1 h 15 min", RecipeImporter.duration("PT1H15M"))
        assertEquals("45 min", RecipeImporter.duration("PT45M"))
    }
}
