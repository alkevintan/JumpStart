package com.aktivesolutions.jumpstart.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import com.aktivesolutions.jumpstart.JumpStartViewModel
import com.aktivesolutions.jumpstart.Screen
import com.aktivesolutions.jumpstart.search.Tags
import android.speech.RecognizerIntent

@Composable
fun JumpStartApp(vm: JumpStartViewModel, overlay: Boolean = false, onExternalFlow: (Boolean) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermission(); if (!overlay) vm.syncFromPrefs() }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.setQuery(it) }
        }
        onExternalFlow(false)
    }
    val contactsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onExternalFlow(false); vm.refreshPermission() }
    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    val openSearch = remember(vm) { { vm.openSearch() } }

    val actions = remember(vm) {
        SearchActions(
            onQuery = vm::setQuery,
            onClose = vm::goHome,
            onClear = vm::clearQuery,
            onEnter = vm::onEnter,
            onVoice = {
                if (vm.launcher.canVoice()) {
                    onExternalFlow(true) // the overlay activity must not close itself while the recogniser is up
                    runCatching { voice.launch(vm.launcher.voiceIntent()) }.onFailure { onExternalFlow(false); vm.toast("Voice search isn't available") }
                } else vm.toast("Voice search isn't available")
            },
            onPrimary = vm::primary,
            onMessage = vm::message,
            onMessageTop = vm::messageTop,
            onCopyTop = vm::copyTop,
            onLaunchApp = vm::launchApp,
            onCopy = vm::copyAnswer,
            onCalculator = vm::openCalculator,
            onWeb = vm::web,
            onFullDict = { vm.showFullDict(true) },
            onAllowContacts = { onExternalFlow(true); contactsPerm.launch(Manifest.permission.READ_CONTACTS) },
            onEditTags = vm::editTags,
        )
    }

    JumpStartTheme(state.scheme) {
        BackHandler(enabled = true) {
            when (state.screen) {
                Screen.HOME -> {}
                Screen.SEARCH -> vm.goHome()
                Screen.SETTINGS -> vm.goHome()
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            // Overlay shows only search; HOME there means "dismiss" (the activity finishes), so draw nothing.
            if (overlay) { if (state.screen == Screen.SEARCH) SearchScreen(state, vm.appRepo, actions, overlay = true) }
            else when (state.screen) {
                Screen.HOME -> HomeScreen(
                    state.suggested, state.themedIcons, wide, vm.appRepo,
                    onOpenSearch = openSearch,
                    onOpenSearchWith = vm::openSearch,
                    onOpenSettings = vm::openSettings,
                    onShowNotifications = { if (!vm.launcher.expandNotifications()) vm.toast("Couldn't open notifications on this device") },
                    onLaunch = vm::launchApp,
                    onEditTags = vm::editTags,
                )
                Screen.SEARCH -> SearchScreen(state, vm.appRepo, actions)
                Screen.SETTINGS -> SettingsScreen(
                    state,
                    onBack = vm::goHome,
                    onScheme = vm::setScheme,
                    onThemed = vm::setThemedIcons,
                    onClearRecents = vm::clearRecents,
                    onClearTags = vm::clearAppTags,
                    onEdgeBar = { on ->
                        vm.setEdgeBar(on)
                        // Turning it on without the permission goes straight to the grant screen.
                        if (on && !state.overlayGranted && !vm.launcher.openOverlaySettings()) vm.toast("Couldn't open settings")
                    },
                    onEdgeSide = vm::setEdgeSide,
                    onSplit = vm::setSplitLaunch,
                    onAllowShortcut = { if (!vm.launcher.openAccessibilitySettings()) vm.toast("Couldn't open settings") },
                    onAllowOverlay = { if (!vm.launcher.openOverlaySettings()) vm.toast("Couldn't open settings") },
                    onSetDefaultHome = {
                        val i = vm.launcher.homeRoleIntent()
                        if (i != null) runCatching { homeRole.launch(i) }.onFailure { vm.launcher.openSetting("home") }
                        else if (!vm.launcher.openSetting("home")) vm.toast("Couldn't open settings")
                    },
                )
            }
            if (state.toast.isNotEmpty()) {
                // Sits 92dp above the bottom like the mockup, above the keyboard / nav bar.
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        .padding(bottom = 92.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) { SnackToast(state.toast, Modifier.then(Modifier)) }
            }
            state.editingTags?.let { item ->
                TagsDialog(item, onSave = { vm.saveTags(item, it) }, onCancel = { vm.editTags(null) }, onAppInfo = {
                    if (!vm.launcher.appInfo(Tags.packageOf(item.key))) vm.toast("Couldn't open app info")
                })
            }
            if (state.showFullDict) state.dict?.let { d ->
                AlertDialog(
                    onDismissRequest = { vm.showFullDict(false) },
                    confirmButton = { TextButton(onClick = { vm.showFullDict(false) }) { Text("Close") } },
                    title = { Text(d.word) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            d.senses.forEachIndexed { i, s ->
                                Text("${i + 1}. ${s.pos}", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                                Text(s.def, Modifier.padding(bottom = 12.dp))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TagsDialog(item: com.aktivesolutions.jumpstart.search.Item, onSave: (String) -> Unit, onCancel: () -> Unit, onAppInfo: () -> Unit) {
    // Saveable: the activity is recreated on rotation (no configChanges) while editingTags survives in the VM.
    var text by androidx.compose.runtime.saveable.rememberSaveable(item.key) { androidx.compose.runtime.mutableStateOf(item.tags) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(item.title, maxLines = 2) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                Text(Tags.packageOf(item.key), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("e.g. facebook, social") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onAppInfo) { Text("App info") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
