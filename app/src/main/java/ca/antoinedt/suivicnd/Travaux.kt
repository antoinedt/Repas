@file:OptIn(ExperimentalMaterial3Api::class)

package ca.antoinedt.suivicnd

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TView { UPCOMING, RESULTS, BY_COURSE }

// ------------------------------------------------------------ helpers

private val pctRe = Regex("""\(([\d.,]+)\s*%\)""")
private val fr = Locale.CANADA_FRENCH
private const val DAY_MS = 24L * 60 * 60 * 1000

fun pctOf(result: String): Double? =
    pctRe.find(result)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

fun gradeColor(result: String): Color? {
    val p = pctOf(result)
    if (p != null) {
        return when {
            p < 60 -> Cat.NEG.color
            p < 75 -> Cat.LATE.color
            else -> Cat.POS.color
        }
    }
    val l = result.trim().uppercase(fr)
    return when {
        l.isEmpty() -> null
        l.startsWith("A") || l.startsWith("B") -> Cat.POS.color
        l.startsWith("C") -> Cat.LATE.color
        l.startsWith("D") || l.startsWith("E") || l.startsWith("F") -> Cat.NEG.color
        else -> null
    }
}

/** "40,0 %" for numeric results, the letter otherwise. */
fun shortResult(result: String): String {
    val p = pctOf(result) ?: return result.trim()
    return String.format(fr, "%.0f %%", p)
}

fun parseDue(s: String): Date? {
    for (f in listOf("dd-MM-yyyy 'à' HH:mm", "dd-MM-yyyy")) {
        try {
            return SimpleDateFormat(f, fr).parse(s)
        } catch (e: Exception) {
            // try next format
        }
    }
    return null
}

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun dueText(t: Travail): String {
    val d = parseDue(t.due) ?: return t.due.ifBlank { "Sans date" }
    val showTime = t.due.contains("à") && !t.due.endsWith("23:59")
    val pattern = if (showTime) "EEE d MMM · HH:mm" else "EEE d MMM"
    return SimpleDateFormat(pattern, fr).format(d)
}

fun relativeDue(t: Travail): String? {
    val d = parseDue(t.due) ?: return null
    val days = ((startOfDay(d.time) - startOfDay(System.currentTimeMillis())) / DAY_MS).toInt()
    return when {
        days < 0 -> null
        days == 0 -> "aujourd'hui"
        days == 1 -> "demain"
        else -> "dans $days j"
    }
}

private fun compText(c: String) = c.removePrefix("Compétence - ").removePrefix("Compétence -").trim()

// ------------------------------------------------------------ list section

fun LazyListScope.travauxSection(
    all: List<Travail>,
    unread: Set<String>,
    etape: String,
    view: TView,
    onView: (TView) -> Unit,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    syncing: Boolean,
) {
    if (all.isEmpty()) {
        if (!syncing) {
            item(key = "t_empty") {
                Text(
                    "Aucun travail pour l'instant.\nAppuyez sur « Synchroniser ».",
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val today = startOfDay(System.currentTimeMillis())
    val upcoming = all.filter { (parseDue(it.due)?.time ?: -1L) >= today }
        .sortedBy { parseDue(it.due)?.time ?: 0L }
    val graded = all.filter { it.result.isNotBlank() }
        .sortedByDescending { parseDue(it.due)?.time ?: 0L }

    item(key = "t_filters") {
        Column {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = view == TView.UPCOMING,
                    onClick = { onView(TView.UPCOMING) },
                    label = { Text("À venir (${upcoming.size})") },
                )
                FilterChip(
                    selected = view == TView.RESULTS,
                    onClick = { onView(TView.RESULTS) },
                    label = { Text("Résultats (${graded.size})") },
                )
                FilterChip(
                    selected = view == TView.BY_COURSE,
                    onClick = { onView(TView.BY_COURSE) },
                    label = { Text("Par matière") },
                )
            }
            if (etape.isNotBlank()) {
                Text(
                    "Étape $etape",
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (view) {
        TView.UPCOMING -> {
            if (upcoming.isEmpty()) {
                item(key = "t_none_up") { EmptyNote("Rien à remettre pour l'instant.") }
            }
            items(upcoming, key = { "u:" + it.key }) { t ->
                TravailCard(t, true, t.key in unread, ("u:" + t.key) in expanded) { onToggle("u:" + t.key) }
            }
        }

        TView.RESULTS -> {
            if (graded.isEmpty()) {
                item(key = "t_none_res") { EmptyNote("Aucun résultat publié pour l'instant.") }
            }
            items(graded, key = { "r:" + it.key }) { t ->
                TravailCard(t, true, t.key in unread, ("r:" + t.key) in expanded) { onToggle("r:" + t.key) }
            }
        }

        TView.BY_COURSE -> {
            val byCourse = all.groupBy { it.code }.toList().sortedBy { it.second.first().course }
            items(byCourse, key = { "c:" + it.first }) { (code, list) ->
                CourseCard(list, unread, ("c:$code") in expanded) { onToggle("c:$code") }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ------------------------------------------------------------ cards

@Composable
fun ResultBadge(result: String) {
    val color = gradeColor(result) ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            shortResult(result),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun NewDot() {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}

@Composable
fun TravailCard(
    t: Travail,
    showCourse: Boolean,
    isNew: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(
            Modifier
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isNew) {
                            NewDot()
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            t.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if (expanded) 4 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val rel = if (t.result.isBlank()) relativeDue(t) else null
                    Text(
                        listOfNotNull(
                            if (showCourse) t.course else null,
                            dueText(t),
                            rel,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (t.result.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    ResultBadge(t.result)
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Réduire" else "Développer",
                    modifier = Modifier.rotate(rotation),
                )
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Line("Matière", t.course)
                if (t.comp.isNotBlank()) Line("Compétence", compText(t.comp))
                if (t.cat.isNotBlank()) Line("Catégorie", t.cat)
                if (t.assigned.isNotBlank()) Line("Assigné le", t.assigned)
                if (t.due.isNotBlank()) Line("À remettre", t.due)
                Line("Résultat", t.result.ifBlank { "Pas encore noté" })
            }
        }
    }
}

@Composable
fun CourseCard(list: List<Travail>, unread: Set<String>, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val pcts = list.mapNotNull { pctOf(it.result) }
    val avg = if (pcts.isEmpty()) null else pcts.average()
    val graded = list.count { it.result.isNotBlank() }
    val anyNew = list.any { it.key in unread }
    val sorted = list.sortedBy { parseDue(it.due)?.time ?: Long.MAX_VALUE }

    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(
            Modifier
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (anyNew) {
                            NewDot()
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            list.first().course,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${list.size} travaux · $graded notés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (avg != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        ResultBadge(String.format(Locale.US, "(%.1f %%)", avg))
                        Text(
                            "moyenne",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Réduire" else "Développer",
                    modifier = Modifier.rotate(rotation),
                )
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                sorted.forEachIndexed { i, t ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (t.key in unread) {
                                    NewDot()
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(t.title, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                listOfNotNull(dueText(t), t.cat.ifBlank { null }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (t.result.isNotBlank()) {
                            Column(horizontalAlignment = Alignment.End) {
                                ResultBadge(t.result)
                                if (pctOf(t.result) != null) {
                                    Text(
                                        t.result.substringBefore("(").trim(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            Text(
                                "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Moyenne indicative des travaux notés, sans pondération.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label : ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
