package com.antoinedt.dinners.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Readable JSON backup: recipes are written out in full and dinners reference recipes by name,
 * so the file can be shared, read, or merged into another phone.
 */
object Backup {
    const val FORMAT = "repas"

    data class Content(val recipes: List<Recipe>, val dinners: List<Pair<LocalDate, String>>)

    fun toJson(recipes: List<Recipe>, dinners: List<DinnerEntry>, today: LocalDate = LocalDate.now()): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", 1)
        root.put("exportedOn", today.toString())
        root.put("recipes", JSONArray().apply {
            recipes.forEach { r ->
                put(JSONObject().apply {
                    put("name", r.name)
                    r.url?.let { put("url", it) }
                    r.imageUrl?.let { put("imageUrl", it) }
                    put("labels", JSONArray(r.labelList))
                    put("ingredients", JSONArray(r.ingredientList))
                    put("instructions", JSONArray(r.instructionList))
                    if (r.notes.isNotBlank()) put("notes", r.notes)
                    r.servings?.let { put("servings", it) }
                    r.totalTime?.let { put("totalTime", it) }
                })
            }
        })
        root.put("dinners", JSONArray().apply {
            dinners.sortedBy { it.epochDay }.forEach { d ->
                put(JSONObject().apply {
                    put("date", LocalDate.ofEpochDay(d.epochDay).toString())
                    put("recipe", d.recipeName)
                })
            }
        })
        return root.toString(2)
    }

    fun parse(json: String): Content {
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "Ce fichier n'est pas une sauvegarde Repas." }
        val recipes = root.optJSONArray("recipes").objects().map { o ->
            Recipe(
                name = o.getString("name").trim(),
                url = o.optStringOrNull("url"),
                imageUrl = o.optStringOrNull("imageUrl"),
                ingredients = o.optJSONArray("ingredients").strings().joinToString("\n"),
                instructions = o.optJSONArray("instructions").strings().joinToString("\n"),
                notes = o.optString("notes"),
                servings = o.optStringOrNull("servings"),
                totalTime = o.optStringOrNull("totalTime"),
            ).withLabels(o.optJSONArray("labels").strings())
        }.filter { it.name.isNotEmpty() }
        val dinners = root.optJSONArray("dinners").objects().map { o ->
            LocalDate.parse(o.getString("date")) to o.getString("recipe").trim()
        }
        return Content(recipes, dinners)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}
