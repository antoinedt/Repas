package ca.antoinedt.suivicnd

import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class SyncException(message: String) : Exception(message)

/**
 * Drives the PedNET portal inside a WebView: logs in, opens
 * "Suivi de l'élève", reads the list and (best effort) each entry's detail.
 *
 * The portal's links are encrypted, session-bound tokens, so we act like a
 * person would: fill the login form, click the menu item, read the table.
 * The JavaScript lives in assets/js so it can be tested on its own.
 * Everything here must run on the main thread.
 */
class PortalScraper(
    private val web: WebView,
    private val log: (String) -> Unit,
) {
    private data class Row(
        val domIndex: Int,
        val date: String,
        val type: String,
        val group: String,
        val teacher: String,
    ) {
        val key: String get() = keyOf(date, type, teacher)
    }

    private val assets = web.context.assets
    private fun script(name: String): String =
        assets.open("js/$name.js").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private val helpers = script("helpers")
    private var lastAlert: String? = null

    init {
        web.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?, url: String?, message: String?, result: JsResult?,
            ): Boolean {
                lastAlert = message
                log("Message du portail : $message")
                result?.confirm()
                return true
            }

            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?, result: JsResult?,
            ): Boolean {
                log("Confirmation demandée (refusée) : $message")
                result?.cancel()
                return true
            }
        }
    }

    // ------------------------------------------------------------ JS bridge

    private suspend fun js(body: String): String =
        suspendCancellableCoroutine { cont ->
            val wrapped = "(function(){try{\n$helpers\n$body\n}catch(e){return 'ERR:'+e.message;}})()"
            web.evaluateJavascript(wrapped) { raw ->
                if (cont.isActive) cont.resume(decode(raw))
            }
        }

    private fun decode(raw: String?): String {
        if (raw == null || raw == "null" || raw == "undefined") return ""
        return if (raw.startsWith("\"")) JSONArray("[$raw]").getString(0) else raw
    }

    private suspend fun waitFor(condition: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (js("return ($condition) ? '1' : '0';") == "1") return true
            delay(400)
        }
        return false
    }

    // ------------------------------------------------------------ flow

    suspend fun sync(
        baseUrl: String,
        user: String,
        pass: String,
        known: Map<String, SuiviItem>,
    ): List<SuiviItem> {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        log("Ouverture du portail…")
        web.loadUrl(base + "login.asp")
        delay(800)
        val ready = waitFor(
            "document.readyState == 'complete' && " +
                "(!!document.querySelector('input[type=password]') || !!document.querySelector('#lnkSUIVI'))",
            30_000,
        )
        if (!ready) throw SyncException("Le portail ne répond pas. Vérifiez la connexion Internet.")

        if (js("return document.querySelector('#lnkSUIVI') ? '1' : '0';") == "1") {
            log("Session déjà ouverte.")
        } else {
            login(user, pass)
        }

        log("Ouverture de « Suivi de l'élève »…")
        if (!openSuivi()) throw SyncException("La section « Suivi de l'élève » n'a pas pu être ouverte.")

        val rows = readRows()
        log("${rows.size} entrées trouvées.")

        val needDetail = rows.count { known[it.key]?.detail == null }
        var done = 0
        var failures = 0
        val result = mutableListOf<SuiviItem>()
        for (r in rows) {
            var detail = known[r.key]?.detail
            if (detail == null && failures < 2) {
                done++
                log("Détails $done/$needDetail…")
                detail = try {
                    fetchDetail(r.key)
                } catch (e: Exception) {
                    log("Détail : ${e.message}")
                    null
                }
                if (detail == null) failures++ else failures = 0
                if (!ensureGrid()) {
                    log("Impossible de revenir à la liste ; détails restants ignorés.")
                    failures = 99
                }
            }
            result += SuiviItem(r.key, r.date, r.type, r.group, r.teacher, detail)
        }
        if (failures >= 2 && needDetail > 0) {
            log("Certains détails n'ont pas pu être lus ; la liste est quand même à jour.")
        }
        return result
    }

    private suspend fun login(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            throw SyncException("Identifiants manquants : ouvrez les paramètres.")
        }
        log("Connexion…")
        lastAlert = null
        val how = js(
            script("login")
                .replace("__USER__", JSONObject.quote(user))
                .replace("__PASS__", JSONObject.quote(pass))
        )
        log("Formulaire envoyé ($how).")
        if (how.startsWith("ERR") || how == "nopw" || how == "nouser") {
            throw SyncException("Formulaire de connexion introuvable ($how).")
        }
        if (!waitFor("!!document.querySelector('#lnkSUIVI')", 40_000)) {
            val alert = lastAlert
            val still = js("return document.querySelector('input[type=password]') ? '1' : '0';") == "1"
            throw SyncException(
                when {
                    alert != null -> "Connexion refusée : $alert"
                    still -> "Connexion refusée. Vérifiez l'identifiant et le mot de passe."
                    else -> "Connexion réussie, mais le menu « Suivi » est introuvable. " +
                        "Activez « Afficher le navigateur » dans les paramètres pour voir la page."
                }
            )
        }
        log("Connecté.")
    }

    private suspend fun openSuivi(): Boolean {
        js("var a = document.querySelector('#lnkSUIVI'); if (a) { a.click(); return 'ok'; } return 'no';")
        val ok = waitFor(
            """!!document.querySelector(G) || !!document.querySelector('[id$="sectionAccCommentaires"]')""",
            30_000,
        )
        delay(700)
        return ok
    }

    private suspend fun readRows(): List<Row> {
        val raw = js(script("rows"))
        if (raw.startsWith("ERR")) throw SyncException("Lecture de la liste impossible ($raw).")
        val a = JSONArray(raw.ifBlank { "[]" })
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Row(
                domIndex = o.getInt("i"),
                date = o.getString("date"),
                type = o.getString("type"),
                group = o.optString("group"),
                teacher = o.optString("teacher"),
            )
        }
    }

    /** Clicks the row's "open" icon and returns whatever detail view appears. */
    private suspend fun fetchDetail(key: String): String? {
        val row = readRows().firstOrNull { it.key == key } ?: return null
        val clicked = js(script("click_detail").replace("__IDX__", row.domIndex.toString()))
        if (clicked != "ok") {
            log("Détail : bouton introuvable ($clicked).")
            return null
        }
        val detect = script("detect")
        var found = ""
        val end = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < end) {
            delay(400)
            val r = js(detect)
            if (r.isNotEmpty() && !r.startsWith("ERR")) {
                found = r
                break
            }
        }
        if (found.isEmpty()) {
            log("Détail : aucune fenêtre détectée.")
            return null
        }
        // Give the popup a moment to finish loading, then read it again.
        delay(700)
        val again = js(detect)
        if (again.isNotEmpty() && !again.startsWith("ERR")) found = again

        if (!found.startsWith("R:")) {
            js(script("close"))
            delay(500)
        }
        return clean(found.substring(2)).ifBlank { null }
    }

    private suspend fun ensureGrid(): Boolean {
        if (waitFor("vis(document.querySelector(G))", 3_000)) return true
        log("Retour à la liste…")
        val hasMenu = js("return document.querySelector('#lnkSUIVI') ? '1' : '0';") == "1"
        if (!hasMenu && web.canGoBack()) {
            web.goBack()
            delay(1_500)
            if (waitFor("vis(document.querySelector(G))", 5_000)) return true
        }
        return openSuivi() && waitFor("vis(document.querySelector(G))", 5_000)
    }

    private fun clean(text: String): String {
        val iconLike = Regex("^[a-z]+(_[a-z]+)+$")
        val icons = setOf(
            "close", "menu", "home", "event", "folder", "group", "link", "list",
            "email", "today", "feedback", "settings", "clear", "done", "check",
            "search", "print", "edit", "info", "save", "add", "remove",
        )
        val out = mutableListOf<String>()
        for (line in text.lines().map { it.trim() }) {
            if (line.isEmpty() || line in icons || iconLike.matches(line)) continue
            if (out.lastOrNull() == line) continue
            out += line
        }
        return out.joinToString("\n")
    }

    companion object {
        fun keyOf(date: String, type: String, teacher: String) = "$date|$type|$teacher"
    }
}
