package com.teamshryne.vimo

import android.view.Surface

/**
 * The Kotlin face of the Vimo engine.
 *
 * The engine is an opaque handle: commands go in, events come out. The UI
 * never queries the engine directly; when it needs to redraw the timeline it
 * asks for a [snapshot].
 *
 * The class is thread safe: the native handle is serialised by the core.
 */
class VimoEngine(
    width: Int = 1920,
    height: Int = 1080,
    sampleRate: Int = 48_000,
    channels: Int = 2,
) : AutoCloseable {

    private var handle: Long = VimoCore.nativeCreate(width, height, sampleRate, channels)

    /** Whether the engine is still alive. */
    val isAlive: Boolean get() = handle != 0L

    /**
     * Send a command to the engine.
     *
     * @return [VimoStatus.OK] on success.
     */
    fun execute(
        tag: Int,
        a: Long = 0,
        b: Long = 0,
        c: Long = 0,
        text: String? = null,
        floats: FloatArray? = null,
    ): Int {
        val current = handle
        if (current == 0L) return VimoStatus.INVALID_HANDLE
        return VimoCore.nativeExecute(current, tag, a, b, c, text, floats)
    }

    /**
     * Take the next event, if there is one.
     *
     * The events are queued by the core, so this never blocks.
     */
    fun pollEvent(): VimoEvent? {
        val current = handle
        if (current == 0L) return null
        return VimoCore.nativePollEvent(current)
    }

    /**
     * Drain every pending event.
     *
     * @param onEvent called for each event, in order.
     */
    fun drainEvents(onEvent: (VimoEvent) -> Unit) {
        while (true) {
            val event = pollEvent() ?: return
            onEvent(event)
        }
    }

    /** The JSON snapshot of the project. */
    fun snapshot(): String {
        val current = handle
        if (current == 0L) return "{}"
        return VimoCore.nativeSnapshot(current)
    }

    /** The engine statistics. */
    fun stats(): VimoStats {
        val current = handle
        if (current == 0L) return VimoStats.EMPTY
        val values = VimoCore.nativeStats(current)
        return VimoStats(
            framesRendered = values.getOrElse(0) { 0 },
            framesDropped = values.getOrElse(1) { 0 },
            framesHeld = values.getOrElse(2) { 0 },
            cacheHits = values.getOrElse(3) { 0 },
            cacheMisses = values.getOrElse(4) { 0 },
            cacheBytes = values.getOrElse(5) { 0 },
            decodersLive = values.getOrElse(6) { 0 }.toInt(),
        )
    }

    /**
     * Hand the output surface over to the engine.
     *
     * The surface is given over **once**: the engine owns it from then on and
     * renders into it on its own thread.
     */
    fun setSurface(surface: Surface) {
        val current = handle
        if (current == 0L) return
        VimoCore.nativeSetSurface(current, surface)
    }

    /** Start playback. */
    fun play() {
        execute(VimoCommand.PLAY)
    }

    /** Pause playback. */
    fun pause() {
        execute(VimoCommand.PAUSE)
    }

    /** Stop playback and rewind. */
    fun stop() {
        execute(VimoCommand.STOP)
    }

    /** Seek to [positionNanos]. */
    fun seek(positionNanos: Long) {
        execute(VimoCommand.SEEK, a = positionNanos)
    }

    /** Set the playback rate (`1.0` is normal speed, `-1.0` is reverse). */
    fun setRate(rate: Float) {
        execute(VimoCommand.SET_RATE, a = (rate * 1000).toLong())
    }

    /** Undo the last edit. */
    fun undo() {
        execute(VimoCommand.UNDO)
    }

    /** Redo the last undone edit. */
    fun redo() {
        execute(VimoCommand.REDO)
    }

    /** Load a project from [path]. */
    fun loadProject(path: String) {
        execute(VimoCommand.LOAD_PROJECT, text = path)
    }

    /** Save the project to [path]. */
    fun saveProject(path: String) {
        execute(VimoCommand.SAVE_PROJECT, text = path)
    }

    /** Ask the engine to emit a snapshot event. */
    fun requestSnapshot() {
        execute(VimoCommand.REQUEST_SNAPSHOT)
    }

    /** Release the engine. */
    override fun close() {
        val current = handle
        if (current != 0L) {
            handle = 0L
            VimoCore.nativeDestroy(current)
        }
    }
}

/** The engine's statistics. */
data class VimoStats(
    val framesRendered: Long,
    val framesDropped: Long,
    val framesHeld: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val cacheBytes: Long,
    val decodersLive: Int,
) {
    companion object {
        /** The zeroed statistics. */
        val EMPTY = VimoStats(0, 0, 0, 0, 0, 0, 0)
    }
}
