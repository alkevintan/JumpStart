package com.aktivesolutions.jumpstart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.aktivesolutions.jumpstart.ui.JumpStartApp

class MainActivity : ComponentActivity() {
    private val vm: JumpStartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh launch, and consumed, so a rotation (recreate with the same intent) doesn't re-open search.
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_SEARCH, false)) {
            intent.removeExtra(EXTRA_OPEN_SEARCH)
            vm.openSearch()
        }
        setContent { JumpStartApp(vm) }
    }

    // singleTask: pressing Home while we're on top delivers the HOME intent here -> back to idle Home.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
        else if (intent.getBooleanExtra(EXTRA_OPEN_SEARCH, false)) {
            intent.removeExtra(EXTRA_OPEN_SEARCH) // Alt+Space shortcut; consumed
            vm.openSearch()
        }
        setIntent(intent)
    }

    companion object {
        const val EXTRA_OPEN_SEARCH = "open_search"
    }
}
