package com.antoinedt.dinners

import android.content.Context
import androidx.core.content.edit
import com.antoinedt.dinners.data.DinnerIdea

/** App settings and the latest Claude idea, kept in private app storage. */
class IdeaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("repas", Context.MODE_PRIVATE)

    var apiKey: String?
        get() = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_API, value?.trim()) }

    var dailyIdea: Boolean
        get() = prefs.getBoolean(KEY_DAILY, false)
        set(value) = prefs.edit { putBoolean(KEY_DAILY, value) }

    var lastIdea: DinnerIdea?
        get() = prefs.getString(KEY_IDEA, null)?.let { runCatching { DinnerIdea.fromJson(it) }.getOrNull() }
        set(value) = prefs.edit { putString(KEY_IDEA, value?.toJson()) }

    /** Folder (document tree URI) where the backup file is rewritten after every change. */
    var autoBackupFolder: String?
        get() = prefs.getString(KEY_BACKUP_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_BACKUP_FOLDER, value) }

    private companion object {
        const val KEY_API = "anthropic_api_key"
        const val KEY_DAILY = "daily_idea"
        const val KEY_IDEA = "last_idea"
        const val KEY_BACKUP_FOLDER = "auto_backup_folder"
    }
}
