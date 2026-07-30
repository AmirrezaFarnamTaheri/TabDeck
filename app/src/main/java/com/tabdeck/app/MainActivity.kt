package com.tabdeck.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tabdeck.app.ui.TabDeckRoot
import com.tabdeck.app.ui.theme.TabDeckTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TabDeckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.importFromIntent(intent)
        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            TabDeckTheme(settings = state.settings) {
                TabDeckRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.importFromIntent(intent)
    }
}
