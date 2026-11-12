package com.teamshryne.vimo

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The editor activity.
 *
 * The activity owns the [VimoEngine] and the output [SurfaceView]. The engine
 * renders into the surface on its own thread, so the UI thread only sends
 * commands and drains events.
 */
class MainActivity : ComponentActivity() {

    private lateinit var engine: VimoEngine
    private val running = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engine = VimoEngine(width = 1920, height = 1080)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var status by remember { mutableStateOf("Ready") }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = "Vimo", style = MaterialTheme.typography.headlineMedium)
                        Text(text = status)

                        PreviewSurface(
                            onSurfaceReady = { surface ->
                                engine.setSurface(surface)
                                status = "Surface ready"
                            },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { engine.play() }) { Text("Play") }
                            Button(onClick = { engine.pause() }) { Text("Pause") }
                            Button(onClick = { engine.stop() }) { Text("Stop") }
                            Button(onClick = { engine.undo() }) { Text("Undo") }
                            Button(onClick = { engine.redo() }) { Text("Redo") }
                        }

                        Button(onClick = {
                            engine.requestSnapshot()
                            status = "Snapshot: ${engine.snapshot().length} bytes"
                        }) { Text("Snapshot") }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        running.set(false)
        engine.close()
        super.onDestroy()
    }

    @androidx.compose.runtime.Composable
    private fun PreviewSurface(onSurfaceReady: (android.view.Surface) -> Unit) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            onSurfaceReady(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                    })
                }
            },
        )
    }
}
