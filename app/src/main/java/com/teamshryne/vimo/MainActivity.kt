package com.teamshryne.vimo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teamshryne.vimo.ui.EditorScreen
import com.teamshryne.vimo.ui.theme.VimoTheme
import kotlinx.coroutines.launch

/**
 * The editor activity.
 *
 * The activity owns the [VimoEngine] (through the view model) and the output
 * surface. The engine renders into the surface on its own thread, so the UI
 * thread only hands the surface over once.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VimoTheme {
                val snackbar = remember { SnackbarHostState() }
                val message = viewModel.message

                LaunchedEffect(message) {
                    if (message != null) {
                        snackbar.showSnackbar(message)
                        viewModel.clearMessage()
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    EditorScreen(
                        viewModel = viewModel,
                        onSurfaceReady = { surface -> viewModel.engine.setSurface(surface) },
                    )
                    SnackbarHost(snackbar, Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The thermal status and the memory usage are reported by the platform
        // while the editor is in the foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    viewModel.reportMemory()
                    kotlinx.coroutines.delay(2_000)
                }
            }
        }
    }
}
