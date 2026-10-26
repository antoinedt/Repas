package com.antoinedt.dinners.ui.suggest

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antoinedt.dinners.DinnersViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClaudeIdeaCard(vm: DinnersViewModel) {
    val hasKey by vm.hasApiKey.collectAsStateWithLifecycle()
    val daily by vm.dailyIdea.collectAsStateWithLifecycle()
    val idea by vm.idea.collectAsStateWithLifecycle()
    val loading by vm.ideaLoading.collectAsStateWithLifecycle()
    val error by vm.ideaError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var keyInput by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("  Idée du jour par Claude", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (hasKey) TextButton(onClick = { showSettings = !showSettings }) { Text("Réglages") }
            }

            if (!hasKey) {
                Text(
                    "Claude regarde vos habitudes et cherche sur le web une recette qui vous ressemble, avec son lien. " +
                        "Il faut une clé API Anthropic (console.anthropic.com → API Keys). Chaque idée coûte quelques centimes.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Clé API (sk-ant-…)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = keyInput.isNotBlank(), onClick = { vm.saveApiKey(keyInput); keyInput = "" }) {
                        Text("Enregistrer la clé")
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys")))
                    }) { Text("Obtenir une clé") }
                }
                return@Column
            }

            if (showSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Une idée chaque jour", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Vers 10 h, avec une notification",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = daily, onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        vm.setDailyIdea(on)
                    })
                }
                TextButton(onClick = { vm.forgetApiKey(); showSettings = false }) { Text("Oublier la clé API") }
            }

            val current = idea
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Claude cherche une idée…", style = MaterialTheme.typography.bodyMedium)
                }
                current != null -> {
                    if (current.date != LocalDate.now()) {
                        Text(
                            "Idée du ${current.date.dayOfMonth}/${current.date.monthValue}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(current.name, style = MaterialTheme.typography.titleLarge)
                    if (current.why.isNotBlank()) Text(current.why, style = MaterialTheme.typography.bodyMedium)
                    if (current.labels.isNotEmpty()) {
                        Text(
                            current.labels.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        current.url?.let { url ->
                            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                                Text(" Voir la recette")
                            }
                        }
                        Button(enabled = !busy, onClick = { vm.saveIdea(current, tonight = true) }) {
                            Icon(Icons.Default.Check, null)
                            Text(" Ce soir !")
                        }
                        OutlinedButton(enabled = !busy, onClick = { vm.saveIdea(current, tonight = false) }) {
                            Icon(Icons.Default.BookmarkAdd, null)
                            Text(" Garder")
                        }
                    }
                }
                else -> Text(
                    "Demandez une idée : Claude s'appuie sur vos repas notés et vos étiquettes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.size(4.dp))
            OutlinedButton(enabled = !loading, onClick = { vm.askClaude() }) {
                Icon(Icons.Default.Refresh, null)
                Text(if (idea == null) " Demander une idée" else " Autre idée")
            }
        }
    }
}
