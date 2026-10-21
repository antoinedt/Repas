package com.antoinedt.dinners.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    @Test
    fun roundTrip() {
        val pasta = Recipe(id = 1, name = "Pâtes carbonara", url = "https://example.com/carbo",
            ingredients = "pâtes\nlardons", instructions = "Cuire\nMélanger", servings = "4")
            .withLabels(listOf("Rapide", "Pâtes"))
        val day = LocalDate.of(2026, 10, 20)
        val json = Backup.toJson(listOf(pasta), listOf(DinnerEntry(1, day.toEpochDay(), 1, pasta.name)), day)
        assertTrue(json.contains("\"date\": \"2026-10-20\""))

        val parsed = Backup.parse(json)
        val r = parsed.recipes.single()
        assertEquals(pasta.name, r.name)
        assertEquals(listOf("Rapide", "Pâtes"), r.labelList)
        assertEquals(pasta.ingredients, r.ingredients)
        assertEquals("4", r.servings)
        assertEquals(listOf(day to pasta.name), parsed.dinners)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherJson() {
        Backup.parse("{\"hello\": 1}")
    }

    @Test
    fun labelShares() {
        val a = Recipe(id = 1, name = "A").withLabels(listOf("Végé", "Rapide"))
        val b = Recipe(id = 2, name = "B").withLabels(listOf("Végé"))
        val c = Recipe(id = 3, name = "C")
        val today = LocalDate.of(2026, 10, 20)
        val dinners = listOf(a, b, c, b).mapIndexed { i, r -> DinnerEntry(i.toLong(), today.toEpochDay() - i, r.id, r.name) }
        val shares = Insights.labelShares(dinners, listOf(a, b, c), null, today).associate { it.label to it.count }
        assertEquals(mapOf("Végé" to 3, "Rapide" to 1, Insights.NO_LABEL to 1), shares)
    }
}
