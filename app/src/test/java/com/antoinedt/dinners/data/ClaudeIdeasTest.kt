package com.antoinedt.dinners.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ClaudeIdeasTest {
    private val day = LocalDate.of(2026, 10, 26)

    @Test
    fun parsesTrailingJson() {
        val answer = """Voici une idée qui change un peu.
            {"nom": "Gratin de courge", "pourquoi": "De saison et végétarien.", "url": "https://www.example.fr/gratin", "etiquettes": ["Végétarien"]}"""
        val idea = ClaudeIdeas.parse(answer, day)
        assertEquals("Gratin de courge", idea.name)
        assertEquals("https://www.example.fr/gratin", idea.url)
        assertEquals(listOf("Végétarien"), idea.labels)
        assertEquals(idea, DinnerIdea.fromJson(idea.toJson()))
    }

    @Test
    fun promptMentionsHistory() {
        val curry = Recipe(id = 1, name = "Curry").withLabels(listOf("Épicé"))
        val dinners = listOf(DinnerEntry(1, day.toEpochDay() - 2, 1, "Curry"))
        val prompt = ClaudeIdeas.prompt(listOf(curry), dinners, day)
        assertTrue(prompt.contains("Curry (1 fois, Épicé)"))
        assertTrue(prompt.contains("lundi 26 octobre 2026"))
    }
}
