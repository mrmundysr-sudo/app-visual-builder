package com.personal.appvisualbuilder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.appvisualbuilder.model.Page

/**
 * Setup area: rename project, add / rename / delete / reorder pages.
 * Reordering uses up/down buttons in this starter. Swap in drag handles later
 * (repo.reorder(projectId, from, to) already supports it).
 * Page IDs never change when you rename or reorder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: AppViewModel) {
    val project = vm.current ?: return
    var renameProject by remember { mutableStateOf(false) }
    var addPage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { renameProject = true }) { Icon(Icons.Default.Edit, "Rename project") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addPage = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add page") }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                project.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
            )
            Text(
                "Order here is the order of the flow preview.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(project.pages, key = { _, p -> p.id }) { index, page ->
                    SetupRow(vm, page, index, project.pages.size)
                }
            }
        }
    }

    if (renameProject) NameDialog("Rename project", project.name,
        onConfirm = { renameProject = false; vm.renameProject(project.id, it) }, onDismiss = { renameProject = false })
    if (addPage) NameDialog("New page name", "Page ${project.pages.size + 1}", confirmLabel = "Add",
        onConfirm = { addPage = false; vm.addPage(it) }, onDismiss = { addPage = false })
}

@Composable
private fun SetupRow(vm: AppViewModel, page: Page, index: Int, count: Int) {
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${index + 1}. ${page.name}", style = MaterialTheme.typography.titleMedium)
                Text(page.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.movePage(page.id, -1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "Move up") }
            IconButton(onClick = { vm.movePage(page.id, 1) }, enabled = index < count - 1) { Icon(Icons.Default.ArrowDownward, "Move down") }
            IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "Rename") }
            IconButton(onClick = { delete = true }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }

    if (rename) NameDialog("Rename page", page.name, onConfirm = { rename = false; vm.renamePage(page.id, it) }, onDismiss = { rename = false })
    if (delete) ConfirmDialog(
        "Delete \"${page.name}\"?",
        "The page is removed and its files move to the project archive. The ID ${page.id} is retired and won't be reused.",
        onConfirm = { delete = false; vm.deletePage(page.id) },
        onDismiss = { delete = false }
    )
}
