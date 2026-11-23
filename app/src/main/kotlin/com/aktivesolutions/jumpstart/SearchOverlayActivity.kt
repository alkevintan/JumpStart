package com.aktivesolutions.jumpstart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aktivesolutions.jumpstart.data.splitScreenSupported
import com.aktivesolutions.jumpstart.edge.ShortcutAccessibilityService
import com.aktivesolutions.jumpstart.edge.splitMode
import com.aktivesolutions.jumpstart.ui.JumpStartApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Search shown on top of whatever app the user is in (opened by the edge-swipe bar). Lives in its own task (see the
 * manifest) so finishing it reveals the app underneath instead of the launcher.
 */
class SearchOverlayActivity : ComponentActivity() {
    private val vm: JumpStartViewModel by viewModels()

    // True while voice recognition / the contacts permission dialog is up: they stop us, but we must survive them.
    private var externalFlow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.fromOverlay = true
        // Launch from this activity (not the Application) so launches have a source activity (split-screen placement).
        vm.useActivityContext(this)
        vm.splitDecider = {
            splitMode(Core.prefs.splitLaunch, splitScreenSupported(), isInMultiWindowMode, ShortcutAccessibilityService.isConnected())
        }
        vm.openSearch()
        setContent { JumpStartApp(vm, overlay = true, onExternalFlow = { externalFlow = it }) }
        // Close button / back / Esc all call vm.goHome(); in overlay mode that means "dismiss".
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.map { it.screen }.distinctUntilChanged().collect { if (it == Screen.HOME) finish() } }
                // A successful launch from here (app/call/web/...): split or translucent targets never stop us, so close now.
                launch {
                    vm.dismiss.collect {
                        finish()
                        // No fade: the previous app must be the top task again immediately (split hand-off).
                        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
                    }
                }
            }
        }
    }

    // singleTask: swiped again while open. Nothing to do beyond making sure we're still on the search screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.openSearch()
    }

    // A finished external flow (voice / permission dialog) is over once we are back in front.
    override fun onResume() {
        super.onResume()
        externalFlow = false
    }

    override fun onDestroy() {
        vm.splitDecider = null
        vm.useActivityContext(null) // don't let the surviving VM hold this Activity
        super.onDestroy()
    }

    // Another app came on top (user launched something from the results) or the screen turned off: the overlay has done
    // its job, don't leave a stale search task around. Rotation also stops us, hence the isChangingConfigurations check.
    override fun onStop() {
        super.onStop()
        if (!externalFlow && !isChangingConfigurations) finish()
    }
}
