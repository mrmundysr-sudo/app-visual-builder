package com.personal.appvisualbuilder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.personal.appvisualbuilder.model.CoordItem
import com.personal.appvisualbuilder.model.Slot

/**
 * Shows the clean PNG with the coords items drawn on top.
 * The image box is sized to the image's exact aspect ratio so normalized 0..1 values land correctly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordsPreviewScreen(vm: AppViewModel, pageId: String) {
    val project = vm.current ?: return
    val page = project.pages.firstOrNull { it.id == pageId } ?: return
    val clean = page.clean()
    val result = remember(page) { vm.exporter.readCoords(project, page) }
    val size = remember(clean?.id) { clean?.let { vm.exporter.imageSize(project, it) } }
    var selected by remember { mutableStateOf<CoordItem?>(null) }
    val measurer = rememberTextMeasurer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapping: ${page.name}") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        if (clean == null || result == null || size == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Needs clean art and a coords file.")
            }
            return@Scaffold
        }
        val items = result.items
        val ratio = size.first.toFloat() / size.second.toFloat()
        val accent = MaterialTheme.colorScheme.primary

        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(ratio)) {
                    AsyncImage(
                        model = vm.repo.assetFile(project.id, clean),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(items) {
                            detectTapGestures { tap ->
                                val nx = tap.x / this.size.width
                                val ny = tap.y / this.size.height
                                selected = items.minByOrNull { distance(it, nx, ny) }
                                    ?.takeIf { distance(it, nx, ny) < 0.08 }
                            }
                        }
                    ) {
                        val w = this.size.width
                        val h = this.size.height
                        items.forEach { item ->
                            val on = item == selected
                            val color = if (on) Color(0xFFFFD54F) else accent
                            val stroke = Stroke(width = if (on) 5f else 3f)
                            if (item.isBox) {
                                val tl = Offset((item.x * w).toFloat(), (item.y * h).toFloat())
                                val sz = Size(((item.w ?: 0.0) * w).toFloat(), ((item.h ?: 0.0) * h).toFloat())
                                drawRect(color, tl, sz, style = stroke)
                                drawText(measurer, "${item.n} ${item.label}", tl + Offset(4f, 2f),
                                    TextStyle(color = color, fontSize = 12.sp))
                            } else {
                                val c = Offset((item.x * w).toFloat(), (item.y * h).toFloat())
                                drawCircle(color, radius = 12f, center = c, style = stroke)
                                drawLine(color, c - Offset(18f, 0f), c + Offset(18f, 0f), strokeWidth = 3f)
                                drawLine(color, c - Offset(0f, 18f), c + Offset(0f, 18f), strokeWidth = 3f)
                                drawText(measurer, "${item.n} ${item.label}", c + Offset(16f, 10f),
                                    TextStyle(color = color, fontSize = 12.sp))
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                val s = selected
                if (s == null) {
                    Text("${items.size} items. Tap one to see its values.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("${s.n}: ${s.label.ifBlank { "(no label)" }}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (s.isBox) "box  x=${s.x}  y=${s.y}  w=${s.w}  h=${s.h}" else "point  x=${s.x}  y=${s.y}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                result.warnings.take(3).forEach {
                    Text("Warning: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Distance in normalized space from a tap to an item (0 if inside a box). */
private fun distance(item: CoordItem, nx: Float, ny: Float): Double {
    return if (item.isBox) {
        val w = item.w ?: 0.0
        val h = item.h ?: 0.0
        val dx = maxOf(item.x - nx, 0.0, nx - (item.x + w))
        val dy = maxOf(item.y - ny, 0.0, ny - (item.y + h))
        kotlin.math.hypot(dx, dy)
    } else {
        kotlin.math.hypot(item.x - nx, item.y - ny)
    }
}
