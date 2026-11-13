package com.teamshryne.vimo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
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
import com.teamshryne.vimo.model.AssetModel
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
    onBack: () -> Unit = {},
    onImport: () -> Unit = {},
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
            TopBar(viewModel, onBack, onImport)

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

            // The media bin: the files that can be put on the timeline.
            if (project.assets.isNotEmpty()) {
                MediaBin(
                    assets = project.assets,
                    onAdd = viewModel::addAssetToTimeline,
                )
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
private fun TopBar(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(VimoColors.Surface)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to the projects",
                tint = VimoColors.OnSurface,
            )
        }
        Text(
            viewModel.project.name.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleMedium,
            color = VimoColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
        if (viewModel.dirty) {
            Text(
                " •",
                style = MaterialTheme.typography.titleMedium,
                color = VimoColors.Accent,
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onImport) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Import media",
                tint = VimoColors.OnSurface,
            )
        }
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

        Spacer(Modifier.width(6.dp))

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

/**
 * The media bin.
 *
 * The files that are in the project. Tapping one puts it on the timeline.
 */
@Composable
private fun MediaBin(
    assets: List<AssetModel>,
    onAdd: (AssetModel) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(VimoColors.Surface)
            .padding(vertical = 6.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(assets, key = { it.id }) { asset ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(VimoColors.SurfaceRaised)
                        .clickable { onAdd(asset) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (asset.kind == "audio") Icons.Filled.AudioFile else Icons.Filled.Movie,
                        contentDescription = null,
                        tint = if (asset.kind == "audio") {
                            VimoColors.AccentAudio
                        } else {
                            VimoColors.AccentVideo
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            asset.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = VimoColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(120.dp),
                        )
                        Text(
                            formatDuration(asset.durationNs),
                            style = MaterialTheme.typography.labelSmall,
                            color = VimoColors.OnSurfaceMuted,
                        )
                    }
                }
            }
        }
    }
}
