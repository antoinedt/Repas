package com.antoinedt.dinners

import android.app.Application
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

class DinnersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(AppDatabase.get(app))

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
            _message.value = warning ?: if (url.isNullOrBlank()) null else "Recipe saved"
        } finally {
            _busy.value = false
        }
    }

    fun refreshRecipe(recipe: Recipe) = viewModelScope.launch {
        _busy.value = true
        try {
            _message.value = repo.refreshFromWebsite(recipe) ?: "Recipe updated from website"
        } finally {
            _busy.value = false
        }
    }

    fun updateRecipe(recipe: Recipe) = viewModelScope.launch { repo.updateRecipe(recipe) }

    fun deleteRecipe(recipe: Recipe) = viewModelScope.launch { repo.deleteRecipe(recipe) }
}
