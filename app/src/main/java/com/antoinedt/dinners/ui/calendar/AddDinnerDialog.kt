package com.antoinedt.dinners.ui.calendar

import com.antoinedt.dinners.ui.frDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.antoinedt.dinners.data.Recipe
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pick an existing recipe from a searchable dropdown, or add a new one
 * (with an optional website whose recipe gets saved in the app).
 * With [date] null the dialog only creates a recipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDinnerDialog(
    date: LocalDate?,
    recipes: List<Recipe>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (name: String, url: String?, logIt: Boolean) -> Unit,
    initialUrl: String? = null,
) {
    var creating by rememberSaveable { mutableStateOf(date == null || initialUrl != null) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf(initialUrl ?: "") }
    var logIt by rememberSaveable { mutableStateOf(true) }

    val title = when {
        date == null -> "Nouvelle recette"
        initialUrl != null -> "Enregistrer la recette partagée"
        else -> "Repas · " + date.format(frDate("EEE d MMM"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!creating) {
                    val matches = recipes.filter { it.name.contains(query.trim(), ignoreCase = true) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it; selectedId = null; expanded = true },
                            label = { Text("Qu'avez-vous mangé ?") },
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            matches.forEach { recipe ->
                                DropdownMenuItem(
                                    text = { Text(recipe.name) },
                                    onClick = { query = recipe.name; selectedId = recipe.id; expanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (query.isBlank()) "Nouvelle recette…" else "Nouvelle recette « ${query.trim()} »…") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { name = query.trim(); creating = true; expanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                    if (recipes.isEmpty()) {
                        Text("Pas encore de recettes — ajoutez la première.", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { name = query.trim(); creating = true }) {
                        Icon(Icons.Default.Add, null)
                        Text("Nouvelle recette")
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (url.isBlank()) "Nom" else "Nom (facultatif, repris du site)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Site de la recette (facultatif)") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = { Text("La recette sera téléchargée et enregistrée dans l'app.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (date != null && initialUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = logIt, onCheckedChange = { logIt = it })
                            Text("Noter comme repas de ce soir")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = !busy && (name.isNotBlank() || url.isNotBlank()),
                    onClick = { onCreate(name, url.ifBlank { null }, date != null && logIt) },
                ) { Text(if (date != null && (initialUrl == null || logIt)) "Ajouter" else "Enregistrer") }
            } else {
                TextButton(
                    enabled = selectedId != null || recipes.any { it.name.equals(query.trim(), true) },
                    onClick = {
                        val id = selectedId ?: recipes.first { it.name.equals(query.trim(), true) }.id
                        onPick(id)
                    },
                ) { Text("Ajouter") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (creating && date != null && initialUrl == null) creating = false else onDismiss()
            }) { Text(if (creating && date != null && initialUrl == null) "Retour" else "Annuler") }
        },
    )
}
