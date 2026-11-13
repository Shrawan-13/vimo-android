package com.teamshryne.vimo

import android.net.Uri
import android.os.Bundle
import android.provider.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.teamshryne.vimo.ui.EditorScreen
import com.teamshryne.vimo.ui.ProjectListScreen
import com.teamshryne.vimo.ui.theme.VimoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        // The app draws behind the system bars and pads itself out of them, so
        // that nothing ever ends up under the status bar.
        enableEdgeToEdge()

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
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding(),
                ) {
                    VimoApp(viewModel, onSurfaceReady = { surface ->
                        viewModel.engine.setSurface(surface)
                    })
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

/**
 * The app: the projects, and the editor.
 *
 * The two screens are kept in one state, so that the app does not need a
 * navigation library for two destinations.
 */
@Composable
private fun VimoApp(
    viewModel: EditorViewModel,
    onSurfaceReady: (android.view.Surface) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImport = uri
        }
    }

    // The file the user picked is copied into the app's own storage.
    val import = pendingImport
    LaunchedEffect(import) {
        if (import == null) return@LaunchedEffect
        pendingImport = null
        viewModel.importMedia(import)
    }

    if (editing) {
        EditorScreen(
            viewModel = viewModel,
            onSurfaceReady = onSurfaceReady,
            onBack = {
                viewModel.saveProject()
                editing = false
                viewModel.refreshProjects()
            },
            onImport = { picker.launch(arrayOf("video/*", "audio/*")) },
        )
    } else {
        LaunchedEffect(Unit) { viewModel.refreshProjects() }
        ProjectListScreen(
            projects = viewModel.projects,
            onOpen = {
                viewModel.openProject(it)
                editing = true
            },
            onCreate = {
                viewModel.createProject(it)
                editing = true
            },
            onDelete = { viewModel.deleteProject(it) },
        )
    }
}
