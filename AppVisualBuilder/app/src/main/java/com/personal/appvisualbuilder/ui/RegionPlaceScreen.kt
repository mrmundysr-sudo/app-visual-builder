package com.personal.appvisualbuilder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Place the template's regions over the page art: pick a region, then drag a rectangle (box) or
 * tap (point). Works before the clean art exists, using the template's art size for the canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPlaceScreen(vm: AppViewModel, pageId: String) {
    val project = vm.current ?: return
    val page = project.pages.firstOrNull { it.id == pageId } ?: return
    val need = page.need ?: return
    val clean = page.clean()
    val imgSize = remember(clean?.id) { clean?.let { vm.exporter.imageSize(project, it) } }
    val ratio = imgSize?.let { it.first.toFloat() / it.second }
        ?: ((need.width ?: 9).toFloat() / (need.height ?: 16).toFloat())

    var selected by remember { mutableStateOf(need.regions.firstOrNull { !it.placed }?.label ?: need.regions.firstOrNull()?.label) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val current = need.regions.firstOrNull { it.label == selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Place regions: ${page.name}") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(ratio).background(Color(0xFF1E1E1E))) {
                    if (clean != null) {
                        AsyncImage(
                            model = vm.repo.assetFile(project.id, clean),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(selected) {
                                detectTapGestures { tap ->
                                    val r = need.regions.firstOrNull { it.label == selected }
                                    if (r != null && !r.isBox) {
                                        vm.setRegion(page.id, r.label, norm(tap.x, size.width), norm(tap.y, size.height), null, null)
                                    }
                                }
                            }
                            .pointerInput(selected) {
                                detectDragGestures(
                                    onDragStart = { dragStart = it; dragNow = it },
                                    onDrag = { change, _ -> dragNow = change.position },
                                    onDragCancel = { dragStart = null; dragNow = null },
                                    onDragEnd = {
                                        val a = dragStart
                                        val b = dragNow
                                        val r = need.regions.firstOrNull { it.label == selected }
                                        if (a != null && b != null && r != null) {
                                            val x1 = norm(a.x, size.width); val y1 = norm(a.y, size.height)
                                            val x2 = norm(b.x, size.width); val y2 = norm(b.y, size.height)
                                            if (r.isBox) {
                                                val w = kotlin.math.abs(x2 - x1)
                                                val h = kotlin.math.abs(y2 - y1)
                                                if (w > 0.005 && h > 0.005) {
                                                    vm.setRegion(page.id, r.label, minOf(x1, x2), minOf(y1, y2), w, h)
                                                }
                                            } else {
                                                vm.setRegion(page.id, r.label, x2, y2, null, null)
                                            }
                                        }
                                        dragStart = null; dragNow = null
                                    }
                                )
                            }
                    ) {
                        val w = this.size.width
                        val h = this.size.height
                        need.regions.filter { it.placed }.forEach { r ->
                            val on = r.label == selected
                            val color = if (on) Color(0xFFFFD54F) else accent
                            val stroke = Stroke(width = if (on) 5f else 3f)
                            val tl = Offset(((r.x ?: 0.0) * w).toFloat(), ((r.y ?: 0.0) * h).toFloat())
                            if (r.isBox) {
                                drawRect(color, tl, Size(((r.w ?: 0.0) * w).toFloat(), ((r.h ?: 0.0) * h).toFloat()), style = stroke)
                                drawText(measurer, r.label, tl + Offset(4f, 2f), TextStyle(color = color, fontSize = 12.sp))
                            } else {
                                drawCircle(color, radius = 12f, center = tl, style = stroke)
                                drawLine(color, tl - Offset(18f, 0f), tl + Offset(18f, 0f), strokeWidth = 3f)
                                drawLine(color, tl - Offset(0f, 18f), tl + Offset(0f, 18f), strokeWidth = 3f)
                                drawText(measurer, r.label, tl + Offset(16f, 10f), TextStyle(color = color, fontSize = 12.sp))
                            }
                        }
                        val a = dragStart
                        val b = dragNow
                        if (a != null && b != null && current?.isBox == true) {
                            drawRect(Color(0xFFFFD54F), Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                                Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(width = 4f))
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    need.regions.forEach { r ->
                        FilterChip(
                            selected = r.label == selected,
                            onClick = { selected = r.label },
                            label = { Text("${if (r.placed) "[done] " else ""}${r.label} (${r.type})") }
                        )
                    }
                }
                if (current != null) {
                    if (current.action.isNotBlank()) {
                        Text(current.action, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        if (current.isBox) "Drag on the image to draw the box. Draw again to move it."
                        else "Tap the image to set the point. Tap again to move it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (current.placed) {
                        OutlinedButton(onClick = { vm.clearRegion(page.id, current.label) }) { Text("Clear this region") }
                    }
                }
                if (clean == null) {
                    Text("No clean art yet. Showing the template's art size; you can place regions now and adjust later.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun norm(v: Float, total: Int): Double = (v / total.toFloat()).toDouble().coerceIn(0.0, 1.0)
