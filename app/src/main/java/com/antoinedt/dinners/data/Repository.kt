package com.antoinedt.dinners.data

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
                .onFailure { warning = "Couldn't read the recipe page (${it.message}). Saved the link only." }
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
        val url = recipe.url ?: return "This recipe has no website."
        return runCatching { RecipeImporter.import(url) }.fold(
            onSuccess = {
                db.recipeDao().update(
                    it.copy(id = recipe.id, name = recipe.name, createdAt = recipe.createdAt,
                        notes = recipe.notes.ifBlank { it.notes })
                )
                null
            },
            onFailure = { "Couldn't read the recipe page (${it.message})." },
        )
    }

    suspend fun updateRecipe(recipe: Recipe) = db.recipeDao().update(recipe)
    suspend fun deleteRecipe(recipe: Recipe) = db.recipeDao().delete(recipe)
}
