package com.antoinedt.dinners.ui.recipes

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.InputChip
import com.antoinedt.dinners.data.allLabels
import com.antoinedt.dinners.ui.frDate
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.antoinedt.dinners.DinnersViewModel
import com.antoinedt.dinners.data.Recipe
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(vm: DinnersViewModel, id: Long, onBack: () -> Unit) {
    val recipe by remember(id) { vm.recipe(id) }.collectAsState(initial = null)
    val dinners by vm.dinners.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var addingLabel by rememberSaveable { mutableStateOf(false) }
    val allRecipes by vm.recipes.collectAsStateWithLifecycle()

    val r = recipe
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(r?.name ?: "", maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            },
            actions = {
                if (r != null) {
                    if (r.url != null) {
                        IconButton(onClick = { vm.refreshRecipe(r) }, enabled = !busy) {
                            Icon(Icons.Default.Refresh, "Réimporter depuis le site")
                        }
                    }
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Modifier") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            },
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (r == null) return@Column

        val eaten = dinners.filter { it.recipeId == r.id }.map { it.epochDay }.sorted()
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            r.imageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(r.name, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Mangée ${eaten.size}×") })
                eaten.lastOrNull()?.let { last ->
                    AssistChip(onClick = {}, label = {
                        Text("Dernière fois le " + LocalDate.ofEpochDay(last).format(frDate("d MMM yyyy")))
                    })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.labelList.forEach { label ->
                    InputChip(
                        selected = false,
                        onClick = { vm.setLabels(r, r.labelList - label) },
                        label = { Text(label) },
                        trailingIcon = { Icon(Icons.Default.Close, "Retirer l'étiquette", Modifier.size(16.dp)) },
                    )
                }
                AssistChip(
                    onClick = { addingLabel = true },
                    label = { Text("Étiquette") },
                    leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) },
                )
            }
            if (r.servings != null || r.totalTime != null) {
                Text(
                    listOfNotNull(r.servings?.let { "Pour $it" }, r.totalTime?.let { "Durée : $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            r.url?.let { url ->
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Text(" " + (Uri.parse(url).host ?: url))
                }
            }
            if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodyMedium)

            if (r.ingredientList.isNotEmpty()) {
                HorizontalDivider()
                Text("Ingrédients", style = MaterialTheme.typography.titleMedium)
                r.ingredientList.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
            if (r.instructionList.isNotEmpty()) {
                HorizontalDivider()
                Text("Étapes", style = MaterialTheme.typography.titleMedium)
                var n = 0
                r.instructionList.forEach { step ->
                    if (step.startsWith("—")) {
                        Text(step.trim('—', ' '), style = MaterialTheme.typography.titleSmall)
                    } else {
                        n++
                        Text("$n.  $step", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (r.ingredientList.isEmpty() && r.instructionList.isEmpty()) {
                Text(
                    if (r.url != null) "Aucun détail n'a pu être lu sur le site. Touchez ✎ pour les ajouter, ou ↻ pour réessayer."
                    else "Pas encore de détails. Touchez ✎ pour ajouter ingrédients et étapes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editing && r != null) {
        EditRecipeDialog(r, onDismiss = { editing = false }, onSave = { vm.updateRecipe(it); editing = false })
    }
    if (addingLabel && r != null) {
        LabelDialog(
            usedLabels = allLabels(allRecipes).filter { l -> r.labelList.none { it.equals(l, ignoreCase = true) } },
            onDismiss = { addingLabel = false },
            onAdd = { vm.setLabels(r, r.labelList + it); addingLabel = false },
        )
    }
    if (confirmDelete && r != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${r.name} » ?") },
            text = { Text("Elle sera aussi retirée de tous les jours du calendrier où elle est notée.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteRecipe(r); onBack() }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun EditRecipeDialog(recipe: Recipe, onDismiss: () -> Unit, onSave: (Recipe) -> Unit) {
    var name by rememberSaveable { mutableStateOf(recipe.name) }
    var url by rememberSaveable { mutableStateOf(recipe.url ?: "") }
    var ingredients by rememberSaveable { mutableStateOf(recipe.ingredients) }
    var instructions by rememberSaveable { mutableStateOf(recipe.instructions) }
    var notes by rememberSaveable { mutableStateOf(recipe.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la recette") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Site web") }, singleLine = true)
                OutlinedTextField(ingredients, { ingredients = it }, label = { Text("Ingrédients (un par ligne)") }, minLines = 3)
                OutlinedTextField(instructions, { instructions = it }, label = { Text("Étapes (une par ligne)") }, minLines = 3)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(
                    recipe.copy(
                        name = name.trim(),
                        url = url.trim().ifBlank { null },
                        ingredients = ingredients.trim(),
                        instructions = instructions.trim(),
                        notes = notes.trim(),
                    )
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
