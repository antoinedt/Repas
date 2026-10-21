package com.antoinedt.dinners.ui.recipes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Pick a label already used on other recipes, or type a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelDialog(usedLabels: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val typed = text.trim()
    val matches = usedLabels.filter { it.contains(typed, ignoreCase = true) }
    val isNew = typed.isNotEmpty() && usedLabels.none { it.equals(typed, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une étiquette") },
        text = {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; expanded = true },
                    label = { Text(if (usedLabels.isEmpty()) "Nouvelle étiquette" else "Étiquette") },
                    placeholder = { Text("ex. Végétarien, Rapide, Poisson…") },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                )
                if (matches.isNotEmpty() || isNew) {
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        matches.forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onAdd(label) },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                        if (isNew) {
                            DropdownMenuItem(
                                text = { Text("Nouvelle étiquette « $typed »") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { onAdd(typed) },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = typed.isNotEmpty(), onClick = {
                onAdd(usedLabels.firstOrNull { it.equals(typed, ignoreCase = true) } ?: typed)
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
