package com.personal.appvisualbuilder.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.appvisualbuilder.model.NeedsParser
import com.personal.appvisualbuilder.model.NeedsResult

/**
 * Returns a function that opens the file picker for a needs file, then shows what it found and asks
 * before doing anything. [update] = true merges into the open project; false creates a new project.
 */
@Composable
fun rememberNeedsImport(vm: AppViewModel, update: Boolean): () -> Unit {
    var result by remember { mutableStateOf<NeedsResult?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) result = NeedsParser.parse(vm.repo.readText(uri))
    }

    result?.let { r ->
        val file = r.file
        if (!r.ok || file == null) {
            AlertDialog(
                onDismissRequest = { result = null },
                title = { Text("Can't use this file") },
                text = { Lines(r.errors, MaterialTheme.colorScheme.error) },
                confirmButton = { TextButton(onClick = { result = null }) { Text("Close") } }
            )
        } else {
            val needFiles = file.pages.sumOf { it.need.assets.size }
            val regions = file.pages.sumOf { it.need.regions.size }
            AlertDialog(
                onDismissRequest = { result = null },
                title = { Text(if (update) "Update this project?" else "Create \"${file.appName}\"?") },
                text = {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        Text("${file.pages.size} pages, $needFiles files to supply, $regions regions to place.")
                        if (update) Text("Nothing you have added is removed or renamed.", style = MaterialTheme.typography.bodySmall)
                        if (r.warnings.isNotEmpty()) Lines(r.warnings.map { "Note: $it" }, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (update) vm.mergeNeeds(file) else vm.createFromNeeds(file)
                        result = null
                    }) { Text(if (update) "Update" else "Create") }
                },
                dismissButton = { TextButton(onClick = { result = null }) { Text("Cancel") } }
            )
        }
    }
    return { picker.launch("*/*") }
}

@Composable
private fun Lines(lines: List<String>, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        lines.forEach { Text("- $it", style = MaterialTheme.typography.bodyMedium, color = color) }
    }
}
