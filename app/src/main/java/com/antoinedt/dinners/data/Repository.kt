package com.antoinedt.dinners.data

import androidx.room.withTransaction
import java.time.LocalDate

class Repository(private val db: AppDatabase) {
    val recipes = db.recipeDao().observeAll()
    val dinners = db.dinnerDao().observeAll()

    fun recipe(id: Long) = db.recipeDao().observe(id)

    suspend fun addDinner(date: LocalDate, recipeId: Long) {
        db.dinnerDao().insert(Dinner(epochDay = date.toEpochDay(), recipeId = recipeId))
    }

    suspend fun removeDinner(dinnerId: Long) = db.dinnerDao().delete(dinnerId)

    /**
     * Creates a recipe (or reuses one with the same name). When [url] is given the page is
     * downloaded and the recipe content saved locally; a failed download still saves the link.
     * Returns the recipe id and an optional warning message.
     */
    suspend fun createRecipe(name: String, url: String?): Pair<Long, String?> {
        val link = url?.trim()?.takeIf { it.isNotBlank() }?.let { if ("://" in it) it else "https://$it" }
        var warning: String? = null
        val recipe = if (link != null) {
            runCatching { RecipeImporter.import(link) }
                .onFailure { warning = "Impossible de lire la page de la recette (${it.message}). Seul le lien a été enregistré." }
                .getOrNull()
                ?.let { imported -> if (name.isNotBlank()) imported.copy(name = name.trim()) else imported }
                ?: Recipe(name = name.trim().ifBlank { link }, url = link)
        } else {
            Recipe(name = name.trim())
        }
        if (link == null) db.recipeDao().findByName(recipe.name)?.let { return it.id to null }
        return db.recipeDao().insert(recipe) to warning
    }

    /** Re-downloads the recipe content from its website. */
    suspend fun refreshFromWebsite(recipe: Recipe): String? {
        val url = recipe.url ?: return "Cette recette n'a pas de site web."
        return runCatching { RecipeImporter.import(url) }.fold(
            onSuccess = {
                db.recipeDao().update(
                    it.copy(id = recipe.id, name = recipe.name, createdAt = recipe.createdAt, labels = recipe.labels,
                        notes = recipe.notes.ifBlank { it.notes })
                )
                null
            },
            onFailure = { "Impossible de lire la page de la recette (${it.message})." },
        )
    }

    suspend fun updateRecipe(recipe: Recipe) = db.recipeDao().update(recipe)
    suspend fun deleteRecipe(recipe: Recipe) = db.recipeDao().delete(recipe)

    /** Full backup read straight from the database. */
    suspend fun backupJson(): String {
        val recipes = db.recipeDao().getAll().sortedBy { it.name.lowercase() }
        val names = recipes.associate { it.id to it.name }
        val dinners = db.dinnerDao().getAll().mapNotNull { d ->
            names[d.recipeId]?.let { DinnerEntry(d.id, d.epochDay, d.recipeId, it) }
        }
        return Backup.toJson(recipes, dinners)
    }

    /**
     * Merges a backup into the database: recipes are matched by name (missing labels and details
     * are filled in), dinners already logged for the same day and recipe are skipped.
     * Returns (recipes added, dinners added).
     */
    suspend fun importBackup(content: Backup.Content): Pair<Int, Int> = db.withTransaction {
        val recipeDao = db.recipeDao()
        val byName = recipeDao.getAll().associateBy { it.name.lowercase() }.toMutableMap()
        var recipesAdded = 0
        content.recipes.forEach { incoming ->
            val existing = byName[incoming.name.lowercase()]
            if (existing == null) {
                val id = recipeDao.insert(incoming.copy(id = 0))
                byName[incoming.name.lowercase()] = incoming.copy(id = id)
                recipesAdded++
            } else {
                val merged = existing.copy(
                    url = existing.url ?: incoming.url,
                    imageUrl = existing.imageUrl ?: incoming.imageUrl,
                    ingredients = existing.ingredients.ifBlank { incoming.ingredients },
                    instructions = existing.instructions.ifBlank { incoming.instructions },
                    notes = existing.notes.ifBlank { incoming.notes },
                    servings = existing.servings ?: incoming.servings,
                    totalTime = existing.totalTime ?: incoming.totalTime,
                ).withLabels(existing.labelList + incoming.labelList)
                if (merged != existing) recipeDao.update(merged)
                byName[incoming.name.lowercase()] = merged
            }
        }
        val dinnerDao = db.dinnerDao()
        val seen = dinnerDao.getAll().map { it.epochDay to it.recipeId }.toMutableSet()
        var dinnersAdded = 0
        content.dinners.forEach { (date, name) ->
            val recipe = byName[name.lowercase()] ?: recipeDao.insert(Recipe(name = name)).let { id ->
                Recipe(id = id, name = name).also { byName[name.lowercase()] = it; recipesAdded++ }
            }
            if (seen.add(date.toEpochDay() to recipe.id)) {
                dinnerDao.insert(Dinner(epochDay = date.toEpochDay(), recipeId = recipe.id))
                dinnersAdded++
            }
        }
        recipesAdded to dinnersAdded
    }
}
