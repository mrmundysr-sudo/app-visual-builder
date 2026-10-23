package com.personal.appvisualbuilder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.appvisualbuilder.model.Naming

/** Wizard: app name, number of pages, a name for each page (prefilled with suggestions). */
@Composable
fun NewProjectDialog(onCreate: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    var appName by remember { mutableStateOf("") }
    val pageNames = remember { mutableStateListOf(*Array(5) { Naming.suggestedPageName(it) }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pages: ${pageNames.size}", modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (pageNames.size > 1) pageNames.removeAt(pageNames.lastIndex) }) {
                        Icon(Icons.Default.Remove, "Fewer pages")
                    }
                    IconButton(onClick = { pageNames.add(Naming.suggestedPageName(pageNames.size)) }) {
                        Icon(Icons.Default.Add, "More pages")
                    }
                }
                pageNames.forEachIndexed { i, name ->
                    OutlinedTextField(
                        value = name,
                        onValueChange = { pageNames[i] = it },
                        label = { Text("Page ${i + 1}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Names can be changed later in Setup. Page IDs (page_01, page_02...) are permanent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(appName.trim(), pageNames.toList()) }, enabled = appName.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
