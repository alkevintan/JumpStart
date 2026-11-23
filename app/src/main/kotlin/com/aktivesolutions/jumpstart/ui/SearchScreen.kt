package com.aktivesolutions.jumpstart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import com.aktivesolutions.jumpstart.UiState
import com.aktivesolutions.jumpstart.data.AppRepository
import com.aktivesolutions.jumpstart.search.Item
import com.aktivesolutions.jumpstart.search.Kind
import com.aktivesolutions.jumpstart.search.SearchResult

@Immutable
class SearchActions(
    val onQuery: (String) -> Unit,
    val onClose: () -> Unit,
    val onClear: () -> Unit,
    val onEnter: () -> Unit,
    val onVoice: () -> Unit,
    val onPrimary: (Item) -> Unit,
    val onMessage: (Item) -> Unit,
    val onLaunchApp: (Item) -> Unit,
    val onCopy: () -> Unit,
    val onMessageTop: () -> Unit,
    val onCopyTop: () -> Unit,
    val onCalculator: () -> Unit,
    val onWeb: (String) -> Unit,
    val onFullDict: () -> Unit,
    val onAllowContacts: () -> Unit,
    val onEditTags: (Item) -> Unit,
)

@Composable
fun SearchScreen(state: UiState, apps: AppRepository, a: SearchActions, overlay: Boolean = false) {
    val c = Js.c
    // Keyboard highlight: index into [acts], -1 = none. Reset whenever the query or the result changes, so it can never
    // point into a list that has been rebuilt underneath it.
    val selState = remember(state.query, state.result, state.suggested) { mutableIntStateOf(-1) }
    val acts = remember(state.query, state.result, state.dict, state.suggested) { selectable(state, a) }
    val sel = selState.intValue
    // Selection is read live (selState), not from this composition's snapshot, so two arrows in one frame both count and
    // Down->Enter activates the new highlight.
    val onEnter = { val i = selState.intValue; if (i in acts.indices) acts[i]() else a.onEnter() }
    val onKey: (KeyEvent, String) -> Boolean = key@{ e, text ->
        val act = Shortcuts.search(e.toChord()) ?: return@key false
        val full = !emptyMode(state)
        val q = text.isNotBlank() // live field text, not the last-composed query
        val ok = when (act) {
            SearchAct.Down, SearchAct.Up -> acts.isNotEmpty()
            SearchAct.Message, SearchAct.Copy -> q // acted on after a resync in the VM, so the on-screen result may be a keystroke behind
            SearchAct.Dict -> full && state.dict != null
            is SearchAct.Web -> q
            is SearchAct.Launch -> !q && act.n < state.suggested.take(8).size
            else -> true
        }
        if (!ok) return@key false
        // Act on KeyDown only (no auto-repeat except arrows); KeyUp of a handled chord is consumed too so it can't leak
        // to the field or the activity.
        if (e.type == KeyEventType.KeyDown && (e.nativeKeyEvent.repeatCount == 0 || act == SearchAct.Down || act == SearchAct.Up)) when (act) {
            SearchAct.Down -> selState.intValue = Shortcuts.move(selState.intValue, acts.size, true)
            SearchAct.Up -> selState.intValue = Shortcuts.move(selState.intValue, acts.size, false)
            SearchAct.Enter -> onEnter()
            SearchAct.Message -> a.onMessageTop()
            SearchAct.Esc -> if (text.isNotEmpty()) a.onClear() else a.onClose()
            SearchAct.Copy -> a.onCopyTop()
            SearchAct.Dict -> a.onFullDict()
            is SearchAct.Web -> a.onWeb(act.kind)
            is SearchAct.Launch -> a.onLaunchApp(state.suggested[act.n])
        }
        true
    }
    // Overlay: slightly see-through so the app underneath reads as being behind the search (the dim comes from the window).
    Box(Modifier.fillMaxSize().background(if (overlay) c.surface.copy(alpha = 0.94f) else c.surface), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 560.dp).windowInsetsPadding(WindowInsets.statusBars).imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Bar pinned near the top so results get all the space down to the keyboard.
                val top = 16.dp
                val fade = remember(c.surface) { Brush.verticalGradient(listOf(c.surface, Color.Transparent)) }
                val scrollState = rememberScrollState()
                // A new query shows the top of its results.
                LaunchedEffect(state.query) { scrollState.scrollTo(0) }
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(top))
                    SearchBar(state.query, a, onKey, onEnter, Modifier.zIndex(1f).padding(start = 12.dp, end = 12.dp)) // zIndex: keep its shadow above the results
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(scrollState).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { Results(state, apps, a, sel) }
                        // Top-edge fade just under the bar, shown only once scrolled (draw-phase read); no pointer input.
                        Box(
                            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(24.dp)
                                .graphicsLayer { alpha = (scrollState.value / 24.dp.toPx()).coerceIn(0f, 1f) }
                                .background(fade),
                        )
                    }
                }
            }
        }
    }
}

// Results are in "empty" mode (suggestions) for a blank query, and for the one frame before the first async result.
private fun emptyMode(state: UiState) = state.query.trim().isEmpty() || (state.result == SearchResult() && state.dict == null)

/** Actionable rows in on-screen order (top to bottom). Must mirror Results. */
private fun selectable(state: UiState, a: SearchActions): List<() -> Unit> {
    if (emptyMode(state)) return state.suggested.take(8).map { { a.onLaunchApp(it) } }
    val r = state.result
    return buildList<() -> Unit> {
        r.top?.let { add { a.onPrimary(it) } }
        r.groups.forEach { g -> g.items.forEach { add { a.onPrimary(it) } } }
        if (r.showWeb) listOf("web", "store", "maps", "wiki").forEach { add { a.onWeb(it) } }
    }
}

/** Keyboard-selection ring + scroll into view. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Modifier.hl(on: Boolean, shape: Shape): Modifier {
    val req = remember { BringIntoViewRequester() }
    LaunchedEffect(on) { if (on) req.bringIntoView() }
    return bringIntoViewRequester(req).then(if (on) Modifier.border(2.dp, Js.c.primary, shape) else Modifier)
}

@Composable
private fun Results(state: UiState, apps: AppRepository, a: SearchActions, sel: Int) {
    val c = Js.c
    val r = state.result
    val q = state.query.trim()
    // Results are computed async: on the first keystroke the result is still the empty SearchResult() (a real
    // result for a non-empty query always has an answer or showWeb). Keep the suggestions up for that frame
    // instead of flashing a blank list.
    if (emptyMode(state)) {
        EmptyState(state, apps, a, sel)
        return
    }
    r.answer?.let { ans ->
        val copied = state.copiedValue == ans.value
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.pc).padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SymbolIcon(Icons.Rounded.Calculate, 18.dp, c.opc)
                T(ans.label, 12, 16, c.opc, 500, 0.5f)
            }
            T(ans.expr, 16, 24, c.opc, 400, 0.5f, Modifier.padding(top = 8.dp))
            T(ans.value, 45, 52, c.opc, 400)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (copied) "Copied" else "Copy", c.primary, c.onPrimary, Icons.Rounded.ContentCopy, a.onCopy)
                TextActionButton("Open ${ans.app}", c.opc, onClick = a.onCalculator)
            }
        }
    }
    var n = 0 // running index into selectable()
    r.top?.let { TopHit(it, state.themedIcons, apps, a, sel == n++) }
    state.dict?.let { d ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sch1)
                .border(1.dp, c.outlineVar, RoundedCornerShape(28.dp))
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SymbolIcon(Icons.Rounded.MenuBook, 18.dp, c.primary)
                T("Dictionary", 12, 16, c.primary, 500, 0.5f)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                T(d.word, 28, 36, c.on, 400)
                if (!d.phon.isNullOrEmpty()) T(d.phon, 14, 20, c.onv, 400, 0.25f)
            }
            d.senses.take(2).forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(s.pos, 12, 16, c.onv, 500, 0.5f, italic = true)
                    T(s.def, 16, 24, c.on, 400, 0.5f)
                }
            }
            TextActionButton("Full definition", c.primary, modifier = Modifier.padding(start = 0.dp), onClick = a.onFullDict)
        }
    }
    if (r.groups.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sch1).padding(vertical = 8.dp)) {
            r.groups.forEach { g ->
                GroupLabel(g.name, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
                g.items.forEach { ListRow(it, state.themedIcons, apps, a.onPrimary, a.onEditTags, sel == n++) }
            }
        }
    }
    if (r.showWeb) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sch1).padding(vertical = 8.dp)) {
            LinkRow(Icons.Rounded.Public, "Search the web for “$q”", sel == n++) { a.onWeb("web") }
            LinkRow(Icons.Rounded.Storefront, "Find “$q” in Play Store", sel == n++) { a.onWeb("store") }
            LinkRow(Icons.Rounded.LocationOn, "Look up “$q” in Maps", sel == n++) { a.onWeb("maps") }
            LinkRow(Icons.Rounded.Language, "Look up “$q” on Wikipedia", sel == n++) { a.onWeb("wiki") }
        }
    }
}

@Composable
private fun TopHit(item: Item, themed: Boolean, apps: AppRepository, a: SearchActions, on: Boolean) {
    val c = Js.c
    val (action, icon) = when (item.kind) {
        Kind.APP -> "Open" to Icons.Rounded.OpenInNew
        Kind.CONTACT -> "Call" to Icons.Rounded.Call
        Kind.SETTING -> "Open setting" to Icons.Rounded.Settings
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sch4).hl(on, RoundedCornerShape(28.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            (if (item.kind == Kind.APP) Modifier.tapOrHold({ a.onPrimary(item) }, { a.onEditTags(item) }) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ItemAvatar(item, 56.dp, 22, themed, apps)
            Column(Modifier.weight(1f)) {
                T("Top hit", 12, 16, c.primary, 500, 0.5f)
                T(item.title, 22, 28, c.on, 400, maxLines = 2)
                T(item.subtitle, 14, 20, c.onv, 400, 0.25f, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(action, c.primary, c.onPrimary, icon) { a.onPrimary(item) }
            if (item.kind == Kind.CONTACT) PillButton("Message", c.sec, c.osec, Icons.Rounded.Chat) { a.onMessage(item) }
        }
    }
}

@Composable
private fun ListRow(item: Item, themed: Boolean, apps: AppRepository, onPrimary: (Item) -> Unit, onEditTags: (Item) -> Unit, on: Boolean) {
    val c = Js.c
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).hl(on, RoundedCornerShape(0.dp))
            .tapOrHold({ onPrimary(item) }, if (item.kind == Kind.APP) ({ onEditTags(item) }) else null, RoundedCornerShape(0.dp))
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ItemAvatar(item, 40.dp, 16, themed, apps)
        Column(Modifier.weight(1f)) {
            T(item.title, 16, 24, c.on, 400, 0.5f, maxLines = 1)
            T(item.subtitle, 14, 20, c.onv, 400, 0.25f, maxLines = 1)
        }
        when (item.kind) {
            Kind.CONTACT -> SymbolIcon(Icons.Rounded.Call, 24.dp, c.onv)
            Kind.SETTING -> SymbolIcon(Icons.Rounded.ChevronRight, 24.dp, c.onv)
            Kind.APP -> {}
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val c = Js.c
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).hl(on, RoundedCornerShape(0.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { SymbolIcon(icon, 24.dp, c.onv) }
        T(label, 16, 24, c.on, 400, 0.5f, Modifier.weight(1f), maxLines = 2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(state: UiState, apps: AppRepository, a: SearchActions, sel: Int) {
    val c = Js.c
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.contactsGranted) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sch1).clickable(role = Role.Button, onClick = a.onAllowContacts)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.sec), contentAlignment = Alignment.Center) {
                    SymbolIcon(Icons.Rounded.Person, 24.dp, c.osec)
                }
                Column(Modifier.weight(1f)) {
                    T("Allow contacts in search", 16, 24, c.on, 400, 0.5f)
                    T("Find people by name. Stays on this device.", 14, 20, c.onv, 400, 0.25f)
                }
                SymbolIcon(Icons.Rounded.ChevronRight, 24.dp, c.onv)
            }
        }
        GroupLabel("Suggestions", Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        val suggestedRows = remember(state.suggested) { state.suggested.take(8).chunked(4) }
        suggestedRows.forEachIndexed { ri, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { ci, app ->
                    Column(
                        Modifier.weight(1f).hl(sel == ri * 4 + ci, RoundedCornerShape(16.dp)).tapOrHold({ a.onLaunchApp(app) }, { a.onEditTags(app) }).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ItemAvatar(app, 52.dp, 20, state.themedIcons, apps)
                        T(app.title, 12, 16, c.on, 400, 0.4f, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        if (state.recents.isNotEmpty()) {
            GroupLabel("Recent", Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.recents.forEach { r ->
                    Row(
                        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.outline, RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { a.onQuery(r) }.padding(start = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SymbolIcon(Icons.Rounded.History, 18.dp, c.primary)
                        T(r, 14, 20, c.onv, 500, 0.1f, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, a: SearchActions, onKey: (KeyEvent, String) -> Boolean, onEnter: () -> Unit, modifier: Modifier) {
    val c = Js.c
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        kb?.show()
    }
    // TextFieldValue (not the String overload, whose cursor starts at 0): a prefilled query from Home's first keystroke
    // must leave the cursor after it. [tfv] is the source of truth while typing; the VM query reaches us a frame late
    // (StateFlow), so values we've sent are remembered in [sent] and not mistaken for external changes (voice, recents,
    // clear button), which do reset the field with the cursor at the end.
    var tfv by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    val sent = remember { ArrayDeque<String>() }
    LaunchedEffect(query) {
        if (echoReset(query, tfv.text, sent)) tfv = TextFieldValue(query, TextRange(query.length))
    }
    Row(
        modifier.fillMaxWidth().height(56.dp).shadow(3.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(c.sch3)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Close search", c.on, a.onClose)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) T("Search apps, people, the web", 16, 24, c.onv, 400, 0.5f, maxLines = 1)
            BasicTextField(
                value = tfv,
                onValueChange = { v ->
                    val changed = v.text != tfv.text
                    tfv = v
                    if (changed) { sent.addLast(v.text); a.onQuery(v.text) }
                },
                singleLine = true,
                textStyle = TextStyle(color = c.on, fontSize = 16.sp, letterSpacing = 0.5.sp),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onEnter() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Search" }
                    .onPreviewKeyEvent { onKey(it, tfv.text) },
            )
        }
        if (query.isNotEmpty()) IconBtn(Icons.Rounded.Close, "Clear search", c.onv, a.onClear)
        else IconBtn(Icons.Rounded.Mic, "Voice search", c.onv, a.onVoice)
    }
}

@Composable
private fun IconBtn(icon: ImageVector, desc: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { SymbolIcon(icon, 24.dp, tint) }
}
