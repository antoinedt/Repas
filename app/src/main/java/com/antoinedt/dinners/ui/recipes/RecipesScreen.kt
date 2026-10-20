package com.antoinedt.dinners.ui.recipes

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.antoinedt.dinners.DinnersViewModel
import com.antoinedt.dinners.ui.calendar.AddDinnerDialog
import java.time.LocalDate

private enum class Sort(val label: String) { Name("A–Z"), MostEaten("Most eaten"), Recent("Recent") }

@Composable
fun RecipesScreen(vm: DinnersViewModel, onOpen: (Long) -> Unit) {
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val dinners by vm.dinners.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.Name) }
    var adding by rememberSaveable { mutableStateOf(false) }

    val counts = dinners.groupingBy { it.recipeId }.eachCount()
    val last = dinners.groupBy { it.recipeId }.mapValues { (_, l) -> l.maxOf { it.epochDay } }
    val today = LocalDate.now().toEpochDay()

    val shown = recipes
        .filter { it.name.contains(query.trim(), ignoreCase = true) }
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search ${recipes.size} recipes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sort.entries.forEach {
                    FilterChip(selected = sort == it, onClick = { sort = it }, label = { Text(it.label) })
                }
            }
            if (recipes.isEmpty()) {
                Text(
                    "No recipes yet. Add one here, from the calendar, or share a recipe link from your browser.",
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
                                        append(if (n == 1) "Eaten once" else "Eaten $n times")
                                        if (ago != null) append(" · last ${agoText(ago)}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (recipe.url != null) Icon(Icons.Default.Link, "Has website")
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("New recipe") },
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
    days < 0 -> "planned"
    days == 0L -> "today"
    days == 1L -> "yesterday"
    else -> "$days days ago"
}
