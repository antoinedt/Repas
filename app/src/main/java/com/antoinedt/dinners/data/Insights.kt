package com.antoinedt.dinners.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ln
import kotlin.math.min
import kotlin.random.Random

data class RecipeShare(val recipeId: Long, val name: String, val count: Int, val pct: Double)

data class MonthBucket(val month: YearMonth, val counts: Map<Long, Int>) {
    val total: Int get() = counts.values.sum()
}

data class Stats(
    val totalDinners: Int,
    val distinctRecipes: Int,
    val daysInPeriod: Int,
    val daysLogged: Int,
    val shares: List<RecipeShare>,
    val months: List<MonthBucket>,
    /** Most frequent recipe for each weekday, with its count. */
    val byWeekday: Map<DayOfWeek, Pair<String, Int>>,
)

data class Suggestion(val recipe: Recipe, val score: Double, val reasons: List<String>)

object Insights {

    /** Stats for dinners in [from]..[to] (inclusive). [from] null means all-time. */
    fun stats(dinners: List<DinnerEntry>, from: LocalDate?, to: LocalDate, monthsBack: Int): Stats {
        val start = from ?: dinners.minOfOrNull { LocalDate.ofEpochDay(it.epochDay) } ?: to
        val inRange = dinners.filter { it.epochDay in start.toEpochDay()..to.toEpochDay() }
        val total = inRange.size
        val shares = inRange.groupBy { it.recipeId }
            .map { (id, list) ->
                RecipeShare(id, list.first().recipeName, list.size, if (total == 0) 0.0 else list.size * 100.0 / total)
            }
            .sortedWith(compareByDescending<RecipeShare> { it.count }.thenBy { it.name.lowercase() })

        val lastMonth = YearMonth.from(to)
        val months = (monthsBack - 1 downTo 0).map { back ->
            val ym = lastMonth.minusMonths(back.toLong())
            val counts = dinners.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == ym }
                .groupingBy { it.recipeId }.eachCount()
            MonthBucket(ym, counts)
        }

        val byWeekday = inRange.groupBy { LocalDate.ofEpochDay(it.epochDay).dayOfWeek }
            .mapValues { (_, list) ->
                list.groupBy { it.recipeName }.maxBy { it.value.size }.let { it.key to it.value.size }
            }

        return Stats(
            totalDinners = total,
            distinctRecipes = shares.size,
            daysInPeriod = (to.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1),
            daysLogged = inRange.map { it.epochDay }.distinct().size,
            shares = shares,
            months = months,
            byWeekday = byWeekday,
        )
    }

    /**
     * Ranks recipes for [day] from eating history:
     *  - favourites (eaten often) score higher,
     *  - recipes "due" again relative to their usual gap score higher,
     *  - anything eaten in the last few days is pushed down,
     *  - recipes often eaten on that weekday get a bonus,
     *  - saved-but-never-cooked recipes get a modest "try something new" score.
     * [jitter] > 0 adds randomness so "shuffle" gives different picks.
     */
    fun suggest(
        recipes: List<Recipe>,
        dinners: List<DinnerEntry>,
        day: LocalDate,
        jitter: Double = 0.0,
        random: Random = Random.Default,
    ): List<Suggestion> {
        val today = day.toEpochDay()
        val history = dinners.filter { it.epochDay < today }.groupBy { it.recipeId }
        val alreadyPlanned = dinners.filter { it.epochDay == today }.map { it.recipeId }.toSet()

        return recipes.filter { it.id !in alreadyPlanned }.map { recipe ->
            val days = history[recipe.id].orEmpty().map { it.epochDay }.distinct().sorted()
            val reasons = mutableListOf<String>()
            var score: Double

            if (days.isEmpty()) {
                score = 1.2
                reasons += "Saved but never cooked — try something new"
            } else {
                val count = days.size
                val daysSince = today - days.last()
                score = ln(1.0 + count)
                if (count >= 3) reasons += "A favourite: eaten $count times"

                val avgGap = if (count >= 2) (days.last() - days.first()).toDouble() / (count - 1) else 21.0
                val due = min(daysSince / avgGap.coerceAtLeast(1.0), 3.0)
                score += 1.5 * due
                when {
                    due >= 1.0 && count >= 2 ->
                        reasons += "Usually every ${avgGap.toInt()} days — last had $daysSince days ago"
                    daysSince >= 30 -> reasons += "Not had for $daysSince days"
                    else -> reasons += "Last had $daysSince days ago"
                }

                if (daysSince <= 2) score -= 5.0 else if (daysSince <= 6) score -= 2.0

                if (count >= 3) {
                    val sameWeekday = days.count { LocalDate.ofEpochDay(it).dayOfWeek == day.dayOfWeek }
                    val share = sameWeekday.toDouble() / count
                    score += 3.0 * (share - 1.0 / 7)
                    if (share >= 0.35) {
                        reasons += "Often a ${day.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} dinner"
                    }
                }
            }
            if (jitter > 0) score += random.nextDouble() * jitter
            Suggestion(recipe, score, reasons)
        }.sortedByDescending { it.score }
    }
}
