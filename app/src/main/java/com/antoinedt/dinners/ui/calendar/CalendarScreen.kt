package com.antoinedt.dinners.ui.calendar

import com.antoinedt.dinners.ui.frDate
import com.antoinedt.dinners.ui.capitalized
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antoinedt.dinners.DinnersViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(vm: DinnersViewModel) {
    val dinners by vm.dinners.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.ofEpochDay(selectedDay)
    val byDay = dinners.groupBy { it.epochDay }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.padding(2.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mois précédent")
            }
            Text(
                month.format(frDate("MMMM yyyy")).capitalized(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mois suivant")
            }
        }
        if (month != YearMonth.from(today)) {
            TextButton(
                onClick = { monthText = YearMonth.from(today).toString(); selectedDay = today.toEpochDay() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Aujourd'hui") }
        }

        Row(Modifier.padding(horizontal = 8.dp)) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.SHORT, Locale.FRENCH).capitalized(),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Monday-first grid.
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        Column(Modifier.padding(horizontal = 8.dp)) {
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(0.72f).padding(2.dp)) {
                            if (dayNum in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNum)
                                DayCell(
                                    date = date,
                                    names = byDay[date.toEpochDay()].orEmpty().map { it.recipeName },
                                    isToday = date == today,
                                    isSelected = date == selected,
                                    onClick = { selectedDay = date.toEpochDay() },
                                )
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    selected.format(frDate("EEEE d MMMM")).capitalized(),
                    style = MaterialTheme.typography.titleMedium,
                )
                val entries = byDay[selectedDay].orEmpty()
                if (entries.isEmpty()) {
                    Text("Rien de noté.", style = MaterialTheme.typography.bodyMedium)
                }
                entries.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.recipeName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { vm.removeDinner(entry.dinnerId) }) {
                            Icon(Icons.Default.Close, "Retirer")
                        }
                    }
                }
                Button(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null)
                    Text("Ajouter un repas")
                }
            }
        }
    }

    if (adding) {
        AddDinnerDialog(
            date = selected,
            recipes = recipes,
            busy = busy,
            onDismiss = { adding = false },
            onPick = { vm.addDinner(selected, it); adding = false },
            onCreate = { name, url, _ -> vm.addRecipe(name, url, selected); adding = false },
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    names: List<String>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (names.isNotEmpty()) colors.secondaryContainer else colors.surfaceVariant.copy(alpha = 0.4f))
            .then(if (isSelected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) colors.primary else colors.onSurface,
        )
        names.take(2).forEach {
            Text(
                it,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.onSecondaryContainer,
            )
        }
        if (names.size > 2) Text("+${names.size - 2}", fontSize = 9.sp)
    }
}
