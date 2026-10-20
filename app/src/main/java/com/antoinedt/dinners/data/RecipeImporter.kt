package com.antoinedt.dinners.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Downloads a recipe page and extracts the recipe from its schema.org JSON-LD,
 * which nearly every recipe site publishes. Falls back to the page title.
 */
object RecipeImporter {

    suspend fun import(url: String): Recipe = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36")
            .timeout(15_000)
            .followRedirects(true)
            .get()
        parse(doc, url)
    }

    fun parse(doc: Document, url: String): Recipe {
        val recipeJson = doc.select("script[type=application/ld+json]")
            .asSequence()
            .mapNotNull { runCatching { JSONTokener(it.data()).nextValue() }.getOrNull() }
            .mapNotNull { findRecipe(it) }
            .firstOrNull()

        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")

        if (recipeJson == null) {
            return Recipe(
                name = clean(ogTitle ?: doc.title()).ifBlank { url },
                url = url,
                imageUrl = ogImage?.takeIf { it.isNotBlank() },
            )
        }

        return Recipe(
            name = clean(recipeJson.optString("name")).ifBlank { clean(ogTitle ?: doc.title()) },
            url = url,
            imageUrl = image(recipeJson.opt("image")) ?: ogImage?.takeIf { it.isNotBlank() },
            ingredients = strings(recipeJson.opt("recipeIngredient") ?: recipeJson.opt("ingredients"))
                .joinToString("\n"),
            instructions = instructions(recipeJson.opt("recipeInstructions")).joinToString("\n"),
            notes = clean(recipeJson.optString("description")),
            servings = strings(recipeJson.opt("recipeYield")).firstOrNull(),
            totalTime = duration(recipeJson.optString("totalTime").ifBlank { recipeJson.optString("cookTime") }),
        )
    }

    private fun findRecipe(node: Any?): JSONObject? = when (node) {
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findRecipe(node.opt(it)) }
        is JSONObject -> when {
            isRecipe(node.opt("@type")) -> node
            node.has("@graph") -> findRecipe(node.opt("@graph"))
            node.has("mainEntity") -> findRecipe(node.opt("mainEntity"))
            else -> null
        }
        else -> null
    }

    private fun isRecipe(type: Any?): Boolean = when (type) {
        is String -> type.equals("Recipe", ignoreCase = true)
        is JSONArray -> (0 until type.length()).any { isRecipe(type.opt(it)) }
        else -> false
    }

    private fun strings(node: Any?): List<String> = when (node) {
        is JSONArray -> (0 until node.length()).flatMap { strings(node.opt(it)) }
        is String -> listOf(clean(node)).filter { it.isNotBlank() }
        is Number -> listOf(node.toString())
        else -> emptyList()
    }

    private fun instructions(node: Any?): List<String> = when (node) {
        is String -> clean(node).split(Regex("\\n+|(?<=\\.)\\s{2,}")).map { it.trim() }.filter { it.isNotBlank() }
        is JSONArray -> (0 until node.length()).flatMap { instructions(node.opt(it)) }
        is JSONObject -> when {
            node.has("itemListElement") -> {
                val section = clean(node.optString("name"))
                val steps = instructions(node.opt("itemListElement"))
                if (section.isNotBlank()) listOf("— $section —") + steps else steps
            }
            else -> listOf(clean(node.optString("text").ifBlank { node.optString("name") }))
                .filter { it.isNotBlank() }
        }
        else -> emptyList()
    }

    private fun image(node: Any?): String? = when (node) {
        is String -> node.takeIf { it.isNotBlank() }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { image(node.opt(it)) }
        is JSONObject -> node.optString("url").takeIf { it.isNotBlank() }
        else -> null
    }

    /** Turns ISO-8601 durations like PT1H15M into "1 h 15 min". */
    internal fun duration(iso: String): String? {
        if (iso.isBlank()) return null
        val m = Regex("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?").find(iso) ?: return iso
        val (d, h, min) = m.destructured
        val totalH = (d.toIntOrNull() ?: 0) * 24 + (h.toIntOrNull() ?: 0)
        val parts = buildList {
            if (totalH > 0) add("$totalH h")
            if ((min.toIntOrNull() ?: 0) > 0) add("${min.toInt()} min")
        }
        return parts.joinToString(" ").ifBlank { null }
    }

    private fun clean(s: String): String =
        Jsoup.parse(s).text().replace(' ', ' ').trim()
}
