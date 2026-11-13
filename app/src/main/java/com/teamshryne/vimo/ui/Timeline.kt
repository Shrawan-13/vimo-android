package com.teamshryne.vimo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

/** How close a dragged clip has to be to snap to another one, in pixels. */
private const val SnapPixels = 14f

/** The slowest fling that is still followed through, in pixels per second. */
private const val MinFlingVelocity = 120f

/** The speed below which a fling gives up, in pixels per second. */
private const val StopFlingVelocity = 40f

/** How quickly a fling dies out, in reciprocal seconds. */
private const val FlingFriction = 3.5f

/** How much empty room is left after the last clip, as a share of the view. */
private const val TailShare = 0.25f

/**
 * What a gesture on the timeline is doing.
 *
 * The gesture is decided once, when the finger has moved far enough to be a
 * drag and not a tap. That is what keeps the playhead still while the
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

/** A clip that is being dragged, drawn where the finger has it. */
@Immutable
private data class Drag(
    /** The clip being dragged. */
    val clipId: Int,

    /** Where the clip is while it is dragged. */
    val startNs: Long,

    /** Where the clip ends while it is dragged. */
    val endNs: Long,
)

/**
 * The live state of the timeline.
 *
 * It lives in one object on purpose: the gesture handler is keyed on it, so
 * it is never restarted when the scroll or the zoom changes. A handler that
 * restarts in the middle of a drag drops the drag, which is what made the
 * timeline feel like it was fighting the finger.
 *
 * The window and the metrics live here too, because the handler outlives the
 * composition that made them.
 */
private class TimelineState {

    /** How far the timeline is scrolled, in pixels. */
    var scrollPx by mutableFloatStateOf(0f)

    /** The zoom: pixels per second. */
    var pixelsPerSecond by mutableFloatStateOf(72f)

    /** The clip being dragged, while it is being dragged. */
    var drag by mutableStateOf<Drag?>(null)

    /** Whether a finger is on the timeline. */
    var dragging by mutableStateOf(false)

    /** How wide the timeline is drawn, in pixels. */
    var viewportPx = 0f

    /** The pixel metrics of the timeline. */
    var metrics = TimelineMetrics(0f, 0f, 0f, 0f, 0f)

    /** The fling that is running, if there is one. */
    var fling: Job? = null
}

/**
 * The editing timeline.
 *
 * Everything is drawn on a single canvas rather than composed from views: a
 * project can have hundreds of clips, and the timeline has to stay at 60 fps
 * while the user scrubs. The text is laid out through a measurer with a
 * cache, and only the clips and the ticks that are on screen are drawn.
 *
 * The gestures are:
 *
 * | Gesture | What it does |
 * |---------|--------------|
 * | Drag on a clip | Move it |
 * | Drag the edge of a clip | Trim it |
 * | Drag the ruler | Move the playhead |
 * | Drag anywhere else | Scroll the timeline, with a fling at the end |
 * | Pinch | Zoom the timeline |
 * | Tap a clip | Select it |
 *
 * A drag is shown where the finger is and only told to the core when the
 * finger lifts, so the timeline never waits for the engine to keep up, and
 * one drag is one undo step.
 */
@Composable
fun Timeline(
    project: ProjectModel,
    positionNs: Long,
    selectedClipId: Int?,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    onSelectClip: (Int?) -> Unit = { _ -> },
    onMoveClip: (clipId: Int, newStartNs: Long) -> Unit = { _, _ -> },
    onTrimClip: (clipId: Int, newStartNs: Long, newEndNs: Long) -> Unit = { _, _, _ -> },
    onSeek: (Long) -> Unit = { _ -> },
    onZoom: (Float) -> Unit = { _ -> },
) {
    val state = remember { TimelineState() }
    val scope = rememberCoroutineScope()
    // The measurer keeps the text it has laid out, so a redraw does not lay
    // out the labels again.
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val density = LocalDensity.current

    val currentProject by rememberUpdatedState(project)
    val currentPosition by rememberUpdatedState(positionNs)
    val currentSelected by rememberUpdatedState(selectedClipId)
    val currentOnSelect by rememberUpdatedState(onSelectClip)
    val currentOnMove by rememberUpdatedState(onMoveClip)
    val currentOnTrim by rememberUpdatedState(onTrimClip)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnZoom by rememberUpdatedState(onZoom)

    BoxWithConstraints(modifier = modifier) {
        val metrics = TimelineMetrics(
            headerPx = with(density) { HeaderWidth.toPx() },
            rulerPx = with(density) { RulerHeight.toPx() },
            trackPx = with(density) { TrackHeight.toPx() },
            trimHandlePx = with(density) { TrimHandleWidth.toPx() },
            playheadPx = with(density) { PlayheadHandleWidth.toPx() },
        )
        val viewportPx = with(density) { maxWidth.toPx() }

        // The handler lives longer than this composition, so the numbers it
        // needs are kept in the state.
        LaunchedEffect(viewportPx, metrics) {
            state.viewportPx = viewportPx
            state.metrics = metrics
        }

        // While the project plays, the playhead is kept on screen.
        LaunchedEffect(playing, positionNs) {
            if (!playing || state.dragging) return@LaunchedEffect
            val m = state.metrics
            if (state.viewportPx <= 0f) return@LaunchedEffect
            val x = timeToX(positionNs, state.pixelsPerSecond, state.scrollPx, m)
            val margin = with(density) { 48.dp.toPx() }
            if (x < m.headerPx + margin || x > state.viewportPx - margin) {
                val target = scrollFor(positionNs, state.pixelsPerSecond, state.viewportPx / 2f, m)
                    .coerceIn(0f, maxScrollPx(currentProject, state.pixelsPerSecond, m, state.viewportPx))
                Animatable(state.scrollPx).animateTo(target, tween(300)) {
                    state.scrollPx = value
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state, scope) {
                    awaitEachGesture {
                        // A new touch always wins over a fling.
                        state.fling?.cancel()
                        state.fling = null
                        state.dragging = true

                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = Velocity()
                        var gesture: Gesture? = null
                        var last = down.position
                        var lastDistance = 0f
                        var multiTouch = false
                        var moved = false
                        var edge = Edge.NONE
                        var clipId = 0
                        var trackIndex = 0
                        var clipStart = 0L
                        var clipEnd = 0L

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val m = state.metrics

                            if (pressed.size >= 2) {
                                // Two fingers: zoom around the middle of them.
                                multiTouch = true
                                moved = true
                                val distance =
                                    (pressed[0].position - pressed[1].position).getDistance()
                                val center = (pressed[0].position + pressed[1].position) / 2f
                                if (lastDistance > 1f && distance > 1f) {
                                    val factor = distance / lastDistance
                                    state.pixelsPerSecond = (state.pixelsPerSecond * factor)
                                        .coerceIn(MinPixelsPerSecond, MaxPixelsPerSecond)
                                    // Keep the time under the fingers where it is.
                                    val timeAtCenter = timeAt(
                                        center.x, state.pixelsPerSecond, state.scrollPx, m,
                                    )
                                    state.scrollPx = scrollFor(
                                        timeAtCenter, state.pixelsPerSecond, center.x, m,
                                    ).coerceIn(
                                        0f,
                                        maxScrollPx(
                                            currentProject, state.pixelsPerSecond, m, state.viewportPx,
                                        ),
                                    )
                                    currentOnZoom(state.pixelsPerSecond)
                                }
                                lastDistance = distance
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            if (multiTouch) break

                            val change = pressed.first()
                            tracker.add(change.uptimeMillis, change.position.x)
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
                                    currentProject, down.position,
                                    state.pixelsPerSecond, state.scrollPx, m,
                                )
                                val clip = hit?.clip
                                gesture = when {
                                    hit != null && hit.inRuler -> Gesture.PLAYHEAD
                                    clip == null -> Gesture.SCROLL
                                    hit.edge != Edge.NONE -> {
                                        edge = hit.edge
                                        clipId = clip.id
                                        trackIndex = hit.trackIndex
                                        clipStart = clip.startNs
                                        clipEnd = clip.endNs
                                        Gesture.TRIM_CLIP
                                    }
                                    else -> {
                                        clipId = clip.id
                                        trackIndex = hit.trackIndex
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
                                    state.scrollPx = (state.scrollPx - delta.x)
                                        .coerceIn(
                                            0f,
                                            maxScrollPx(
                                                currentProject, state.pixelsPerSecond, m,
                                                state.viewportPx,
                                            ),
                                        )
                                    change.consume()
                                }
                                Gesture.PLAYHEAD -> {
                                    currentOnSeek(
                                        timeAt(
                                            change.position.x,
                                            state.pixelsPerSecond,
                                            state.scrollPx,
                                            m,
                                        ),
                                    )
                                    change.consume()
                                }
                                Gesture.MOVE_CLIP -> {
                                    // The clip follows the finger on screen;
                                    // the core only hears about it at the end.
                                    val deltaNs =
                                        (delta.x / state.pixelsPerSecond * 1e9).roundToLong()
                                    val duration = clipEnd - clipStart
                                    val wanted = (clipStart + deltaNs).coerceAtLeast(0L)
                                    val snapped = snap(
                                        currentProject, trackIndex, clipId, currentPosition,
                                        wanted, wanted + duration, state,
                                    )
                                    clipStart = snapped.first
                                    clipEnd = snapped.second
                                    state.drag = Drag(clipId, clipStart, clipEnd)
                                    change.consume()
                                }
                                Gesture.TRIM_CLIP -> {
                                    val deltaNs =
                                        (delta.x / state.pixelsPerSecond * 1e9).roundToLong()
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
                                    val snapped = snap(
                                        currentProject, trackIndex, clipId, currentPosition,
                                        newStart, newEnd, state,
                                    )
                                    clipStart = snapped.first
                                    clipEnd = snapped.second
                                    state.drag = Drag(clipId, clipStart, clipEnd)
                                    change.consume()
                                }
                                null -> Unit
                            }
                            last = change.position
                        }

                        state.dragging = false

                        // A fling carries the scroll on after the finger lifts.
                        val velocity = tracker.velocity
                        if (gesture == Gesture.SCROLL && abs(velocity) > MinFlingVelocity) {
                            state.fling = scope.launch {
                                var speed = velocity
                                var lastFrame = 0L
                                while (abs(speed) > StopFlingVelocity) {
                                    val frame = withFrameNanos { it }
                                    if (lastFrame != 0L) {
                                        val seconds =
                                            ((frame - lastFrame) / 1e9f).coerceIn(0f, 1f / 30f)
                                        speed *= exp(-FlingFriction * seconds)
                                        val limit = maxScrollPx(
                                            currentProject, state.pixelsPerSecond,
                                            state.metrics, state.viewportPx,
                                        )
                                        val next = (state.scrollPx - speed * seconds)
                                            .coerceIn(0f, limit)
                                        state.scrollPx = next
                                        if (next == 0f && speed > 0f) break
                                        if (next == limit && speed < 0f) break
                                    }
                                    lastFrame = frame
                                }
                                state.fling = null
                            }
                        }

                        if (!moved) {
                            // A tap: select what was touched.
                            val hit = hitTest(
                                currentProject, down.position,
                                state.pixelsPerSecond, state.scrollPx, state.metrics,
                            )
                            if (hit != null && hit.inRuler) {
                                currentOnSeek(
                                    timeAt(
                                        down.position.x,
                                        state.pixelsPerSecond,
                                        state.scrollPx,
                                        state.metrics,
                                    ),
                                )
                            } else {
                                currentOnSelect(hit?.clip?.id)
                            }
                        } else {
                            // The drag is over: the clip goes where the finger
                            // left it, in one step.
                            val drag = state.drag
                            if (drag != null) {
                                state.drag = null
                                when (gesture) {
                                    Gesture.MOVE_CLIP -> currentOnMove(drag.clipId, drag.startNs)
                                    Gesture.TRIM_CLIP -> currentOnTrim(
                                        drag.clipId,
                                        drag.startNs,
                                        drag.endNs,
                                    )
                                    else -> Unit
                                }
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawTimeline(
                    project = currentProject,
                    positionNs = currentPosition,
                    selectedClipId = currentSelected,
                    drag = state.drag,
                    pixelsPerSecond = state.pixelsPerSecond,
                    scrollPx = state.scrollPx,
                    measurer = measurer,
                    metrics = metrics,
                )
            }
        }
    }
}

/** The pixel metrics of the timeline, computed once per composition. */
@Immutable
private data class TimelineMetrics(
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
    val inRuler: Boolean,
    val trackIndex: Int,
)

/** The clip under a touch, or the ruler. */
private fun hitTest(
    project: ProjectModel,
    offset: Offset,
    pixelsPerSecond: Float,
    scrollPx: Float,
    metrics: TimelineMetrics,
): Hit? {
    // The ruler: a touch there moves the playhead, whether it is on the
    // handle or not. Scrubbing the ruler is the quickest way to look at a
    // moment.
    if (offset.y < metrics.rulerPx) {
        if (offset.x < metrics.headerPx) return null
        return Hit(null, Edge.NONE, true, 0)
    }
    if (offset.x < metrics.headerPx) return null
    val trackIndex = ((offset.y - metrics.rulerPx) / metrics.trackPx).toInt()
    val track = project.tracks.getOrNull(trackIndex) ?: return null
    val timeNs = timeAt(offset.x, pixelsPerSecond, scrollPx, metrics)
    val clip = track.clips.firstOrNull { timeNs >= it.startNs && timeNs < it.endNs }
        ?: return null
    val edge = edgeAt(clip, offset.x, pixelsPerSecond, scrollPx, metrics)
    return Hit(clip, edge, false, trackIndex)
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

/**
 * The place a dragged clip snaps to.
 *
 * Each edge is offered the edges of the other clips on the track and the
 * playhead, and the nearest one within reach wins, so a clip can be lined
 * up with its neighbours without the finger having to be exact.
 */
private fun snap(
    project: ProjectModel,
    trackIndex: Int,
    clipId: Int,
    playheadNs: Long,
    startNs: Long,
    endNs: Long,
    state: TimelineState,
): Pair<Long, Long> {
    val track = project.tracks.getOrNull(trackIndex) ?: return startNs to endNs
    val thresholdNs = (SnapPixels / state.pixelsPerSecond * 1e9).toLong()
    var bestStart = startNs
    var bestStartDelta = Long.MAX_VALUE
    var bestEnd = endNs
    var bestEndDelta = Long.MAX_VALUE

    fun consider(time: Long) {
        val startDelta = abs(time - startNs)
        if (startDelta <= thresholdNs && startDelta < bestStartDelta) {
            bestStart = time
            bestStartDelta = startDelta
        }
        val endDelta = abs(time - endNs)
        if (endDelta <= thresholdNs && endDelta < bestEndDelta) {
            bestEnd = time
            bestEndDelta = endDelta
        }
    }

    for (other in track.clips) {
        if (other.id == clipId) continue
        consider(other.startNs)
        consider(other.endNs)
    }
    consider(playheadNs)

    // The two edges snap together: a clip cannot be pulled inside out.
    if (bestEnd < bestStart) bestEnd = bestStart + 1_000_000L
    return bestStart to bestEnd
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

/** The end of the content: the last clip, or what the project says. */
private fun contentEndNs(project: ProjectModel): Long {
    val lastClip = project.tracks.flatMap { it.clips }.maxOfOrNull { it.endNs } ?: 0L
    return max(project.durationNs, lastClip)
}

/** How far the timeline can be scrolled, in pixels. */
private fun maxScrollPx(
    project: ProjectModel,
    pixelsPerSecond: Float,
    metrics: TimelineMetrics,
    viewportPx: Float,
): Float {
    if (viewportPx <= 0f) return 0f
    val durationPx = (contentEndNs(project) / 1e9f) * pixelsPerSecond
    val visiblePx = (viewportPx - metrics.headerPx).coerceAtLeast(1f)
    return (durationPx + metrics.headerPx - viewportPx + visiblePx * TailShare)
        .coerceAtLeast(0f)
}

/** Draw the whole timeline. */
private fun DrawScope.drawTimeline(
    project: ProjectModel,
    positionNs: Long,
    selectedClipId: Int?,
    drag: Drag?,
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

    drawRuler(
        project = project,
        pixelsPerSecond = pixelsPerSecond,
        scrollPx = scrollPx,
        measurer = measurer,
        metrics = metrics,
    )

    // --- the tracks ------------------------------------------------------
    val assetNames = project.assets.associate { it.id to it.displayName }
    project.tracks.forEachIndexed { index, track ->
        val top = rulerPx + index * trackPx
        if (top > size.height) return@forEachIndexed

        drawRect(
            if (index % 2 == 0) VimoColors.Surface.copy(alpha = 0.45f) else VimoColors.Background,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackPx),
        )

        track.clips.forEach { clip ->
            // While a clip is dragged it is drawn where the finger has it.
            val preview = drag?.takeIf { it.clipId == clip.id }
            val startNs = preview?.startNs ?: clip.startNs
            val endNs = preview?.endNs ?: clip.endNs
            val startX = timeToX(startNs, pixelsPerSecond, scrollPx, metrics)
            val endX = timeToX(endNs, pixelsPerSecond, scrollPx, metrics)
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

            // The name of the media, if there is room for it.
            if (right - left > 48f) {
                val label = assetNames[clip.asset] ?: "Clip ${clip.id + 1}"
                val measured = measurer.measure(
                    label,
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
        // The handle: the part that moves the playhead.
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

/** Draw the ticks and the labels of the ruler. */
private fun DrawScope.drawRuler(
    project: ProjectModel,
    pixelsPerSecond: Float,
    scrollPx: Float,
    measurer: TextMeasurer,
    metrics: TimelineMetrics,
) {
    val step = tickStep(pixelsPerSecond)
    val minor = step / 5
    if (minor <= 0L) return

    // Only the ticks that are on screen are drawn.
    val first = (timeAt(metrics.headerPx, pixelsPerSecond, scrollPx, metrics) / minor)
        .coerceAtLeast(0L)
    val last = timeAt(size.width, pixelsPerSecond, scrollPx, metrics) / minor

    var tick = first
    var lastLabelRight = metrics.headerPx - 8f
    while (tick <= last) {
        val timeNs = tick * minor
        val x = timeToX(timeNs, pixelsPerSecond, scrollPx, metrics)
        tick += 1
        if (x < metrics.headerPx) continue
        if (x > size.width) break

        val major = timeNs % step == 0L
        drawLine(
            VimoColors.Outline,
            Offset(x, if (major) metrics.rulerPx * 0.35f else metrics.rulerPx * 0.65f),
            Offset(x, metrics.rulerPx),
            strokeWidth = 1f,
        )
        if (!major) continue

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

/** The speed of a drag, in pixels per second. */
private class Velocity {

    private var lastTime = 0L
    private var lastX = 0f
    private var value = 0f

    /** Take a sample of the finger's horizontal position. */
    fun add(timeMillis: Long, x: Float) {
        if (lastTime != 0L) {
            val seconds = (timeMillis - lastTime) / 1000f
            if (seconds > 0f) {
                val sample = (x - lastX) / seconds
                value = if (value == 0f) sample else value * 0.6f + sample * 0.4f
            }
        }
        lastTime = timeMillis
        lastX = x
    }

    /** The speed, in pixels per second. Positive is to the right. */
    val velocity: Float get() = value
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
