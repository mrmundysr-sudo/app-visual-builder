package com.personal.appvisualbuilder.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.personal.appvisualbuilder.model.Page
import com.personal.appvisualbuilder.model.Slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen flow preview.
 * - Pure black background, art scaled to fit (never cropped), follows phone rotation.
 * - Swipe, or tap the left/right 20% edges, to move between pages in setup order.
 * - Tap the center to show/hide the overlay (page name, "3 of 7", markup toggle, exit).
 * - At either end a hint appears ("End of flow" / "Start of flow"); the next attempt wraps around.
 */
@Composable
fun FlowPreviewScreen(vm: AppViewModel, startIndex: Int) {
    val project = vm.current ?: return
    val pages = project.pages
    if (pages.isEmpty()) { vm.back(); return }

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, pages.lastIndex)) { pages.size }

    // Immersive mode while this screen is showing.
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Overlay visibility (auto-hides) and per-page markup selection (-1 = clean art).
    var overlay by remember { mutableStateOf(false) }
    var overlayTick by remember { mutableIntStateOf(0) }
    val markupIndex = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(overlay, overlayTick) {
        if (overlay) { delay(2500); overlay = false }
    }

    // End-of-flow hint. `armed` = direction of the last blocked attempt (+1 forward, -1 back).
    var hint by remember { mutableStateOf<String?>(null) }
    var armed by remember { mutableIntStateOf(0) }
    LaunchedEffect(hint) { if (hint != null) { delay(1400); hint = null } }
    LaunchedEffect(pagerState.currentPage) { armed = 0 }

    fun step(direction: Int) {
        val target = pagerState.currentPage + direction
        if (target in pages.indices) {
            armed = 0
            scope.launch { pagerState.animateScrollToPage(target) }
        } else if (armed == direction) {
            armed = 0
            scope.launch { pagerState.animateScrollToPage(if (direction > 0) 0 else pages.lastIndex) }
        } else {
            armed = direction
            hint = if (direction > 0) "End of flow" else "Start of flow"
        }
    }

    // Catches a swipe that tries to go past the first/last page.
    val overscroll = remember {
        object : NestedScrollConnection {
            private var handled = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.x != 0f && !handled) {
                    handled = true
                    // Dragging left (negative) means "go forward".
                    step(if (available.x < 0f) 1 else -1)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                handled = false
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .nestedScroll(overscroll)
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val w = size.width
                    when {
                        tap.x < w * 0.2f -> step(-1)
                        tap.x > w * 0.8f -> step(1)
                        else -> { overlay = !overlay; overlayTick++ }
                    }
                }
            }
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            val page = pages[index]
            PageArt(vm, project.id, page, markupIndex[page.id] ?: -1)
        }

        // Edge hint
        AnimatedVisibility(
            visible = hint != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
        ) {
            Text(
                hint ?: "",
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }

        // Overlay: page name, position, markup toggle, exit
        AnimatedVisibility(
            visible = overlay,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val page = pages[pagerState.currentPage]
            val markups = page.filesIn(Slot.MARKUP)
            val shown = markupIndex[page.id] ?: -1
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(page.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${pagerState.currentPage + 1} of ${pages.size}" +
                            if (shown >= 0) "  |  Markup ${shown + 1}/${markups.size}" else "",
                        color = Color(0xFFB4BAC6),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (markups.isNotEmpty()) {
                    IconButton(onClick = {
                        // clean -> markup 1 -> markup 2 ... -> clean
                        markupIndex[page.id] = if (shown + 1 >= markups.size) -1 else shown + 1
                        overlayTick++
                    }) { Icon(Icons.Default.Layers, "Toggle markup", tint = Color.White) }
                }
                IconButton(onClick = { vm.back() }) { Icon(Icons.Default.Close, "Exit preview", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun PageArt(vm: AppViewModel, projectId: String, page: Page, markupShown: Int) {
    val markups = page.filesIn(Slot.MARKUP)
    val asset = if (markupShown in markups.indices) markups[markupShown] else page.clean()

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (asset == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(page.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text("No clean art yet", color = Color(0xFF8A90A0), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            AsyncImage(
                model = vm.repo.assetFile(projectId, asset),
                contentDescription = page.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
