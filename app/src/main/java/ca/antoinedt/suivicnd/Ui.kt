@file:OptIn(ExperimentalMaterial3Api::class)

package ca.antoinedt.suivicnd

import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------ theme

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ------------------------------------------------------------ helpers

enum class Cat(val label: String, val tag: String, val color: Color) {
    NEG("Non conformes", "Non conforme", Color(0xFFD32F2F)),
    LATE("Retards et absences", "Assiduité", Color(0xFFEF6C00)),
    POS("Conformes", "Conforme", Color(0xFF2E7D32)),
    INFO("Autres", "Info", Color(0xFF1565C0)),
}

fun catOf(type: String): Cat {
    val l = type.lowercase(Locale.CANADA_FRENCH)
    return when {
        "non conforme" in l -> Cat.NEG
        "retard" in l || "absence" in l -> Cat.LATE
        "conforme" in l || "coforme" in l -> Cat.POS
        else -> Cat.INFO
    }
}

private val suffixRe = Regex("""\s*\((NON conforme|CONFORME|COFORME)\)\s*$""", RegexOption.IGNORE_CASE)
fun displayTitle(type: String): String = type.replace(suffixRe, "").ifBlank { type }

private fun parseDate(s: String): Date? = try {
    SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.CANADA_FRENCH).parse(s)
} catch (e: Exception) {
    null
}

fun prettyDate(s: String): String =
    parseDate(s)?.let { SimpleDateFormat("EEE d MMM · HH:mm", Locale.CANADA_FRENCH).format(it) } ?: s

fun monthOf(s: String): String =
    parseDate(s)?.let {
        SimpleDateFormat("MMMM yyyy", Locale.CANADA_FRENCH).format(it)
            .replaceFirstChar { c -> c.uppercase() }
    } ?: "Autres"

fun prettyName(n: String): String {
    val p = n.split(",")
    return if (p.size == 2) "${p[1].trim()} ${p[0].trim()}" else n
}

fun stamp(ms: Long): String =
    SimpleDateFormat("d MMM 'à' HH:mm", Locale.CANADA_FRENCH).format(Date(ms))

fun sortItems(items: List<SuiviItem>): List<SuiviItem> =
    items.distinctBy { it.key }.sortedByDescending { parseDate(it.date)?.time ?: 0L }

/** Hosts the single WebView; moves it out of any previous parent first. */
@Composable
fun WebHost(web: WebView, modifier: Modifier) {
    AndroidView(
        factory = {
            (web.parent as? ViewGroup)?.removeView(web)
            web
        },
        modifier = modifier,
    )
}

// ------------------------------------------------------------ app

enum class Screen { LIST, SETTINGS }

private const val DAY = 24L * 60 * 60 * 1000

@Composable
fun App(store: Store, web: WebView) {
    var screen by remember { mutableStateOf(if (store.user.isBlank()) Screen.SETTINGS else Screen.LIST) }
    var items by remember { mutableStateOf(sortItems(store.loadItems())) }
    var unread by remember { mutableStateOf(store.unread) }
    var lastSync by remember { mutableLongStateOf(store.lastSync) }
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showBrowser by remember { mutableStateOf(store.showBrowser) }
    var subjects by remember { mutableStateOf(store.subjects) }
    val logs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    fun sync() {
        if (syncing) return
        scope.launch {
            syncing = true
            error = null
            logs.clear()
            try {
                val scraper = PortalScraper(web) { line ->
                    logs.add(line)
                    status = line
                }
                val known = items.associateBy { it.key }
                val fresh = sortItems(scraper.sync(store.portalUrl, store.user, store.pass, known))
                val freshKeys = fresh.map { it.key }.toSet()
                val newKeys = freshKeys - known.keys
                if (known.isNotEmpty()) unread = (unread + newKeys) intersect freshKeys
                store.unread = unread
                items = fresh
                store.saveItems(fresh)
                lastSync = System.currentTimeMillis()
                store.lastSync = lastSync

                // Teacher -> subject map, refreshed weekly (daily if someone is unknown).
                val age = lastSync - store.subjectsAt
                val missing = fresh.any { PortalScraper.nameKey(it.teacher) !in subjects }
                if (subjects.isEmpty() || age > 7 * DAY || (missing && age > DAY)) {
                    val m = scraper.fetchSubjects()
                    if (m.isNotEmpty()) {
                        subjects = m
                        store.subjects = m
                        store.subjectsAt = lastSync
                    }
                }

                status = when {
                    known.isEmpty() -> "${fresh.size} suivis importés"
                    newKeys.isEmpty() -> "Aucun nouveau suivi"
                    newKeys.size == 1 -> "1 nouveau suivi"
                    else -> "${newKeys.size} nouveaux suivis"
                }
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                status = null
            } finally {
                syncing = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.LIST -> ListScreen(
                all = items,
                subjects = subjects,
                unread = unread,
                lastSync = lastSync,
                syncing = syncing,
                status = status,
                error = error,
                logs = logs,
                showBrowser = showBrowser,
                web = web,
                onSync = { sync() },
                onSettings = { screen = Screen.SETTINGS },
                onOpened = { key ->
                    if (key in unread) {
                        unread = unread - key
                        store.unread = unread
                    }
                },
            )

            Screen.SETTINGS -> SettingsScreen(
                store = store,
                web = web,
                onDone = {
                    showBrowser = store.showBrowser
                    screen = Screen.LIST
                },
                onClearItems = {
                    items = emptyList()
                    unread = emptySet()
                    lastSync = 0L
                    store.saveItems(emptyList())
                    store.unread = emptySet()
                    store.lastSync = 0L
                },
            )
        }
        // The WebView must stay attached to work; keep it invisible when not shown.
        if (!(showBrowser && screen == Screen.LIST)) {
            WebHost(web, Modifier.size(1.dp).alpha(0f))
        }
    }
}

// ------------------------------------------------------------ list screen

private sealed class Entry {
    data class Header(val title: String) : Entry()
    data class Item(val item: SuiviItem) : Entry()
}

@Composable
fun ListScreen(
    all: List<SuiviItem>,
    subjects: Map<String, String>,
    unread: Set<String>,
    lastSync: Long,
    syncing: Boolean,
    status: String?,
    error: String?,
    logs: List<String>,
    showBrowser: Boolean,
    web: WebView,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onOpened: (String) -> Unit,
) {
    var filter by remember { mutableStateOf<Cat?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var showLog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Suivi de l'élève")
                        if (lastSync > 0) {
                            Text(
                                "Synchro : " + stamp(lastSync),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings, enabled = !syncing) {
                        Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!syncing) onSync() },
                icon = {
                    if (syncing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                },
                text = { Text(if (syncing) "Synchro…" else "Synchroniser") },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (showBrowser) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    WebHost(web, Modifier.fillMaxWidth().height(320.dp))
                }
            }

            val shown = all.filter { filter == null || catOf(it.type) == filter }
            val entries = buildList {
                var month = ""
                for (s in shown) {
                    val m = monthOf(s.date)
                    if (m != month) {
                        month = m
                        add(Entry.Header(m))
                    }
                    add(Entry.Item(s))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (error != null) {
                    item(key = "error") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (logs.isNotEmpty()) {
                                    TextButton(onClick = { showLog = !showLog }) {
                                        Text(if (showLog) "Masquer le journal" else "Voir le journal")
                                    }
                                    if (showLog) {
                                        Text(
                                            logs.joinToString("\n"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (status != null) {
                    item(key = "status") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                status,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                if (all.isNotEmpty()) {
                    item(key = "filters") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = filter == null,
                                onClick = { filter = null },
                                label = { Text("Tous (${all.size})") },
                            )
                            Cat.entries.forEach { c ->
                                val n = all.count { catOf(it.type) == c }
                                if (n > 0) {
                                    FilterChip(
                                        selected = filter == c,
                                        onClick = { filter = if (filter == c) null else c },
                                        label = { Text("${c.label} ($n)") },
                                    )
                                }
                            }
                        }
                    }
                } else if (!syncing) {
                    item(key = "empty") {
                        Text(
                            "Aucun suivi pour l'instant.\nAppuyez sur « Synchroniser ».",
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(
                    entries,
                    key = { e ->
                        when (e) {
                            is Entry.Header -> "h:" + e.title
                            is Entry.Item -> "i:" + e.item.key
                        }
                    },
                ) { e ->
                    when (e) {
                        is Entry.Header -> Text(
                            e.title,
                            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        is Entry.Item -> {
                            val key = e.item.key
                            SuiviCard(
                                item = e.item,
                                subject = subjects[PortalScraper.nameKey(e.item.teacher)],
                                isNew = key in unread,
                                expanded = key in expanded,
                                onToggle = {
                                    expanded = if (key in expanded) expanded - key else expanded + key
                                    onOpened(key)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuiviCard(
    item: SuiviItem,
    subject: String?,
    isNew: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val cat = catOf(item.type)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(cat.color)
            )
            Column(
                Modifier
                    .weight(1f)
                    .animateContentSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayTitle(item.type),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if (expanded) 4 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = cat.color.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    cat.tag,
                                    color = cat.color,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                prettyDate(item.date) + (subject?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (isNew) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("Nouveau", modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Réduire" else "Développer",
                        modifier = Modifier.rotate(rotation),
                    )
                }

                if (expanded) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Detail(
                        "Enseignant",
                        prettyName(item.teacher) + (subject?.let { " · $it" } ?: ""),
                    )
                    if (item.group.isNotBlank()) Detail("Regroupement", item.group)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.detail?.takeIf { it.isNotBlank() } ?: "Aucun détail supplémentaire récupéré.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.detail.isNullOrBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label : ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ------------------------------------------------------------ settings

@Composable
fun SettingsScreen(
    store: Store,
    web: WebView,
    onDone: () -> Unit,
    onClearItems: () -> Unit,
) {
    var user by remember { mutableStateOf(store.user) }
    var pass by remember { mutableStateOf(store.pass) }
    var url by remember { mutableStateOf(store.portalUrl) }
    var showBrowser by remember { mutableStateOf(store.showBrowser) }
    var showPass by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val canGoBack = store.user.isNotBlank()

    BackHandler(enabled = canGoBack) { onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Compte du portail parents (PedNET)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Identifiant") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showPass = !showPass }) {
                        Text(if (showPass) "Masquer" else "Afficher")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Adresse du portail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Afficher le navigateur pendant la synchro")
                    Text(
                        "Utile si la connexion échoue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = showBrowser, onCheckedChange = { showBrowser = it })
            }
            Button(
                onClick = {
                    if (user.trim() != store.user) CookieManager.getInstance().removeAllCookies(null)
                    store.user = user.trim()
                    store.pass = pass
                    store.portalUrl = url.trim().ifBlank { Store.DEFAULT_URL }
                    store.showBrowser = showBrowser
                    onDone()
                },
                enabled = user.isNotBlank() && pass.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enregistrer")
            }
            Text(
                "Les identifiants sont chiffrés et restent sur ce téléphone. " +
                    "L'app se connecte uniquement au portail du collège.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            OutlinedButton(
                onClick = {
                    CookieManager.getInstance().removeAllCookies(null)
                    web.clearCache(true)
                    message = "Session du portail réinitialisée."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Réinitialiser la session du portail")
            }
            OutlinedButton(
                onClick = {
                    onClearItems()
                    message = "Suivis enregistrés effacés."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Effacer les suivis enregistrés")
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
