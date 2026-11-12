package com.teamshryne.vimo.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.teamshryne.vimo.ui.theme.VimoColors

/**
 * The preview surface and the transport bar.
 *
 * The engine renders into the surface on its own thread, so the UI thread
 * only hands the surface over once and then never touches it again.
 */
@Composable
fun PreviewPanel(
    playing: Boolean,
    positionLabel: String,
    durationLabel: String,
    aspectRatio: Float,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSurfaceReady: (android.view.Surface) -> Unit = {},
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
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

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = { onStep(-1) }) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous frame",
                    tint = VimoColors.OnSurface,
                )
            }
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(VimoColors.Accent),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.Black,
                )
            }
            IconButton(onClick = { onStep(1) }) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next frame",
                    tint = VimoColors.OnSurface,
                )
            }
            IconButton(onClick = onStop) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Stop",
                    tint = VimoColors.OnSurfaceMuted,
                )
            }

            Spacer(Modifier.weight(1f))

            // The timecode: monospaced so that it does not jitter.
            Text(
                text = positionLabel,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = VimoColors.OnSurface,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/ $durationLabel",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = VimoColors.OnSurfaceMuted,
            )
        }
    }
}
