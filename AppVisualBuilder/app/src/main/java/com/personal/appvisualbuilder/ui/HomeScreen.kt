package com.personal.appvisualbuilder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.personal.appvisualbuilder.model.Project
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    var showNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("App Visual Builder") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New project") }
            )
        }
    ) { pad ->
        if (vm.projects.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No projects yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(4.dp))
                Text(
                    "Tap New project, name your app, and list its pages (splash, gameplay, win, loss...).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.projects, key = { it.id }) { project ->
                    ProjectRow(vm, project)
                }
            }
        }
    }

    if (showNew) {
        NewProjectDialog(
            onCreate = { name, pages -> showNew = false; vm.createProject(name, pages) },
            onDismiss = { showNew = false }
        )
    }
}

@Composable
private fun ProjectRow(vm: AppViewModel, project: Project) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val firstClean = project.pages.firstNotNullOfOrNull { it.clean() }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { vm.go(Screen.ProjectHome(project.id)) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(firstClean?.let { vm.repo.assetFile(project.id, it) }, firstClean?.extension ?: "")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${project.pages.size} pages | ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.modifiedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; vm.go(Screen.ProjectHome(project.id)) })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = true })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; vm.duplicateProject(project.id) })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; delete = true })
            }
        }
    }

    if (rename) NameDialog("Rename project", project.name, onConfirm = { rename = false; vm.renameProject(project.id, it) }, onDismiss = { rename = false })
    if (delete) ConfirmDialog(
        "Delete \"${project.name}\"?",
        "This removes the project and all its files, including the archive. This can't be undone.",
        onConfirm = { delete = false; vm.deleteProject(project.id) },
        onDismiss = { delete = false }
    )
}
