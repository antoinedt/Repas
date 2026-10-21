package com.antoinedt.dinners.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.antoinedt.dinners.DinnersViewModel
import com.antoinedt.dinners.ui.calendar.AddDinnerDialog
import com.antoinedt.dinners.ui.calendar.CalendarScreen
import com.antoinedt.dinners.ui.recipes.RecipeDetailScreen
import com.antoinedt.dinners.ui.recipes.RecipesScreen
import com.antoinedt.dinners.ui.stats.StatsScreen
import com.antoinedt.dinners.ui.suggest.SuggestScreen
import java.time.LocalDate

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("calendar", "Calendrier", Icons.Default.CalendarMonth),
    Tab("recipes", "Recettes", Icons.AutoMirrored.Filled.MenuBook),
    Tab("stats", "Stats", Icons.Default.BarChart),
    Tab("suggest", "Idées", Icons.Default.AutoAwesome),
)

@Composable
fun DinnersApp(vm: DinnersViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    val sharedUrl by vm.sharedUrl.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route || (tab.route == "recipes" && current?.startsWith("recipe/") == true),
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "calendar", modifier = Modifier.padding(padding)) {
            composable("calendar") { CalendarScreen(vm) }
            composable("recipes") { RecipesScreen(vm, onOpen = { nav.navigate("recipe/$it") }) }
            composable("recipe/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                RecipeDetailScreen(vm, it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable("stats") { StatsScreen(vm) }
            composable("suggest") { SuggestScreen(vm) }
        }
    }

    // A recipe link shared from the browser: offer to save it (and log it for today).
    sharedUrl?.let { url ->
        AddDinnerDialog(
            date = LocalDate.now(),
            recipes = recipes,
            busy = busy,
            initialUrl = url,
            onDismiss = vm::sharedUrlHandled,
            onPick = { id -> vm.addDinner(LocalDate.now(), id); vm.sharedUrlHandled() },
            onCreate = { name, link, logIt ->
                vm.addRecipe(name, link, if (logIt) LocalDate.now() else null)
                vm.sharedUrlHandled()
            },
        )
    }
}
