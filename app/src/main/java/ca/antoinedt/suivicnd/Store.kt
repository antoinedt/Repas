package ca.antoinedt.suivicnd

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class SuiviItem(
    val key: String,
    val date: String,
    val type: String,
    val group: String,
    val teacher: String,
    val detail: String?,
)

/** Credentials are kept in encrypted prefs; everything else in plain prefs. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("data", Context.MODE_PRIVATE)

    private val secure = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var user: String
        get() = secure.getString("user", "") ?: ""
        set(v) {
            secure.edit().putString("user", v).apply()
        }

    var pass: String
        get() = secure.getString("pass", "") ?: ""
        set(v) {
            secure.edit().putString("pass", v).apply()
        }

    var portalUrl: String
        get() = prefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL
        set(v) {
            prefs.edit().putString("url", v).apply()
        }

    var showBrowser: Boolean
        get() = prefs.getBoolean("showBrowser", false)
        set(v) {
            prefs.edit().putBoolean("showBrowser", v).apply()
        }

    var lastSync: Long
        get() = prefs.getLong("lastSync", 0L)
        set(v) {
            prefs.edit().putLong("lastSync", v).apply()
        }

    var unread: Set<String>
        get() = prefs.getStringSet("unread", emptySet())?.toSet() ?: emptySet()
        set(v) {
            prefs.edit().putStringSet("unread", v.toSet()).apply()
        }

    /** nameKey(teacher) -> subject, from the portal's "Cours" page. */
    var subjects: Map<String, String>
        get() = try {
            val o = JSONObject(prefs.getString("subjects", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        set(v) {
            prefs.edit().putString("subjects", JSONObject(v).toString()).apply()
        }

    var subjectsAt: Long
        get() = prefs.getLong("subjectsAt", 0L)
        set(v) {
            prefs.edit().putLong("subjectsAt", v).apply()
        }

    fun loadItems(): List<SuiviItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SuiviItem(
                    key = o.getString("key"),
                    date = o.getString("date"),
                    type = o.getString("type"),
                    group = o.optString("group"),
                    teacher = o.optString("teacher"),
                    detail = if (o.isNull("detail")) null else o.optString("detail"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveItems(items: List<SuiviItem>) {
        val a = JSONArray()
        items.forEach {
            a.put(
                JSONObject()
                    .put("key", it.key)
                    .put("date", it.date)
                    .put("type", it.type)
                    .put("group", it.group)
                    .put("teacher", it.teacher)
                    .put("detail", it.detail ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString("items", a.toString()).apply()
    }

    companion object {
        const val DEFAULT_URL = "https://collegenotredame.coba.ca/pednet/"
    }
}
