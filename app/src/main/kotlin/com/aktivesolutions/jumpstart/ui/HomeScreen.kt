package com.aktivesolutions.jumpstart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.aktivesolutions.jumpstart.data.AppRepository
import com.aktivesolutions.jumpstart.search.Item
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** CSS `radial-gradient(rx ry at cx cy, color 0%, transparent 70%)` as an ellipse; the brush is built once per size. */
private class Glow(val center: Offset, val radius: Float, val yScale: Float, val brush: Brush)

private fun glowFor(size: Size, cx: Float, cy: Float, rx: Float, ry: Float, color: Color): Glow {
    val center = Offset(size.width * cx, size.height * cy)
    val radius = size.width * rx
    val rY = size.height * ry
    return Glow(
        center, radius, rY / radius,
        Brush.radialGradient(
            0f to color, 0.7f to color.copy(alpha = 0f), 1f to color.copy(alpha = 0f),
            center = center, radius = radius,
        ),
    )
}

private fun DrawScope.draw(g: Glow) {
    withTransform({ scale(1f, g.yScale, pivot = g.center) }) {
        drawRect(
            brush = g.brush,
            topLeft = Offset(g.center.x - g.radius, g.center.y - g.radius),
            size = Size(g.radius * 2, g.radius * 2),
        )
    }
}

/**
 * Surface colour plus three soft glows. drawWithCache builds the gradient brushes only when the size or colours
 * change, not on every redraw (the clock ticking or the toast showing would otherwise rebuild three shaders).
 */
fun Modifier.wallpaper(c: JsColors): Modifier = this.background(c.surface).drawWithCache {
    val glows = listOf(
        glowFor(size, 0.90f, 0.10f, 0.75f, 0.45f, c.pc),
        glowFor(size, 0.00f, 0.70f, 0.85f, 0.50f, c.tert),
        glowFor(size, 0.70f, 0.95f, 0.60f, 0.35f, c.sec),
    )
    onDrawBehind { for (g in glows) draw(g) }
}

// Restarted by [key] on every resume: delay() runs on uptime, which stops in deep sleep, so after the
// tablet wakes the clock would otherwise show the old minute for up to a minute.
@Composable
private fun rememberNow(key: Int): State<Date> = produceState(Date(), key) {
    while (true) {
        value = Date()
        delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
    }
}

/** Owns the per-minute state, so only this subtree recomposes when the minute changes. */
@Composable
private fun ClockBlock() {
    val c = Js.c
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val now by rememberNow(resumes)
    val clock = remember(now) { SimpleDateFormat("h:mm", Locale.US).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(clock, 72, 80, c.on, 400, -0.5f)
        T(date, 16, 24, c.onv, 500, 0.15f)
    }
}

/**
 * Tablet + hardware keyboard: the search pill is redundant (just start typing, the root's onKeyEvent opens search with
 * the typed character), so hide it. Reading LocalConfiguration keeps this current; a keyboard attach/detach is a config
 * change that recreates the activity anyway. Touch users keep the swipe-down gesture.
 */
@Composable
private fun hideSearchPill(wide: Boolean): Boolean {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    return wide && com.aktivesolutions.jumpstart.edge.hardKeyboardAttached(cfg)
}

@Composable
fun HomeScreen(
    suggested: List<Item>,
    themedIcons: Boolean,
    wide: Boolean,
    apps: AppRepository,
    onOpenSearch: () -> Unit,
    onOpenSearchWith: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onShowNotifications: () -> Unit,
    onLaunch: (Item) -> Unit,
    onEditTags: (Item) -> Unit,
) {
    val c = Js.c
    val swipePx = with(LocalDensity.current) { 48.dp.toPx() }
    val edgeMinPx = with(LocalDensity.current) { 32.dp.toPx() }
    val shown = remember(suggested, wide) { suggested.take(if (wide) 6 else 4) }
    // Hardware keyboard: the root holds focus (focusable() draws no indication and doesn't touch pointer handling) so key
    // events arrive. onKeyEvent bubbles, so this still works if a tile has taken focus. Re-requested on every resume
    // because the window losing focus (dialog, other app) drops Compose focus.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { runCatching { focus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().wallpaper(c)
            .focusRequester(focus)
            .onKeyEvent { e ->
                val act = Shortcuts.home(e.toChord())?.takeUnless { it is HomeAct.Launch && it.n >= shown.size }
                if (act != null) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) when (act) {
                        is HomeAct.Search -> if (act.initial.isEmpty()) onOpenSearch() else onOpenSearchWith(act.initial)
                        HomeAct.Settings -> onOpenSettings()
                        HomeAct.Notifications -> onShowNotifications()
                        is HomeAct.Launch -> onLaunch(shown[act.n])
                    }
                    true
                } else false
            }
            .focusTarget()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenSettings() }) }
            .pointerInput(Unit) {
                // Initial pass so swipes starting on app tiles are seen before the tile's tap/long-press handler.
                // Consuming the changes makes tiles and the background long-press cancel instead of also firing a tap
                // or opening Settings. detectTapGestures (tiles' combinedClickable and the background) does not cancel on movement, only on
                // consumption or leaving bounds, so waiting for the 48dp threshold lets a slow drag hit the long-press
                // timeout first and open Settings / the tag dialog *and* then search. Claim the gesture at touch slop
                // instead (as the old detectVerticalDragGestures did), and treat a hold past the long-press timeout
                // as belonging to the long-press, not a swipe.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val slop = viewConfiguration.touchSlop
                    val holdMs = viewConfiguration.longPressTimeoutMillis
                    var dragging = false
                    var fired = false
                    while (!fired) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } > 1) break
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val d = ch.position - down.position
                        if (!dragging) {
                            if (ch.uptimeMillis - down.uptimeMillis >= holdMs) break
                            dragging = d.y > slop && d.y > abs(d.x)
                        }
                        if (dragging) event.changes.forEach { it.consume() }
                        val kind = classifySwipe(down.position.x, size.width.toFloat(), d.x, d.y, swipePx, edgeMinPx)
                        if (kind != HomeSwipe.None) {
                            fired = true
                            event.changes.forEach { it.consume() }
                            if (kind == HomeSwipe.Search) onOpenSearch() else onShowNotifications()
                        }
                    }
                    if (fired) {
                        // Swallow the rest of this gesture, including the release.
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
            .semantics { customActions = listOf(CustomAccessibilityAction("Show notifications") { onShowNotifications(); true }) },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).widthIn(max = 560.dp)
                .padding(start = 24.dp, end = 24.dp, top = 80.dp, bottom = 32.dp),
        ) {
            ClockBlock()
            Box(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { app ->
                    Column(
                        Modifier.weight(1f).tapOrHold({ onLaunch(app) }, { onEditTags(app) }).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ItemAvatar(app, 56.dp, 22, themedIcons, apps)
                        T(app.title, 12, 16, c.on, 400, 0.4f, maxLines = 1)
                    }
                }
            }
            if (!hideSearchPill(wide)) {
                Row(
                    Modifier.padding(top = 20.dp).fillMaxWidth().height(56.dp)
                        .shadow(2.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(c.sch3)
                        .clickable(role = Role.Button, onClick = onOpenSearch)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SymbolIcon(Icons.Rounded.Search, 24.dp, c.on)
                    T("Search apps, people, the web", 16, 24, c.onv, 400, 0.5f, Modifier.weight(1f), maxLines = 1)
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { SymbolIcon(Icons.Rounded.Mic, 24.dp, c.onv) }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(top = 4.dp, end = 4.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onOpenSettings)
                    .semantics { contentDescription = "Launcher settings" },
                contentAlignment = Alignment.Center,
            ) { SymbolIcon(Icons.Rounded.MoreVert, 24.dp, c.onv) }
        }
    }
}
