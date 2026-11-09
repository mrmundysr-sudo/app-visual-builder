package com.personal.appvisualbuilder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personal.appvisualbuilder.model.NeedAsset
import com.personal.appvisualbuilder.model.Page

private val Amber = Color(0xFFFFB74D)

/**
 * The template for a page: every file and region the coder asked for, with Done / Still needed marks.
 * Leaving items empty is always allowed; they are only flagged.
 */
@Composable
fun NeedsChecklist(vm: AppViewModel, page: Page, onFill: (NeedAsset) -> Unit) {
    val project = vm.current ?: return
    val need = page.need ?: return
    val labels = remember(project) { vm.exporter.coordLabels(project, page) }
    val open = remember(project) { vm.exporter.openNeeds(project, page) }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Template", style = MaterialTheme.typography.titleMedium)
            if (need.purpose.isNotBlank()) Text(need.purpose, style = MaterialTheme.typography.bodyMedium)
            val w = need.width
            val h = need.height
            if (w != null && h != null) {
                Text("Art size: $w x $h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (open.isEmpty()) "Everything required is filled." else "${open.size} still needed. You can skip and come back.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (open.isEmpty()) MaterialTheme.colorScheme.primary else Amber
            )

            if (need.layers.isNotEmpty()) {
                Text("Name your PSD layers:", style = MaterialTheme.typography.labelLarge)
                Text(need.layers.joinToString("  |  "), style = MaterialTheme.typography.bodySmall)
            }

            need.assets.forEach { a ->
                val done = a.isFilledIn(page)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Status(done, a.required)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyMedium)
                        val sub = buildString {
                            append(a.slot.label)
                            if (a.description.isNotBlank()) append(": ${a.description}")
                            if (!done) append(if (a.required) "  (still needed)" else "  (optional)")
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall,
                            color = if (!done && a.required) Amber else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { onFill(a) }) { Text(if (done) "Replace" else "Add") }
                }
            }

            if (need.regions.isNotEmpty()) {
                val placed = need.regions.count { vm.exporter.isPlaced(it, labels) }
                Text("Regions: $placed of ${need.regions.size} placed", style = MaterialTheme.typography.labelLarge)
                need.regions.forEach { r ->
                    val done = vm.exporter.isPlaced(r, labels)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Status(done, r.required)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${r.label} (${r.type})", style = MaterialTheme.typography.bodyMedium)
                            if (r.action.isNotBlank()) {
                                Text(r.action, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { vm.go(Screen.RegionPlace(project.id, page.id)) }) { Text("Place regions") }
            }
        }
    }
}

@Composable
private fun Status(done: Boolean, required: Boolean) {
    if (done) Icon(Icons.Default.CheckCircle, "Done", tint = MaterialTheme.colorScheme.primary)
    else Icon(Icons.Default.RadioButtonUnchecked, "Still needed", tint = if (required) Amber else MaterialTheme.colorScheme.onSurfaceVariant)
}
