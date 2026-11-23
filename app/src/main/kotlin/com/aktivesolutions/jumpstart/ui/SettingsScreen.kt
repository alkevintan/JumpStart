package com.aktivesolutions.jumpstart.ui

import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aktivesolutions.jumpstart.UiState

@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onScheme: (String) -> Unit,
    onThemed: (Boolean) -> Unit,
    onClearRecents: () -> Unit,
    onClearTags: () -> Unit,
    onEdgeBar: (Boolean) -> Unit,
    onEdgeSide: (String) -> Unit,
    onAllowOverlay: () -> Unit,
    onSplit: (Boolean) -> Unit,
    onAllowShortcut: () -> Unit,
    onSetDefaultHome: () -> Unit,
) {
    val c = Js.c
    val ctx = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    val licenseText = remember {
        runCatching { ctx.assets.open("WORDNET_LICENSE.txt").bufferedReader().use { it.readText() } }.getOrDefault("WordNet 3.0, Princeton University.")
    }
    // Esc = back. Root holds focus (no visible indication); onKeyEvent bubbles so a focused child doesn't matter.
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(c.surface)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.key != Key.Escape) false
                else { if (e.type == KeyEventType.KeyDown) onBack(); true }
            }
            .focusTarget(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.fillMaxSize().widthIn(max = 560.dp).windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                    SymbolIcon(Icons.AutoMirrored.Rounded.ArrowBack, 24.dp, c.on)
                }
                T("Settings", 22, 28, c.on, 400, modifier = Modifier.padding(start = 8.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card {
                    GroupLabel("Colour scheme", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                    val options = Schemes.names + if (Schemes.dynamicAvailable) listOf(Schemes.DYNAMIC) else emptyList()
                    options.forEach { name ->
                        val selected = state.scheme == name
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onScheme(name) }.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val sw = Schemes.all[name]
                            Row(Modifier.clip(CircleShape).border(1.dp, c.outline, CircleShape)) {
                                Box(Modifier.size(width = 20.dp, height = 32.dp).background(sw?.pc ?: c.primary))
                                Box(Modifier.size(width = 20.dp, height = 32.dp).background(sw?.primary ?: c.tert))
                            }
                            T(if (name == Schemes.DYNAMIC) "Wallpaper (Material You)" else name, 16, 24, c.on, 400, 0.5f, Modifier.weight(1f))
                            if (selected) SymbolIcon(Icons.Rounded.Check, 24.dp, c.primary)
                        }
                    }
                }
                Card {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onThemed(!state.themedIcons) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T("Themed icons", 16, 24, c.on, 400, 0.5f)
                            T("Letter badges tinted to the colour scheme instead of app icons", 14, 20, c.onv, 400, 0.25f)
                        }
                        Switch(
                            checked = state.themedIcons, onCheckedChange = onThemed,
                            colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary),
                        )
                    }
                }
                Card {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onEdgeBar(!state.edgeBar) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T("Edge swipe search", 16, 24, c.on, 400, 0.5f)
                            T("Swipe in from the screen edge in any app to search", 14, 20, c.onv, 400, 0.25f)
                        }
                        Switch(
                            checked = state.edgeBar, onCheckedChange = onEdgeBar,
                            colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary),
                        )
                    }
                    if (state.edgeBar && !state.overlayGranted) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            T("Needs permission to display over other apps.", 14, 20, c.onv, 400, 0.25f)
                            Row { PillButton("Allow display over other apps", c.primary, c.onPrimary, onClick = onAllowOverlay) }
                        }
                    }
                    if (state.edgeBar) listOf("Right", "Left").forEach { side ->
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onEdgeSide(side) }.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T("$side edge", 16, 24, c.on, 400, 0.5f, Modifier.weight(1f))
                            if (state.edgeSide == side) SymbolIcon(Icons.Rounded.Check, 24.dp, c.primary)
                        }
                    }
                }
                Card {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("Alt+Space quick search", 16, 24, c.on, 400, 0.5f)
                        T(
                            if (state.shortcutEnabled) "On: press Alt+Space in any app to search"
                            else "Off: needs the JumpStart accessibility service, which only watches for this key combination",
                            14, 20, c.onv, 400, 0.25f,
                        )
                        if (!state.shortcutEnabled) Row { PillButton("Turn on in Accessibility settings", c.primary, c.onPrimary, onClick = onAllowShortcut) }
                    }
                }
                // Applies to anything launched from the quick-search overlay (edge swipe or Alt+Space).
                if ((state.edgeBar || state.shortcutEnabled) && remember { com.aktivesolutions.jumpstart.data.splitScreenSupported() }) Card {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onSplit(!state.splitLaunch) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T("Open apps in split screen when possible", 16, 24, c.on, 400, 0.5f)
                            T(
                                "For apps launched from quick search. " +
                                    if (state.shortcutEnabled) "Depends on the device and app."
                                    else "From a full-screen app this needs the Alt+Space accessibility service turned on (above); otherwise it only works when split screen is already active.",
                                14, 20, c.onv, 400, 0.25f,
                            )
                        }
                        Switch(
                            checked = state.splitLaunch, onCheckedChange = onSplit,
                            colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary),
                        )
                    }
                }
                Card {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Set as default home app", c.primary, c.onPrimary, onClick = onSetDefaultHome)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Clear recent searches", c.sec, c.osec, onClick = onClearRecents)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Clear all app tags", c.sec, c.osec, onClick = onClearTags)
                        }
                    }
                }
                Card {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GroupLabel("Gestures")
                        T("Swipe down: search", 14, 20, c.onv, 400, 0.25f)
                        T("Swipe down from the left or right edge: notifications", 14, 20, c.onv, 400, 0.25f)
                        T("Edge swipe (when enabled): swipe in from the screen edge in any app to search (hidden while a keyboard is connected)", 14, 20, c.onv, 400, 0.25f)
                        T("Alt+Space in any app (after enabling the accessibility service below): quick search", 14, 20, c.onv, 400, 0.25f)
                        T("With a keyboard attached (tablet): just start typing to search", 14, 20, c.onv, 400, 0.25f)
                    }
                }
                Card {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GroupLabel("Dictionary")
                        T("Definitions come from WordNet 3.0, © 2006 Princeton University. Bundled offline.", 14, 20, c.onv, 400, 0.25f)
                        TextActionButton("WordNet licence", c.primary, modifier = Modifier.padding(start = 0.dp)) { showLicense = true }
                    }
                }
                Box(Modifier.padding(bottom = 16.dp))
            }
        }
    }
    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Close") } },
            title = { Text("WordNet 3.0 licence") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(licenseText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) } },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Js.c.sch1).padding(vertical = 4.dp)) { content() }
}
