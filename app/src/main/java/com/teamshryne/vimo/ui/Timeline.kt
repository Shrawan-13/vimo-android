package com.teamshryne.vimo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
private val RulerHeight = 30.dp

/** The height of a track row. */
private val TrackHeight = 64.dp

/** The width of the track header column. */
private val HeaderWidth = 108.dp

/** How close to a clip edge a touch has to be to trim it. */
private val TrimHandleWidth = 20.dp

/** How wide the playhead's handle is. */
private val PlayheadHandleWidth = 28.dp

/** The smallest and the largest zoom, in pixels per second. */
private const val MinPixelsPerSecond = 12f
private const val MaxPixelsPerSecond = 900f

/**
 * What a gesture on the timeline is doing.
 *
 * The gesture is decided once, when the finger has moved far enough to be
 * a drag and not a tap. That is what keeps the playhead still while the
 * timeline is scrolled.
 */
private enum class Gesture {
    /** The timeline is being scrolled. */
    SCROLL,

    /** The playhead is being moved. */
    PLAYHEAD,

    /** A clip is being moved. */
    MOVE_CLIP,

    /** A clip is being trimmed. */
    TRIM_CLIP,
}

/** Which edge of a clip a touch is close to. */
private enum class Edge { START, END, NONE }

/**
 * The editing timeline.
 *
 * Everything is drawn on a single canvas rather than composed from views: a
 * project can have hundreds of clips, and the timeline has to stay at 60 fps
 * while the user scrubs.
 *
 * The gestures are:
 *
 * | Gesture | What it does |
 * |---------|--------------|
 * | Drag on a clip | Move it |
 * | Drag the edge of a clip | Trim it |
 * | Drag the playhead's handle | Move the playhead |
 * | Drag anywhere else | Scroll the timeline |
 * | Pinch | Zoom the timeline |
 * | Tap a clip | Select it |
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
    var pixelsPerSecond by remember { mutableFloatStateOf(72f) }
    // The horizontal scroll, in pixels.
    var scrollPx by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val metrics = TimelineMetrics(
            headerPx = with(density) { HeaderWidth.toPx() },
            rulerPx = with(density) { RulerHeight.toPx() },
            trackPx = with(density) { TrackHeight.toPx() },
            trimHandlePx = with(density) { TrimHandleWidth.toPx() },
            playheadPx = with(density) { PlayheadHandleWidth.toPx() },
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(project, pixelsPerSecond, scrollPx, selectedClipId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var gesture: Gesture? = null
                        var last = down.position
                        var lastDistance = 0f
                        var edge = Edge.NONE
                        var clipId = 0
                        var clipStart = 0L
                        var clipEnd = 0L
                        var moved = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                // Two fingers: zoom around the middle of them.
                                val distance =
                                    (pressed[0].position - pressed[1].position).getDistance()
                                val center = (pressed[0].position + pressed[1].position) / 2f
                                if (lastDistance > 1f && distance > 1f) {
                                    val factor = distance / lastDistance
                                    val target = (pixelsPerSecond * factor)
                                        .coerceIn(MinPixelsPerSecond, MaxPixelsPerSecond)
                                    // Keep the time under the fingers where it is.
                                    val timeAtCenter = timeAt(center.x, pixelsPerSecond, scrollPx, metrics)
                                    pixelsPerSecond = target
                                    scrollPx = scrollFor(
                                        timeAtCenter, target, center.x, metrics,
                                    )
                                    onZoom(target)
                                }
                                lastDistance = distance
                                gesture = Gesture.SCROLL
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val change = pressed.first()
                            val delta = change.position - last
                            if (gesture == null) {
                                // Wait for the touch slop before deciding what
                                // the gesture means.
                                if (delta.getDistance() < viewConfiguration.touchSlop) {
                                    last = change.position
                                    continue
                                }
                                // The touch slop has been crossed: decide what
                                // the gesture means, from where it started.
                                val hit = hitTest(
                                    project, positionNs, down.position,
                                    pixelsPerSecond, scrollPx, metrics,
                                )
                                val clip = hit?.clip
                                gesture = when {
                                    hit == null -> Gesture.SCROLL
                                    hit.isPlayhead -> Gesture.PLAYHEAD
                                    clip == null -> Gesture.SCROLL
                                    hit.edge != Edge.NONE -> {
                                        edge = hit.edge
                                        clipId = clip.id
                                        clipStart = clip.startNs
                                        clipEnd = clip.endNs
                                        Gesture.TRIM_CLIP
                                    }
                                    else -> {
                                        clipId = clip.id
                                        clipStart = clip.startNs
                                        clipEnd = clip.endNs
                                        Gesture.MOVE_CLIP
                                    }
                                }
                                last = change.position
                                change.consume()
                                continue
                            }

                            moved = true
                            when (gesture) {
                                Gesture.SCROLL -> {
                                    scrollPx = (scrollPx - delta.x).coerceAtLeast(0f)
                                    change.consume()
                                }
                                Gesture.PLAYHEAD -> {
                                    onSeek(timeAt(change.position.x, pixelsPerSecond, scrollPx, metrics))
                                    change.consume()
                                }
                                Gesture.MOVE_CLIP -> {
                                    val deltaNs =
                                        (delta.x / pixelsPerSecond * 1e9).roundToLong()
                                    val start = (clipStart + deltaNs).coerceAtLeast(0L)
                                    clipStart = start
                                    onMoveClip(clipId, start)
                                    change.consume()
                                }
                                Gesture.TRIM_CLIP -> {
                                    val deltaNs =
                                        (delta.x / pixelsPerSecond * 1e9).roundToLong()
                                    val newStart = if (edge == Edge.START) {
                                        (clipStart + deltaNs).coerceAtLeast(0L)
                                    } else {
                                        clipStart
                                    }
                                    val newEnd = if (edge == Edge.END) {
                                        max(clipEnd + deltaNs, newStart + 1_000_000L)
                                    } else {
                                        clipEnd
                                    }
                                    clipStart = newStart
                                    clipEnd = newEnd
                                    onTrimClip(clipId, newStart, newEnd)
                                    change.consume()
                                }
                                null -> Unit
                            }
                            last = change.position
                        }

                        if (!moved) {
                            // A tap: select what was touched.
                            val hit = hitTest(
                                project, positionNs, down.position,
                                pixelsPerSecond, scrollPx, metrics,
                            )
                            onSelectClip(hit?.clip?.id)
                            if (hit != null && hit.isPlayhead) {
                                onSeek(timeAt(down.position.x, pixelsPerSecond, scrollPx, metrics))
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawTimeline(
                    project = project,
                    positionNs = positionNs,
                    selectedClipId = selectedClipId,
                    pixelsPerSecond = pixelsPerSecond,
                    scrollPx = scrollPx,
                    measurer = measurer,
                    metrics = metrics,
                )
            }
        }
    }
}

/** The pixel metrics of the timeline, computed once per composition. */
private class TimelineMetrics(
    val headerPx: Float,
    val rulerPx: Float,
    val trackPx: Float,
    val trimHandlePx: Float,
    val playheadPx: Float,
)

/** What is under a touch. */
private class Hit(
    val clip: ClipModel?,
    val edge: Edge,
    val isPlayhead: Boolean,
)

/** The clip under a touch, or the playhead's handle. */
private fun hitTest(
    project: ProjectModel,
    positionNs: Long,
    offset: Offset,
    pixelsPerSecond: Float,
    scrollPx: Float,
    metrics: TimelineMetrics,
): Hit? {
    // The playhead's handle sits in the ruler.
    if (offset.y < metrics.rulerPx) {
        val playheadX = timeToX(positionNs, pixelsPerSecond, scrollPx, metrics)
        val handle = metrics.playheadPx / 2f
        if (abs(offset.x - playheadX) <= handle) {
            return Hit(null, Edge.NONE, true)
        }
        return null
    }
    if (offset.x < metrics.headerPx) return null
    val trackIndex = ((offset.y - metrics.rulerPx) / metrics.trackPx).toInt()
    val track = project.tracks.getOrNull(trackIndex) ?: return null
    val timeNs = timeAt(offset.x, pixelsPerSecond, scrollPx, metrics)
    val clip = track.clips.firstOrNull { timeNs >= it.startNs && timeNs < it.endNs }
        ?: return null
    val edge = edgeAt(clip, offset.x, pixelsPerSecond, scrollPx, metrics)
    return Hit(clip, edge, false)
}

/** Which edge of a clip a touch is close to. */
private fun edgeAt(
    clip: ClipModel,
    x: Float,
    pixelsPerSecond: Float,
    scrollPx: Float,
    metrics: TimelineMetrics,
): Edge {
    val startX = timeToX(clip.startNs, pixelsPerSecond, scrollPx, metrics)
    val endX = timeToX(clip.endNs, pixelsPerSecond, scrollPx, metrics)
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
    scrollPx: Float,
    metrics: TimelineMetrics,
): Long {
    val seconds = (x - metrics.headerPx + scrollPx) / pixelsPerSecond
    return (seconds * 1e9).roundToLong()
}

/** The horizontal position of a time. */
private fun timeToX(
    timeNs: Long,
    pixelsPerSecond: Float,
    scrollPx: Float,
    metrics: TimelineMetrics,
): Float {
    val seconds = (timeNs / 1e9f) - (scrollPx / pixelsPerSecond)
    return metrics.headerPx + seconds * pixelsPerSecond - scrollPx
}

/** The scroll that keeps a time under a position. */
private fun scrollFor(
    timeNs: Long,
    pixelsPerSecond: Float,
    x: Float,
    metrics: TimelineMetrics,
): Float {
    val seconds = timeNs / 1e9f
    return (seconds * pixelsPerSecond - (x - metrics.headerPx)).coerceAtLeast(0f)
}

/** Draw the whole timeline. */
private fun DrawScope.drawTimeline(
    project: ProjectModel,
    positionNs: Long,
    selectedClipId: Int?,
    pixelsPerSecond: Float,
    scrollPx: Float,
    measurer: TextMeasurer,
    metrics: TimelineMetrics,
) {
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
    val firstTick = ((scrollPx / pixelsPerSecond) * 1e9 / step).toLong().coerceAtLeast(0L)
    var tick = firstTick
    // The ruler is bounded, so that a strange zoom can never make it spin.
    val lastTick = firstTick + 2_000
    var lastLabelRight = headerPx - 8f
    while (tick <= lastTick) {
        val timeNs = tick * step
        val x = timeToX(timeNs, pixelsPerSecond, scrollPx, metrics)
        if (x > size.width) break
        if (x >= headerPx) {
            drawLine(
                VimoColors.Outline,
                Offset(x, rulerPx - 9f),
                Offset(x, rulerPx),
                strokeWidth = 1f,
            )
            // The labels never overlap: a label is only drawn when there is
            // room for it.
            val label = formatTimecode(timeNs, project.fps)
            val measured = measurer.measure(
                label,
                TextStyle(fontSize = 10.sp, color = VimoColors.OnSurfaceMuted),
            )
            if (measured.size.width + 12f < x - lastLabelRight) {
                drawText(
                    measured,
                    topLeft = Offset(x + 4f, with(density) { 4.dp.toPx() }),
                )
                lastLabelRight = x + measured.size.width
            }
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

        track.clips.forEach { clip ->
            val startX = timeToX(clip.startNs, pixelsPerSecond, scrollPx, metrics)
            val endX = timeToX(clip.endNs, pixelsPerSecond, scrollPx, metrics)
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
        drawText(
            name,
            topLeft = Offset(with(density) { 12.dp.toPx() }, top + with(density) { 12.dp.toPx() }),
        )
        val kind = measurer.measure(
            if (track.kind == TrackKind.AUDIO) "Audio" else "Video",
            TextStyle(fontSize = 10.sp, color = VimoColors.OnSurfaceMuted),
        )
        drawText(
            kind,
            topLeft = Offset(with(density) { 12.dp.toPx() }, top + with(density) { 30.dp.toPx() }),
        )
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
    val playheadX = timeToX(positionNs, pixelsPerSecond, scrollPx, metrics)
    if (playheadX in headerPx..size.width) {
        drawLine(
            VimoColors.Accent,
            Offset(playheadX, rulerPx),
            Offset(playheadX, size.height),
            strokeWidth = with(density) { 2.dp.toPx() },
        )
        // The handle: the only part that moves the playhead.
        val half = with(density) { PlayheadHandleWidth.toPx() } / 2f
        val head = Path().apply {
            moveTo(playheadX - half, 0f)
            lineTo(playheadX + half, 0f)
            lineTo(playheadX + half, rulerPx * 0.55f)
            lineTo(playheadX, rulerPx * 0.8f)
            lineTo(playheadX - half, rulerPx * 0.55f)
            close()
        }
        drawPath(head, VimoColors.Accent)
    }
}

/**
 * The tick step of the ruler.
 *
 * The step is chosen so that the labels never collide: the smallest step
 * whose labels are at least 64 pixels apart wins.
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
