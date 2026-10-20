package com.antoinedt.dinners.ui.suggest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antoinedt.dinners.DinnersViewModel
import com.antoinedt.dinners.data.Insights
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

@Composable
fun SuggestScreen(vm: DinnersViewModel) {
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val dinners by vm.dinners.collectAsStateWithLifecycle()
    var tomorrow by rememberSaveable { mutableStateOf(false) }
    var shuffle by rememberSaveable { mutableIntStateOf(0) }
    val day = LocalDate.now().plusDays(if (tomorrow) 1 else 0)

    val suggestions = remember(recipes, dinners, day, shuffle) {
        Insights.suggest(
            recipes, dinners, day,
            jitter = if (shuffle == 0) 0.0 else 2.5,
            random = Random(shuffle),
        ).take(5)
    }
    val planned = dinners.filter { it.epochDay == day.toEpochDay() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("What's for dinner?", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !tomorrow, onClick = { tomorrow = false }, label = { Text("Tonight") })
            FilterChip(selected = tomorrow, onClick = { tomorrow = true }, label = { Text("Tomorrow") })
            OutlinedButton(onClick = { shuffle++ }) {
                Icon(Icons.Default.Shuffle, null)
                Text(" Shuffle")
            }
        }
        Text(
            "Based on what you eat often, what's due again, what you had recently and your " +
                day.format(DateTimeFormatter.ofPattern("EEEE")) + " habits.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (planned.isNotEmpty()) {
            Text(
                "Already planned: " + planned.joinToString { it.recipeName },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (recipes.isEmpty()) {
            Text("Add some recipes and log a few dinners to get suggestions.", style = MaterialTheme.typography.bodyLarge)
        }
        suggestions.forEachIndexed { i, s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${i + 1}. ${s.recipe.name}", style = MaterialTheme.typography.titleMedium)
                    s.reasons.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.addDinner(day, s.recipe.id) }) {
                        Icon(Icons.Default.Check, null)
                        Text(if (tomorrow) " Plan for tomorrow" else " Have it tonight")
                    }
                }
            }
        }
    }
}
