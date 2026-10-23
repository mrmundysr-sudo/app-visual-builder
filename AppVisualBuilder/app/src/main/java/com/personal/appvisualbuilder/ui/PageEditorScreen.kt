package com.personal.appvisualbuilder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.appvisualbuilder.model.AssetFile
import com.personal.appvisualbuilder.model.Naming
import com.personal.appvisualbuilder.model.Page
import com.personal.appvisualbuilder.model.Slot
import kotlinx.coroutines.delay

/** What the user is in the middle of attaching. */
private data class Pending(val slot: Slot, val uri: Uri, val originalName: String, val replaceAssetId: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(vm: AppViewModel, pageId: String) {
    val project = vm.current ?: return
    val page = project.pages.firstOrNull { it.id == pageId } ?: return

    // Which slot (and optionally which existing file) the picker is currently for.
    var pickSlot by remember { mutableStateOf<Slot?>(null) }
    var pickReplace by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var restoreSlot by remember { mutableStateOf<Slot?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val slot = pickSlot
        if (uri != null && slot != null) {
            pending = Pending(slot, uri, vm.repo.queryName(uri), pickReplace)
        }
    }

    fun launch(slot: Slot, replaceId: String?) {
        pickSlot = slot
        pickReplace = replaceId
        picker.launch(if (slot == Slot.CLEAN || slot == Slot.MARKUP) "image/*" else "*/*")
    }

    // Page notes: edit locally, save shortly after typing stops.
    var notes by remember(page.id) { mutableStateOf(page.notes) }
    LaunchedEffect(notes) {
        if (notes != page.notes) {
            delay(500)
            vm.setNotes(page.id, notes)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${page.name}  (${page.id})") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Page notes for the AI coder") },
                placeholder = { Text("e.g. confetti plays on entry; tap anywhere to continue") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Slot.entries.forEach { slot ->
                SlotCard(
                    vm = vm,
                    projectId = project.id,
                    page = page,
                    slot = slot,
                    onAttach = { launch(slot, null) },
                    onReplace = { asset -> launch(slot, asset.id) },
                    onRestore = { restoreSlot = slot }
                )
            }
        }
    }

    // Name dialog with the suggested filename, editable before saving.
    pending?.let { p ->
        val suggestion = remember(p) {
            if (p.replaceAssetId != null) {
                page.files.firstOrNull { it.id == p.replaceAssetId }?.displayName
                    ?: Naming.suggest(page, p.slot, p.originalName)
            } else Naming.suggest(page, p.slot, p.originalName)
        }
        NameDialog(
            title = "Name this ${p.slot.label.lowercase()} file",
            initial = suggestion,
            confirmLabel = "Save",
            onConfirm = { name ->
                val finalName = name.ifBlank { suggestion }
                if (p.replaceAssetId != null) vm.replaceAsset(page.id, p.replaceAssetId, p.uri, finalName, p.originalName)
                else vm.attach(page.id, p.slot, p.uri, finalName, p.originalName)
                pending = null
            },
            onDismiss = { pending = null }
        )
    }

    restoreSlot?.let { slot ->
        val entries = vm.archivedFor(page.id, slot)
        AlertDialog(
            onDismissRequest = { restoreSlot = null },
            title = { Text("Restore ${slot.label.lowercase()} from archive") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (entries.isEmpty()) Text("Nothing archived for this slot.")
                    entries.forEach { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            FileThumb(vm.repo.archivedFile(project.id, e.asset), e.asset.extension, 44.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(e.asset.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.restore(e); restoreSlot = null }) { Text("Restore") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { restoreSlot = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun SlotCard(
    vm: AppViewModel,
    projectId: String,
    page: Page,
    slot: Slot,
    onAttach: () -> Unit,
    onReplace: (AssetFile) -> Unit,
    onRestore: () -> Unit
) {
    val files = page.filesIn(slot)
    val hasArchive = vm.archivedFor(page.id, slot).isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(slot.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (hasArchive) TextButton(onClick = onRestore) { Text("Restore") }
                if (slot.multiple || files.isEmpty()) {
                    OutlinedButton(onClick = onAttach) { Text(if (slot.multiple) "Add" else "Attach") }
                }
            }

            if (files.isEmpty()) {
                Text(
                    when (slot) {
                        Slot.CLEAN -> "The real game artwork (PNG)."
                        Slot.MARKUP -> "Images that show where things go."
                        Slot.PSD -> "Layered file with named layers."
                        Slot.COORDS -> "JSON coordinates from the toolkit."
                        Slot.OTHER -> "Anything else the coder needs."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            files.forEach { asset ->
                FileRow(vm, projectId, page, asset, onReplace)
            }
        }
    }
}

@Composable
private fun FileRow(vm: AppViewModel, projectId: String, page: Page, asset: AssetFile, onReplace: (AssetFile) -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val file = vm.repo.assetFile(projectId, asset)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileThumb(file, asset.extension)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(asset.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "from ${asset.originalName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "Rename") }
            IconButton(onClick = { onReplace(asset) }) { Icon(Icons.Default.SwapHoriz, "Replace") }
            IconButton(onClick = { remove = true }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
        }

        if (asset.slot == Slot.COORDS) CoordsStatus(vm, page)
    }

    if (rename) NameDialog("Rename file", asset.displayName,
        onConfirm = { rename = false; vm.renameAsset(page.id, asset.id, it) }, onDismiss = { rename = false })
    if (remove) ConfirmDialog(
        "Remove \"${asset.displayName}\"?",
        "It moves to the project archive. You can restore it later.",
        confirmLabel = "Remove",
        onConfirm = { remove = false; vm.removeAsset(page.id, asset.id) },
        onDismiss = { remove = false }
    )
}

@Composable
private fun CoordsStatus(vm: AppViewModel, page: Page) {
    val project = vm.current ?: return
    val coordsId = page.filesIn(Slot.COORDS).firstOrNull()?.id
    val result = remember(coordsId) { vm.exporter.readCoords(project, page) } ?: return

    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (result.ok) {
            Text("Valid: ${result.items.size} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        } else {
            result.errors.forEach { Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        result.warnings.forEach {
            Text("Warning: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (result.ok && page.clean() != null) {
            OutlinedButton(onClick = { vm.go(Screen.CoordsPreview(project.id, page.id)) }) { Text("Preview mapping") }
        } else if (result.ok) {
            Text("Attach clean art to preview the mapping.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
