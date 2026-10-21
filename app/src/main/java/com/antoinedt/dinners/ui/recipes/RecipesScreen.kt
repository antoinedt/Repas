package com.antoinedt.dinners.ui.recipes

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.antoinedt.dinners.DinnersViewModel
import com.antoinedt.dinners.data.allLabels
import com.antoinedt.dinners.ui.calendar.AddDinnerDialog
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Sort(val label: String) { Name("A–Z"), MostEaten("Les plus mangées"), Recent("Récentes") }

@Composable
fun RecipesScreen(vm: DinnersViewModel, onOpen: (Long) -> Unit) {
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val dinners by vm.dinners.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.Name) }
    var labelFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }

    val labels = allLabels(recipes)
    val activeLabel = labelFilter?.takeIf { it in labels }
    val counts = dinners.groupingBy { it.recipeId }.eachCount()
    val last = dinners.groupBy { it.recipeId }.mapValues { (_, l) -> l.maxOf { it.epochDay } }
    val today = LocalDate.now().toEpochDay()

    val shown = recipes
        .filter { it.name.contains(query.trim(), ignoreCase = true) }
        .filter { r -> activeLabel == null || r.labelList.any { it.equals(activeLabel, ignoreCase = true) } }
        .let { list ->
            when (sort) {
                Sort.Name -> list
                Sort.MostEaten -> list.sortedByDescending { counts[it.id] ?: 0 }
                Sort.Recent -> list.sortedByDescending { last[it.id] ?: Long.MIN_VALUE }
            }
        }

    Box(Modifier.fillMaxSize()) {
        Column {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Chercher parmi ${recipes.size} recettes") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Sauvegarde") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Exporter la sauvegarde…") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = { menu = false; exportLauncher.launch(vm.backupFileName()) },
                        )
                        DropdownMenuItem(
                            text = { Text("Partager la sauvegarde…") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                menu = false
                                scope.launch {
                                    val uri = vm.backupForSharing()
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Sauvegarde Repas")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Partager la sauvegarde"))
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Importer une sauvegarde…") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = { menu = false; importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        )
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Sort.entries.forEach {
                    FilterChip(selected = sort == it, onClick = { sort = it }, label = { Text(it.label) })
                }
            }
            if (labels.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = activeLabel == null, onClick = { labelFilter = null }, label = { Text("Toutes") })
                    labels.forEach { label ->
                        FilterChip(
                            selected = activeLabel == label,
                            onClick = { labelFilter = if (activeLabel == label) null else label },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(16.dp)) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (recipes.isEmpty()) {
                Text(
                    "Pas encore de recettes. Ajoutez-en une ici, depuis le calendrier, ou partagez un lien de recette depuis votre navigateur.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { recipe ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(recipe.id) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (recipe.imageUrl != null) {
                                AsyncImage(
                                    model = recipe.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                                val n = counts[recipe.id] ?: 0
                                val ago = last[recipe.id]?.let { today - it }
                                Text(
                                    buildString {
                                        append(
                                            when (n) {
                                                0 -> "Jamais mangée"
                                                1 -> "Mangée 1 fois"
                                                else -> "Mangée $n fois"
                                            }
                                        )
                                        if (ago != null) append(" · ${agoText(ago)}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (recipe.labelList.isNotEmpty()) {
                                    Text(
                                        recipe.labelList.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (recipe.url != null) Icon(Icons.Default.Link, "A un site web")
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Nouvelle recette") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        AddDinnerDialog(
            date = null,
            recipes = recipes,
            busy = busy,
            onDismiss = { adding = false },
            onPick = {},
            onCreate = { name, url, _ -> vm.addRecipe(name, url, null); adding = false },
        )
    }
}

fun agoText(days: Long): String = when {
    days < 0 -> "prévue"
    days == 0L -> "aujourd'hui"
    days == 1L -> "hier"
    else -> "il y a $days jours"
}
