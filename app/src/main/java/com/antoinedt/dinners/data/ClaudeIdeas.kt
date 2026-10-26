package com.antoinedt.dinners.data

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DinnerIdea(
    val date: LocalDate,
    val name: String,
    val why: String,
    val url: String?,
    val labels: List<String>,
) {
    fun toJson(): String = JSONObject()
        .put("date", date.toString())
        .put("nom", name)
        .put("pourquoi", why)
        .put("url", url ?: JSONObject.NULL)
        .put("etiquettes", JSONArray(labels))
        .toString()

    companion object {
        fun fromJson(json: String, date: LocalDate? = null): DinnerIdea {
            val o = JSONObject(json)
            val labels = o.optJSONArray("etiquettes")
            return DinnerIdea(
                date = date ?: LocalDate.parse(o.getString("date")),
                name = o.getString("nom").trim(),
                why = o.optString("pourquoi").trim(),
                url = o.optString("url").trim().takeIf { it.startsWith("http") },
                labels = if (labels == null) emptyList()
                else (0 until labels.length()).map { labels.optString(it).trim() }.filter { it.isNotEmpty() },
            )
        }
    }
}

/** Asks Claude (with web search) for one dinner idea that fits the household's history. */
object ClaudeIdeas {
    const val MODEL = "claude-opus-5-5"

    private const val SYSTEM =
        "Tu aides un foyer à choisir son dîner. Tu réponds toujours en français, brièvement."

    fun prompt(recipes: List<Recipe>, dinners: List<DinnerEntry>, day: LocalDate): String {
        val today = day.toEpochDay()
        val counts = dinners.groupingBy { it.recipeId }.eachCount()
        val byId = recipes.associateBy { it.id }
        val favourites = counts.entries.sortedByDescending { it.value }.take(15).mapNotNull { (id, n) ->
            byId[id]?.let { r ->
                "- ${r.name} ($n fois" + (if (r.labelList.isEmpty()) "" else ", ${r.labelList.joinToString()}") + ")"
            }
        }
        val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)
        val recent = dinners.filter { it.epochDay in (today - 14) until today }
            .sortedBy { it.epochDay }
            .map { "- ${LocalDate.ofEpochDay(it.epochDay).format(dateFmt)} : ${it.recipeName}" }
        val labels = allLabels(recipes)
        val never = recipes.filter { counts[it.id] == null }.take(10).map { "- ${it.name}" }

        return buildString {
            appendLine("Nous sommes le ${day.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH))}.")
            appendLine("Propose UNE idée de dîner pour ce soir.")
            appendLine()
            appendLine("Nos plats les plus fréquents :")
            appendLine(favourites.ifEmpty { listOf("- (pas encore d'historique)") }.joinToString("\n"))
            appendLine()
            appendLine("Nos repas des 14 derniers jours :")
            appendLine(recent.ifEmpty { listOf("- (rien de noté)") }.joinToString("\n"))
            if (labels.isNotEmpty()) {
                appendLine()
                appendLine("Étiquettes que nous utilisons : ${labels.joinToString()}")
            }
            if (never.isNotEmpty()) {
                appendLine()
                appendLine("Recettes enregistrées mais jamais cuisinées :")
                appendLine(never.joinToString("\n"))
            }
            appendLine()
            appendLine("Consignes :")
            appendLine("- L'idée doit correspondre à nos goûts (style de plats, étiquettes habituelles) tout en variant : pas un plat mangé ces 7 derniers jours. Ce peut être une nouveauté ou un de nos plats pas fait depuis longtemps.")
            appendLine("- Cherche sur le web une vraie page de recette en français pour ce plat et donne son adresse exacte telle qu'elle apparaît dans les résultats de recherche.")
            appendLine("- Si c'est un plat de notre liste, reprends exactement son nom.")
            appendLine("- Choisis des étiquettes parmi les nôtres quand elles s'appliquent.")
            append("""- Termine ta réponse par ce JSON seul, sans rien après : {"nom": "...", "pourquoi": "une phrase", "url": "https://...", "etiquettes": ["..."]}""")
        }
    }

    /** Extracts the final JSON object from Claude's answer. */
    fun parse(text: String, day: LocalDate): DinnerIdea {
        val end = text.lastIndexOf('}')
        // The idea object contains no nested braces, so it is the last {...} block.
        val start = if (end < 0) -1 else text.lastIndexOf('{', end)
        require(start in 0 until end) { "Réponse de Claude illisible." }
        return DinnerIdea.fromJson(text.substring(start, end + 1), day)
    }

    suspend fun fetch(apiKey: String, recipes: List<Recipe>, dinners: List<DinnerEntry>, day: LocalDate): DinnerIdea =
        withContext(Dispatchers.IO) {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
            try {
                val params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(16000L)
                    .system(SYSTEM)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                    .addTool(WebSearchTool20260209.builder().maxUses(5L).build())
                    // If a safety classifier declines, let the API retry on a fallback model.
                    .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .addUserMessage(prompt(recipes, dinners, day))

                var response = client.messages().create(params.build())
                // Long web searches can pause the turn; send the partial answer back to continue.
                var continuations = 0
                while (response.stopReason().orElse(null) == StopReason.PAUSE_TURN && continuations++ < 3) {
                    params.addMessage(response)
                    response = client.messages().create(params.build())
                }
                if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                    error("Claude n'a pas pu répondre à cette demande.")
                }
                val text = response.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
                parse(text, day)
            } finally {
                client.close()
            }
        }
}
