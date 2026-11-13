package com.teamshryne.vimo

/**
 * The raw native entry points.
 *
 * The functions are implemented in `crates/vimo-jni` and are deliberately
 * thin: they convert the arguments, call the core and convert the result
 * back. Everything else lives in [VimoEngine].
 */
internal object VimoCore {
    init {
        System.loadLibrary("vimo_jni")
    }

    /** Create an engine. Returns a handle, or `0` on failure. */
    external fun nativeCreate(width: Int, height: Int, sampleRate: Int, channels: Int): Long

    /** Release an engine. */
    external fun nativeDestroy(handle: Long)

    /**
     * Execute a command.
     *
     * @return a status code: `0` is success, negative values are errors.
     */
    external fun nativeExecute(
        handle: Long,
        tag: Int,
        a: Long,
        b: Long,
        c: Long,
        text: String?,
        floats: FloatArray?,
    ): Int

    /** Poll for one event, or `null` when there is nothing to report. */
    external fun nativePollEvent(handle: Long): VimoEvent?

    /** The JSON snapshot of the project. */
    external fun nativeSnapshot(handle: Long): String

    /** The engine statistics: frames, drops, cache hits and cache size. */
    external fun nativeStats(handle: Long): LongArray

    /**
     * Hand the output surface over to the engine.
     *
     * The renderer is started on its own thread: the GL context belongs to
     * exactly one thread.
     */
    external fun nativeSetSurface(
        handle: Long,
        surface: Any,
        width: Int,
        height: Int,
    ): Boolean

    /**
     * Load the effect manifest and the shader sources.
     *
     * @return the number of interface mismatches (zero is good).
     */
    external fun nativeLoadEffects(
        handle: Long,
        manifest: String,
        shaders: String,
    ): Int

    /**
     * List the projects in a directory.
     *
     * @return a JSON array with one object per project.
     */
    external fun nativeListProjects(dir: String): String

    /**
     * Delete a project.
     *
     * @return `0` on success, a negative status otherwise.
     */
    external fun nativeDeleteProject(path: String): Int
}
