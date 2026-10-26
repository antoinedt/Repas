package com.antoinedt.dinners

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.antoinedt.dinners.data.Backup
import com.antoinedt.dinners.data.ClaudeIdeas
import com.antoinedt.dinners.data.DinnerIdea
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antoinedt.dinners.data.AppDatabase
import com.antoinedt.dinners.data.DinnerEntry
import com.antoinedt.dinners.data.Recipe
import com.antoinedt.dinners.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(FlowPreview::class)
class DinnersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(AppDatabase.get(app))
    private val store = IdeaStore(app)

    val recipes: StateFlow<List<Recipe>> =
        repo.recipes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dinners: StateFlow<List<DinnerEntry>> =
        repo.dinners.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    /** One-off messages shown in a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    /** Recipe link shared into the app from another app, waiting to be added. */
    private val _sharedUrl = MutableStateFlow<String?>(null)
    val sharedUrl = _sharedUrl.asStateFlow()
    fun onShared(text: String?) {
        _sharedUrl.value = text?.let { Regex("https?://\\S+").find(it)?.value }
    }
    fun sharedUrlHandled() { _sharedUrl.value = null }

    fun recipe(id: Long) = repo.recipe(id)

    fun addDinner(date: LocalDate, recipeId: Long) = viewModelScope.launch { repo.addDinner(date, recipeId) }

    fun removeDinner(dinnerId: Long) = viewModelScope.launch { repo.removeDinner(dinnerId) }

    /** Creates a recipe (importing it when a URL is given) and optionally logs it on [date]. */
    fun addRecipe(name: String, url: String?, date: LocalDate?) = viewModelScope.launch {
        _busy.value = true
        try {
            val (id, warning) = repo.createRecipe(name, url)
            if (date != null) repo.addDinner(date, id)
            _message.value = warning ?: if (url.isNullOrBlank()) null else "Recette enregistrée"
        } finally {
            _busy.value = false
        }
    }

    fun refreshRecipe(recipe: Recipe) = viewModelScope.launch {
        _busy.value = true
        try {
            _message.value = repo.refreshFromWebsite(recipe) ?: "Recette mise à jour depuis le site"
        } finally {
            _busy.value = false
        }
    }

    fun updateRecipe(recipe: Recipe) = viewModelScope.launch { repo.updateRecipe(recipe) }

    fun deleteRecipe(recipe: Recipe) = viewModelScope.launch { repo.deleteRecipe(recipe) }

    fun setLabels(recipe: Recipe, labels: List<String>) = viewModelScope.launch {
        repo.updateRecipe(recipe.withLabels(labels))
    }

    fun backupFileName() = "repas-sauvegarde-${LocalDate.now()}.json"

    /** Writes the backup to a file the user picked (e.g. Downloads or Drive). */
    fun exportTo(uri: Uri) = viewModelScope.launch {
        _message.value = runCatching {
            val json = repo.backupJson()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(json.toByteArray())
                }
            }
            "Sauvegarde exportée"
        }.getOrElse { "Échec de l'export : ${it.message}" }
    }

    /** Writes the backup to the app cache and returns a shareable content:// URI. */
    suspend fun backupForSharing(): Uri {
        val app = getApplication<Application>()
        val json = repo.backupJson()
        val file = withContext(Dispatchers.IO) {
            File(app.cacheDir, "partage").apply { mkdirs() }
                .resolve(backupFileName())
                .apply { writeText(json) }
        }
        return FileProvider.getUriForFile(app, "${app.packageName}.files", file)
    }

    /** Merges a backup file into the current data. */
    fun importFrom(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        try {
            _message.value = runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                val (recipesAdded, dinnersAdded) = repo.importBackup(Backup.parse(text))
                "Import terminé : $recipesAdded recette(s) et $dinnersAdded repas ajoutés"
            }.getOrElse { "Échec de l'import : ${it.message}" }
        } finally {
            _busy.value = false
        }
    }

    // ---- Automatic backup to a folder chosen by the user ----

    private val _autoBackupFolder = MutableStateFlow(store.autoBackupFolder)
    val autoBackupFolder = _autoBackupFolder.asStateFlow()

    init {
        // Rewrite the backup file a couple of seconds after any change.
        viewModelScope.launch {
            combine(repo.recipes, repo.dinners) { _, _ -> }.debounce(2_000).collect {
                _autoBackupFolder.value?.let { folder -> runCatching { writeAutoBackup(Uri.parse(folder)) } }
            }
        }
    }

    fun setAutoBackupFolder(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            writeAutoBackup(uri)
        }.onSuccess {
            store.autoBackupFolder = uri.toString()
            _autoBackupFolder.value = uri.toString()
            _message.value = "Sauvegarde automatique activée : $AUTO_BACKUP_FILE"
        }.onFailure { _message.value = "Impossible d'écrire dans ce dossier : ${it.message}" }
    }

    fun disableAutoBackup() {
        val app = getApplication<Application>()
        _autoBackupFolder.value?.let { folder ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        store.autoBackupFolder = null
        _autoBackupFolder.value = null
        _message.value = "Sauvegarde automatique désactivée"
    }

    private suspend fun writeAutoBackup(folder: Uri) {
        val app = getApplication<Application>()
        val json = repo.backupJson()
        withContext(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(app, folder) ?: error("dossier introuvable")
            val file = dir.findFile(AUTO_BACKUP_FILE)
                ?: dir.createFile("application/json", AUTO_BACKUP_FILE.removeSuffix(".json"))
                ?: error("impossible de créer le fichier")
            app.contentResolver.openOutputStream(file.uri, "wt")!!.use { it.write(json.toByteArray()) }
        }
    }

    // ---- Claude idea of the day ----

    private val _hasApiKey = MutableStateFlow(store.apiKey != null)
    val hasApiKey = _hasApiKey.asStateFlow()

    private val _dailyIdea = MutableStateFlow(store.dailyIdea)
    val dailyIdea = _dailyIdea.asStateFlow()

    private val _idea = MutableStateFlow(store.lastIdea)
    val idea = _idea.asStateFlow()

    private val _ideaLoading = MutableStateFlow(false)
    val ideaLoading = _ideaLoading.asStateFlow()

    private val _ideaError = MutableStateFlow<String?>(null)
    val ideaError = _ideaError.asStateFlow()

    /** Set when a notification asks to open the Idées tab. */
    private val _openIdeas = MutableStateFlow(false)
    val openIdeas = _openIdeas.asStateFlow()
    fun requestOpenIdeas() { _openIdeas.value = true }
    fun openIdeasHandled() { _openIdeas.value = false }

    /** Re-reads the idea the background job may have saved. */
    fun reloadIdea() { _idea.value = store.lastIdea }

    fun saveApiKey(key: String) {
        store.apiKey = key
        _hasApiKey.value = store.apiKey != null
    }

    fun forgetApiKey() {
        store.apiKey = null
        _hasApiKey.value = false
        setDailyIdea(false)
    }

    fun setDailyIdea(enabled: Boolean) {
        store.dailyIdea = enabled
        _dailyIdea.value = enabled
        val app = getApplication<Application>()
        if (enabled) DailyIdeaWorker.schedule(app) else DailyIdeaWorker.cancel(app)
    }

    fun askClaude() = viewModelScope.launch {
        val key = store.apiKey ?: return@launch
        _ideaLoading.value = true
        _ideaError.value = null
        try {
            val (recipes, dinners) = repo.snapshot()
            val idea = ClaudeIdeas.fetch(key, recipes, dinners, LocalDate.now())
            store.lastIdea = idea
            _idea.value = idea
        } catch (e: Exception) {
            _ideaError.value = "Échec de la demande à Claude : ${e.message}"
        } finally {
            _ideaLoading.value = false
        }
    }

    /** Saves the idea as a recipe (importing its web page) and optionally logs it for today. */
    fun saveIdea(idea: DinnerIdea, tonight: Boolean) = viewModelScope.launch {
        _busy.value = true
        try {
            val (id, warning) = repo.createRecipe(idea.name, idea.url)
            repo.addLabels(id, idea.labels)
            if (tonight) repo.addDinner(LocalDate.now(), id)
            _message.value = warning ?: if (tonight) "Ajouté au repas de ce soir" else "Recette enregistrée"
        } finally {
            _busy.value = false
        }
    }

    companion object {
        const val AUTO_BACKUP_FILE = "repas-sauvegarde.json"
    }
}
