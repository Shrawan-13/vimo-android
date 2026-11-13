package com.teamshryne.vimo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamshryne.vimo.model.ClipModel
import com.teamshryne.vimo.model.ProjectModel
import com.teamshryne.vimo.model.TrackKind
import com.teamshryne.vimo.ui.theme.VimoColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** The height of the ruler. */
private val RulerHeight = 28.dp

/** The height of a track row. */
private val TrackHeight = 62.dp

/** The width of the track header column. */
private val HeaderWidth = 104.dp

/** How close to a clip edge a touch has to be to trim it. */
private val TrimHandleWidth = 18.dp

/**
 * What the timeline is currently doing.
 *
 * The gesture state lives here rather than in the view model so that a drag
 * never round-trips through the native engine: the clip follows the finger and
 * the engine is only told where it landed.
 */
private sealed interface Drag {
    /** Nothing is being dragged. */
    data object Idle : Drag

    /** A clip is being moved to a new start time. */
    data class Moving(val clipId: Int, val trackIndex: Int, val startNs: Long) : Drag

    /** A clip is being trimmed at one of its edges. */
    data class Trimming(
        val clipId: Int,
        val trackIndex: Int,
        val edge: Edge,
        val startNs: Long,
        val endNs: Long,
    ) : Drag
}

/**
 * The editing timeline.
 *
 * Everything is drawn on a single canvas rather than composed from views: a
 * project can have hundreds of clips, and the timeline has to stay at 60 fps
 * while the user scrubs.
 */
@Composable
fun Timeline(
    project: ProjectModel,
    positionNs: Long,
    selectedClipId: Int?,
    modifier: Modifier = Modifier,
    onSelectClip: (Int?) -> Unit = { _ -> },
    onMoveClip: (clipId: Int, newStartNs: Long) -> Unit = { _, _ -> },
    onTrimClip: (clipId: Int, newStartNs: Long, newEndNs: Long) -> Unit = { _, _, _ -> },
    onSeek: (Long) -> Unit = { _ -> },
    onZoom: (Float) -> Unit = { _ -> },
) {
    var pixelsPerSecond by remember { mutableFloatStateOf(80f) }
    var scrollNs by remember { mutableStateOf(0L) }
    var drag by remember { mutableStateOf<Drag>(Drag.Idle) }
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val headerPx = with(density) { HeaderWidth.toPx() }
        val rulerPx = with(density) { RulerHeight.toPx() }
        val trackPx = with(density) { TrackHeight.toPx() }
        val metrics = TimelineMetrics(
            headerPx = headerPx,
            rulerPx = rulerPx,
            trackPx = trackPx,
            trimHandlePx = with(density) { TrimHandleWidth.toPx() },
        )

        // Pinch to zoom, two fingers to scroll.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) {
                            pixelsPerSecond = (pixelsPerSecond * zoom).coerceIn(4f, 2000f)
                            onZoom(pixelsPerSecond)
                        }
                        if (pan.x != 0f) {
                            val deltaNs = (pan.x / pixelsPerSecond * 1e9).roundToLong()
                            scrollNs = (scrollNs - deltaNs).coerceAtLeast(0L)
                        }
                    }
                }
                .pointerInput(project, pixelsPerSecond, scrollNs) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (offset.x < headerPx) return@detectTapGestures
                            val hit = hitTest(project, offset, pixelsPerSecond, scrollNs, metrics)
                            onSelectClip(hit)
                        },
                        onLongPress = { offset ->
                            if (offset.x < headerPx) return@detectTapGestures
                            onSelectClip(hitTest(project, offset, pixelsPerSecond, scrollNs, metrics))
                        },
                    )
                }
                .pointerInput(project, pixelsPerSecond, scrollNs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val clip = if (offset.x < headerPx) {
                                null
                            } else {
                                clipAt(project, offset, pixelsPerSecond, scrollNs, metrics)
                            }
                            if (clip == null) {
                                // Dragging the ruler (or empty space) scrubs.
                                drag = Drag.Idle
                                onSeek(timeAt(offset.x, pixelsPerSecond, scrollNs, metrics))
                            } else {
                                val (trackIndex, model) = clip
                                val edge =
                                    edgeAt(model, offset.x, pixelsPerSecond, scrollNs, metrics)
                                drag = when (edge) {
                                    Edge.START, Edge.END -> Drag.Trimming(
                                        model.id, trackIndex, edge,
                                        model.startNs, model.endNs,
                                    )
                                    Edge.NONE ->
                                        Drag.Moving(model.id, trackIndex, model.startNs)
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            when (val current = drag) {
                                Drag.Idle -> {
                                    // Scrubbing the ruler.
                                    onSeek(timeAt(change.position.x, pixelsPerSecond, scrollNs, metrics))
                                }
                                is Drag.Moving -> {
                                    val deltaNs = (dragAmount.x / pixelsPerSecond * 1e9).roundToLong()
                                    val start = (current.startNs + deltaNs).coerceAtLeast(0L)
                                    drag = current.copy(startNs = start)
                                    onMoveClip(current.clipId, start)
                                }
                                is Drag.Trimming -> {
                                    val deltaNs = (dragAmount.x / pixelsPerSecond * 1e9).roundToLong()
                                    val newStart = if (current.edge == Edge.START) {
                                        (current.startNs + deltaNs).coerceAtLeast(0L)
                                    } else {
                                        current.startNs
                                    }
                                    val newEnd = if (current.edge == Edge.END) {
                                        max(current.endNs + deltaNs, newStart + 1_000_000L)
                                    } else {
                                        current.endNs
                                    }
                                    drag = current.copy(startNs = newStart, endNs = newEnd)
                                    onTrimClip(current.clipId, newStart, newEnd)
                                }
                            }
                        },
                        onDragEnd = { drag = Drag.Idle },
                        onDragCancel = { drag = Drag.Idle },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawTimeline(
                    project = project,
                    positionNs = positionNs,
                    selectedClipId = selectedClipId,
                    pixelsPerSecond = pixelsPerSecond,
                    scrollNs = scrollNs,
                    drag = drag,
                    measurer = measurer,
                    metrics = metrics,
                )
            }
        }
    }
}

/** Which edge of a clip a touch is close to. */
private enum class Edge { START, END, NONE }

/** The pixel metrics of the timeline, computed once per composition. */
private class TimelineMetrics(
    val headerPx: Float,
    val rulerPx: Float,
    val trackPx: Float,
    val trimHandlePx: Float,
)

/** The clip under a touch, with the index of its track. */
private fun clipAt(
    project: ProjectModel,
    offset: Offset,
    pixelsPerSecond: Float,
    scrollNs: Long,
    metrics: TimelineMetrics,
): Pair<Int, ClipModel>? {
    if (offset.x < metrics.headerPx || offset.y < metrics.rulerPx) return null
    val trackIndex = ((offset.y - metrics.rulerPx) / metrics.trackPx).toInt()
    val track = project.tracks.getOrNull(trackIndex) ?: return null
    val timeNs = timeAt(offset.x, pixelsPerSecond, scrollNs, metrics)
    val clip = track.clips.firstOrNull { timeNs >= it.startNs && timeNs < it.endNs }
    return clip?.let { trackIndex to it }
}

/** The clip under a touch, without the track index. */
private fun hitTest(
    project: ProjectModel,
    offset: Offset,
    pixelsPerSecond: Float,
    scrollNs: Long,
    metrics: TimelineMetrics,
): Int? = clipAt(project, offset, pixelsPerSecond, scrollNs, metrics)?.second?.id

/** Which edge of a clip a touch is close to. */
private fun edgeAt(
    clip: ClipModel,
    x: Float,
    pixelsPerSecond: Float,
    scrollNs: Long,
    metrics: TimelineMetrics,
): Edge {
    val startX = timeToX(clip.startNs, pixelsPerSecond, scrollNs, metrics)
    val endX = timeToX(clip.endNs, pixelsPerSecond, scrollNs, metrics)
    return when {
        abs(x - startX) <= metrics.trimHandlePx -> Edge.START
        abs(x - endX) <= metrics.trimHandlePx -> Edge.END
        else -> Edge.NONE
    }
}

/** The time at a horizontal position. */
private fun timeAt(
    x: Float,
    pixelsPerSecond: Float,
    scrollNs: Long,
    metrics: TimelineMetrics,
): Long {
    val seconds = (x - metrics.headerPx) / pixelsPerSecond
    return scrollNs + (seconds * 1e9).roundToLong()
}

/** The horizontal position of a time. */
private fun timeToX(
    timeNs: Long,
    pixelsPerSecond: Float,
    scrollNs: Long,
    metrics: TimelineMetrics,
): Float {
    val seconds = (timeNs - scrollNs) / 1e9f
    return metrics.headerPx + seconds * pixelsPerSecond
}

/** Draw the whole timeline. */
private fun DrawScope.drawTimeline(
    project: ProjectModel,
    positionNs: Long,
    selectedClipId: Int?,
    pixelsPerSecond: Float,
    scrollNs: Long,
    drag: Drag,
    measurer: TextMeasurer,
    metrics: TimelineMetrics,
) {
    // A DrawScope is a Density, so the conversions below use its own density.
    val headerPx = metrics.headerPx
    val rulerPx = metrics.rulerPx
    val trackPx = metrics.trackPx

    drawRect(VimoColors.Background, size = size)

    // --- the ruler -------------------------------------------------------
    drawRect(VimoColors.Surface, size = Size(size.width, rulerPx))
    drawLine(
        VimoColors.Outline,
        Offset(0f, rulerPx),
        Offset(size.width, rulerPx),
        strokeWidth = 1f,
    )

    val step = tickStep(pixelsPerSecond)
    val firstTick = (scrollNs / step).coerceAtLeast(0L)
    var tick = firstTick
    // The ruler is bounded, so that a strange zoom can never make it spin.
    val lastTick = firstTick + 1_000
    while (tick <= lastTick) {
        val timeNs = tick * step
        val x = timeToX(timeNs, pixelsPerSecond, scrollNs, metrics)
        if (x > size.width) break
        if (x >= headerPx) {
            drawLine(
                VimoColors.Outline,
                Offset(x, rulerPx - 10f),
                Offset(x, rulerPx),
                strokeWidth = 1f,
            )
            val label = formatTimecode(timeNs, project.fps)
            val measured = measurer.measure(
                label,
                TextStyle(fontSize = 10.sp, color = VimoColors.OnSurfaceMuted),
            )
            drawText(measured, topLeft = Offset(x + 4f, with(density) { 4.dp.toPx() }))
        }
        tick += 1
    }

    // --- the tracks ------------------------------------------------------
    project.tracks.forEachIndexed { index, track ->
        val top = rulerPx + index * trackPx
        if (top > size.height) return@forEachIndexed

        drawRect(
            if (index % 2 == 0) VimoColors.Surface.copy(alpha = 0.45f) else VimoColors.Background,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackPx),
        )

        // The clips.
        track.clips.forEach { clip ->
            val startX = timeToX(clip.startNs, pixelsPerSecond, scrollNs, metrics)
            val endX = timeToX(clip.endNs, pixelsPerSecond, scrollNs, metrics)
            if (endX < headerPx || startX > size.width) return@forEach

            val left = max(startX, headerPx)
            val right = max(endX, left + 1f)
            val clipTop = top + 6f
            val clipBottom = top + trackPx - 6f
            val radius = with(density) { 6.dp.toPx() }

            val base = if (track.kind == TrackKind.AUDIO) {
                VimoColors.AccentAudio
            } else {
                VimoColors.ClipPalette[index % VimoColors.ClipPalette.size]
            }
            val fill = if (clip.enabled) base else base.copy(alpha = 0.35f)

            drawRoundRect(
                color = fill,
                topLeft = Offset(left, clipTop),
                size = Size(right - left, clipBottom - clipTop),
                cornerRadius = CornerRadius(radius, radius),
            )

            // A darker strip at the top of the clip, like a film leader.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(left, clipTop),
                size = Size(right - left, (clipBottom - clipTop) * 0.28f),
                cornerRadius = CornerRadius(radius, radius),
            )

            if (clip.id == selectedClipId) {
                drawRoundRect(
                    color = VimoColors.Accent,
                    topLeft = Offset(left, clipTop),
                    size = Size(right - left, clipBottom - clipTop),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = with(density) { 2.dp.toPx() }),
                )
            }

            // The label, if there is room for it.
            val labelWidth = right - left
            if (labelWidth > 48f) {
                val measured = measurer.measure(
                    "Clip ${clip.id + 1}",
                    TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium),
                )
                drawText(
                    measured,
                    topLeft = Offset(
                        left + with(density) { 8.dp.toPx() },
                        clipTop + with(density) { 5.dp.toPx() },
                    ),
                )
            }

            // The trim handles.
            if (clip.id == selectedClipId) {
                drawRect(
                    color = VimoColors.Accent.copy(alpha = 0.9f),
                    topLeft = Offset(left, clipTop),
                    size = Size(with(density) { 3.dp.toPx() }, clipBottom - clipTop),
                )
                drawRect(
                    color = VimoColors.Accent.copy(alpha = 0.9f),
                    topLeft = Offset(right - with(density) { 3.dp.toPx() }, clipTop),
                    size = Size(with(density) { 3.dp.toPx() }, clipBottom - clipTop),
                )
            }
        }

        // The track header.
        drawRect(
            VimoColors.Surface,
            topLeft = Offset(0f, top),
            size = Size(headerPx, trackPx),
        )
        drawLine(
            VimoColors.Outline,
            Offset(0f, top + trackPx),
            Offset(headerPx, top + trackPx),
            strokeWidth = 1f,
        )
        val name = measurer.measure(
            track.name,
            TextStyle(fontSize = 12.sp, color = VimoColors.OnSurface, fontWeight = FontWeight.Medium),
        )
        drawText(name, topLeft = Offset(with(density) { 12.dp.toPx() }, top + with(density) { 10.dp.toPx() }))
        val kind = measurer.measure(
            if (track.kind == TrackKind.AUDIO) "Audio" else "Video",
            TextStyle(fontSize = 10.sp, color = VimoColors.OnSurfaceMuted),
        )
        drawText(kind, topLeft = Offset(with(density) { 12.dp.toPx() }, top + with(density) { 28.dp.toPx() }))
    }

    // The header column, on top of everything.
    drawRect(
        VimoColors.Surface,
        topLeft = Offset(0f, rulerPx),
        size = Size(headerPx, size.height - rulerPx),
    )
    drawLine(
        VimoColors.Outline,
        Offset(headerPx, 0f),
        Offset(headerPx, size.height),
        strokeWidth = 1f,
    )

    // --- the playhead ----------------------------------------------------
    val playheadX = timeToX(positionNs, pixelsPerSecond, scrollNs, metrics)
    if (playheadX in headerPx..size.width) {
        drawLine(
            VimoColors.Accent,
            Offset(playheadX, 0f),
            Offset(playheadX, size.height),
            strokeWidth = with(density) { 2.dp.toPx() },
        )
        // The head of the playhead.
        val head = Path().apply {
            moveTo(playheadX - with(density) { 7.dp.toPx() }, 0f)
            lineTo(playheadX + with(density) { 7.dp.toPx() }, 0f)
            lineTo(playheadX, with(density) { 12.dp.toPx() })
            close()
        }
        drawPath(head, VimoColors.Accent)
    }
}

/**
 * The tick step of the ruler.
 *
 * The step is chosen so that the labels never collide: the smallest step whose
 * labels are at least 64 pixels apart wins.
 */
private fun tickStep(pixelsPerSecond: Float): Long {
    // The steps are in nanoseconds, the unit the project uses.
    val second = 1_000_000_000L
    val steps = longArrayOf(
        second / 60, second / 30, second / 10, second / 4, second / 2,
        second, 2 * second, 5 * second, 10 * second, 15 * second,
        30 * second, 60 * second, 300 * second, 600 * second, 1800 * second,
    )
    for (step in steps) {
        if (step * pixelsPerSecond / second >= 64f) return step
    }
    return steps.last()
}

/** Format a time as `mm:ss:ff`. */
fun formatTimecode(timeNs: Long, fps: Float): String {
    val totalSeconds = timeNs / 1_000_000_000.0
    val hours = (totalSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    val frames = ((totalSeconds % 1) * fps).toInt()
    return if (hours > 0) {
        "%d:%02d:%02d:%02d".format(hours, minutes, seconds, frames)
    } else {
        "%02d:%02d:%02d".format(minutes, seconds, frames)
    }
}

/** Format a duration as `m:ss`. */
fun formatDuration(timeNs: Long): String {
    val totalSeconds = (timeNs / 1_000_000_000.0).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
