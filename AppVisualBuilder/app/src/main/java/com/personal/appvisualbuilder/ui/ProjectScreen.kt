package com.personal.appvisualbuilder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.appvisualbuilder.export.Problem
import com.personal.appvisualbuilder.model.Page
import com.personal.appvisualbuilder.model.Slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(vm: AppViewModel) {
    val project = vm.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val problems = remember(project) { vm.exporter.check(project) }
    var showCheck by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    val importNeeds = rememberNeedsImport(vm, update = true)
    val openByPage = remember(project) { project.pages.associate { it.id to vm.exporter.openNeeds(project, it).size } }
    val firstOpen = project.pages.firstOrNull { (openByPage[it.id] ?: 0) > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project.name) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = importNeeds) { Icon(Icons.Default.Sync, "Update from needs file") }
                    IconButton(onClick = { vm.go(Screen.Setup(project.id)) }) { Icon(Icons.Default.Settings, "Setup") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.go(Screen.FlowPreview(project.id)) },
                    enabled = project.pages.any { it.clean() != null },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Preview flow")
                }
                OutlinedButton(onClick = { showCheck = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
            }

            if (firstOpen != null) {
                val total = openByPage.values.sum()
                TextButton(
                    onClick = { vm.go(Screen.PageEditor(project.id, firstOpen.id)) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("$total still needed. Continue with ${firstOpen.name}") }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(project.pages, key = { _, p -> p.id }) { index, page ->
                    PageRow(vm, project.id, index, page, problems.any { it.pageId == page.id }, openByPage[page.id] ?: 0)
                }
            }
        }
    }

    if (showCheck) {
        ExportCheckDialog(
            problems = problems,
            busy = busy,
            onExport = {
                busy = true
                scope.launch {
                    val zip = withContext(Dispatchers.IO) { vm.exporter.exportZip(project, context.cacheDir) }
                    busy = false
                    showCheck = false
                    exported = zip
                }
            },
            onDismiss = { if (!busy) showCheck = false }
        )
    }

    exported?.let { zip ->
        AlertDialog(
            onDismissRequest = { exported = null },
            title = { Text("Export ready") },
            text = { Text(zip.name) },
            confirmButton = { TextButton(onClick = { shareZip(context, zip) }) { Text("Share") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { saveZipToDownloads(context, zip) }) { Text("Save to Downloads") }
                    TextButton(onClick = { exported = null }) { Text("Done") }
                }
            }
        )
    }
}

@Composable
private fun PageRow(vm: AppViewModel, projectId: String, index: Int, page: Page, hasProblem: Boolean, open: Int) {
    val clean = page.clean()
    Card(
        modifier = Modifier.fillMaxWidth().clickable { vm.go(Screen.PageEditor(projectId, page.id)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(clean?.let { vm.repo.assetFile(projectId, it) }, clean?.extension ?: "", 64.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${index + 1}. ${page.name}", style = MaterialTheme.typography.titleMedium)
                Text(page.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (page.need != null) {
                    Text(
                        if (open == 0) "Template complete" else "$open still needed",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (open == 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(0xFFFFB74D)
                    )
                }
                Spacer(Modifier.padding(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Slot.CLEAN, Slot.MARKUP, Slot.PSD, Slot.COORDS).forEach { slot ->
                        val n = page.filesIn(slot).size
                        AssistChip(
                            onClick = { vm.go(Screen.PageEditor(projectId, page.id)) },
                            label = { Text(if (n == 0) slot.label else if (slot.multiple) "${slot.label} $n" else slot.label) },
                            enabled = n > 0
                        )
                    }
                }
            }
            if (hasProblem) Icon(Icons.Default.Warning, "Has problems", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ExportCheckDialog(problems: List<Problem>, busy: Boolean, onExport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (problems.isEmpty()) "Ready to export" else "${problems.size} thing(s) to check") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (problems.isEmpty()) Text("No problems found.")
                problems.forEach { Text("- ${it.message}", style = MaterialTheme.typography.bodyMedium) }
                if (problems.isNotEmpty()) {
                    Text(
                        "You can still export. These are also listed in the README.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport, enabled = !busy) { Text(if (busy) "Exporting..." else if (problems.isEmpty()) "Export" else "Export anyway") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}
