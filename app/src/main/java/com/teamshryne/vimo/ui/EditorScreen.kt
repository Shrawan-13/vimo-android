package com.teamshryne.vimo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teamshryne.vimo.EditorViewModel
import com.teamshryne.vimo.ui.theme.VimoColors

/**
 * The editor.
 *
 * The layout adapts to the space it is given:
 *
 * * **Wide** (a tablet, or a phone in landscape) — the preview and the
 *   inspector sit side by side above the timeline, so the timeline gets the
 *   full width.
 * * **Narrow** (a phone in portrait) — the preview is on top, the inspector is
 *   below it and the timeline takes the bottom third.
 *
 * The timeline is always the widest element, because that is what the user
 * is working on.
 */
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onSurfaceReady: (android.view.Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = viewModel.project
    val selected = viewModel.selectedClip()
    val aspect = if (project.height > 0) {
        project.width.toFloat() / project.height.toFloat()
    } else {
        16f / 9f
    }

    BoxWithConstraints(modifier.fillMaxSize().background(VimoColors.Background)) {
        val wide = maxWidth >= 720.dp

        Column(Modifier.fillMaxSize()) {
            TopBar(viewModel)

            if (wide) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        PreviewColumn(
                            viewModel = viewModel,
                            aspect = aspect,
                            onSurfaceReady = onSurfaceReady,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                            .background(VimoColors.Surface),
                    ) {
                        Inspector(
                            clip = selected,
                            effects = viewModel.availableEffects,
                            onOpacityChange = viewModel::setClipOpacity,
                            onToggleEnabled = viewModel::toggleClipEnabled,
                            onSplit = viewModel::splitAtPlayhead,
                            onDelete = viewModel::deleteSelected,
                            onAddEffect = viewModel::addEffect,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    PreviewColumn(
                        viewModel = viewModel,
                        aspect = aspect,
                        onSurfaceReady = onSurfaceReady,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // The timeline.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (wide) 260.dp else 240.dp)
                    .background(VimoColors.Surface),
            ) {
                Timeline(
                    project = project,
                    positionNs = viewModel.positionNs,
                    selectedClipId = viewModel.selectedClipId,
                    onSelectClip = viewModel::selectClip,
                    onMoveClip = viewModel::moveClip,
                    onTrimClip = viewModel::trimClip,
                    onSeek = viewModel::seek,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The preview and the transport. */
@Composable
private fun PreviewColumn(
    viewModel: EditorViewModel,
    aspect: Float,
    onSurfaceReady: (android.view.Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PreviewPanel(
            playing = viewModel.playing,
            positionLabel = formatTimecode(viewModel.positionNs, viewModel.project.fps),
            durationLabel = formatDuration(viewModel.project.durationNs),
            aspectRatio = aspect,
            onTogglePlayback = viewModel::togglePlayback,
            onStop = viewModel::stop,
            onStep = viewModel::step,
            onSurfaceReady = onSurfaceReady,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The bar across the top. */
@Composable
private fun TopBar(viewModel: EditorViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(VimoColors.Surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Movie,
            contentDescription = null,
            tint = VimoColors.Accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            viewModel.project.name.ifBlank { "Vimo" },
            style = MaterialTheme.typography.titleMedium,
            color = VimoColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(140.dp),
        )
        if (viewModel.dirty) {
            Text(
                " •",
                style = MaterialTheme.typography.titleMedium,
                color = VimoColors.Accent,
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = viewModel::undo) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = VimoColors.OnSurface,
            )
        }
        IconButton(onClick = viewModel::redo) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = VimoColors.OnSurface,
            )
        }

        Spacer(Modifier.width(8.dp))

        Button(
            onClick = { viewModel.startExport(0) },
            colors = ButtonDefaults.buttonColors(
                containerColor = VimoColors.Accent,
                contentColor = androidx.compose.ui.graphics.Color.Black,
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export", fontWeight = FontWeight.SemiBold)
        }
    }
}
