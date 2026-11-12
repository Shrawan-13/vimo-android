package com.teamshryne.vimo

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.vimo.model.ClipModel
import com.teamshryne.vimo.model.ProjectModel
import com.teamshryne.vimo.model.SnapshotParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** An effect the app can add to a clip. */
data class EffectDescriptor(
    val id: String,
    val name: String,
    val category: String,
    val index: Int,
    val params: List<EffectParamDescriptor>,
)

/** One parameter of an effect. */
data class EffectParamDescriptor(
    val name: String,
    val label: String,
    val type: String,
    val default: Double,
    val min: Double,
    val max: Double,
)

/**
 * The editor's state.
 *
 * The view model owns the engine and mirrors its state into Compose. The
 * engine stays the source of truth: every action is a command, and the view
 * model re-reads the snapshot afterwards.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    /** The engine. */
    val engine: VimoEngine = VimoEngine(
        width = 1920,
        height = 1080,
        sampleRate = 48_000,
        channels = 2,
    )

    /** The parsed project. */
    var project by mutableStateOf(ProjectModel.EMPTY)
        private set

    /** The playhead position, in nanoseconds. */
    var positionNs by mutableStateOf(0L)
        private set

    /** Whether playback is running. */
    var playing by mutableStateOf(false)
        private set

    /** The selected clip. */
    var selectedClipId by mutableStateOf<Int?>(null)
        private set

    /** The effects that can be added to a clip. */
    var availableEffects by mutableStateOf<List<EffectDescriptor>>(emptyList())
        private set

    /** A transient message for the user. */
    var message by mutableStateOf<String?>(null)
        private set

    /** The last error, if any. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Whether the project has unsaved changes. */
    var dirty by mutableStateOf(false)
        private set

    private var pollJob: Job? = null

    init {
        loadEffects()
        refresh()
        startEventLoop()
    }

    /**
     * Load the effect manifest and the shaders from the app's assets.
     *
     * The manifest says which effects exist; the shaders implement them. The
     * core pairs them and checks that the two agree.
     */
    private fun loadEffects() {
        viewModelScope.launch(Dispatchers.IO) {
            val assets = getApplication<Application>().assets
            val manifestJson = runCatching {
                assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
            }.getOrElse {
                Log.w(TAG, "no effect manifest at $MANIFEST_PATH: ${it.message}")
                return@launch
            }

            // The manifest lists the shaders, so the paths come from there.
            val shaderPaths = runCatching {
                val root = JSONObject(manifestJson)
                val effects = root.optJSONArray("effects") ?: return@runCatching emptyList()
                (0 until effects.length()).mapNotNull { index ->
                    effects.optJSONObject(index)?.optString("shader")?.takeIf { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())

            val sources = LinkedHashMap<String, String>()
            for (path in shaderPaths) {
                runCatching { assets.open(path).bufferedReader().use { it.readText() } }
                    .onSuccess { sources[path] = it }
                    .onFailure { Log.w(TAG, "could not read the shader $path: ${it.message}") }
            }

            val mismatches = engine.loadEffects(manifestJson, encodeSources(sources))
            if (mismatches > 0) {
                Log.w(TAG, "$mismatches effect interfaces do not match their shaders")
            }

            availableEffects = parseDescriptors(manifestJson)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    /** Encode the shader sources as the JSON the core expects. */
    private fun encodeSources(sources: Map<String, String>): String {
        val root = JSONObject()
        for ((path, source) in sources) {
            root.put(path, source)
        }
        return root.toString()
    }

    /** Parse the effect list out of the manifest. */
    private fun parseDescriptors(manifestJson: String): List<EffectDescriptor> = runCatching {
        val root = JSONObject(manifestJson)
        val effects = root.optJSONArray("effects") ?: return emptyList()
        (0 until effects.length()).mapNotNull { index ->
            val effect = effects.optJSONObject(index) ?: return@mapNotNull null
            val params = effect.optJSONArray("params")
            EffectDescriptor(
                id = effect.optString("id"),
                name = effect.optString("name", effect.optString("id")),
                category = effect.optString("category", "general"),
                index = index,
                params = if (params == null) {
                    emptyList()
                } else {
                    (0 until params.length()).mapNotNull { p ->
                        val param = params.optJSONObject(p) ?: return@mapNotNull null
                        EffectParamDescriptor(
                            name = param.optString("name"),
                            label = param.optString("label", param.optString("name")),
                            type = param.optString("type", "float"),
                            default = param.optDouble("default", 0.0),
                            min = param.optDouble("min", 0.0),
                            max = param.optDouble("max", 1.0),
                        )
                    }
                },
            )
        }
    }.getOrDefault(emptyList())

    /** Poll the engine for events. */
    private fun startEventLoop() {
        pollJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                var changed = false
                engine.drainEvents { event ->
                    when (event.tag) {
                        VimoEventTag.POSITION -> {
                            positionNs = event.a
                            changed = true
                        }
                        VimoEventTag.PLAYBACK_STATE -> {
                            playing = event.a == 1L
                            changed = true
                        }
                        VimoEventTag.PROJECT_CHANGED -> {
                            changed = true
                        }
                        VimoEventTag.ERROR -> {
                            error = event.message
                            changed = true
                        }
                        else -> Unit
                    }
                }
                if (changed) {
                    withContext(Dispatchers.Main) { refresh() }
                }
                delay(16)
            }
        }
    }

    /** Re-read the project from the engine. */
    fun refresh() {
        val json = engine.snapshot()
        project = SnapshotParser.parse(json)
        dirty = json != lastSnapshot
        lastSnapshot = json
    }

    private var lastSnapshot: String = ""

    // --- transport ---------------------------------------------------------

    /** Start or pause playback. */
    fun togglePlayback() {
        if (playing) engine.pause() else engine.play()
    }

    /** Stop playback. */
    fun stop() {
        engine.stop()
        positionNs = 0
    }

    /** Seek to a position, in nanoseconds. */
    fun seek(timeNs: Long) {
        engine.seek(timeNs)
        positionNs = timeNs
    }

    /** Move the playhead by a number of frames. */
    fun step(frames: Int) {
        val frameNs = (1_000_000_000.0 / project.fps).toLong()
        seek((positionNs + frames * frameNs).coerceAtLeast(0L))
    }

    // --- editing -----------------------------------------------------------

    /** Select a clip, or clear the selection. */
    fun selectClip(clipId: Int?) {
        selectedClipId = clipId
    }

    /** The selected clip, if there is one. */
    fun selectedClip(): ClipModel? =
        project.tracks.flatMap { it.clips }.firstOrNull { it.id == selectedClipId }

    /** Move a clip to a new start time. */
    fun moveClip(clipId: Int, newStartNs: Long) {
        engine.execute(VimoCommand.MOVE_CLIP, a = clipId.toLong(), c = newStartNs)
    }

    /** Trim a clip. */
    fun trimClip(clipId: Int, newStartNs: Long, newEndNs: Long) {
        engine.execute(
            VimoCommand.TRIM_CLIP,
            a = clipId.toLong(),
            b = newStartNs,
            c = newEndNs,
        )
    }

    /** Split the selected clip at the playhead. */
    fun splitAtPlayhead() {
        val clip = selectedClip() ?: return
        engine.execute(VimoCommand.SPLIT_CLIP, a = clip.id.toLong(), b = positionNs)
    }

    /** Delete the selected clip. */
    fun deleteSelected() {
        val clip = selectedClip() ?: return
        val track = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clip.id } }
        if (track < 0) return
        engine.execute(VimoCommand.REMOVE_CLIP, a = track.toLong(), b = clip.id.toLong())
        selectedClipId = null
    }

    /** Undo. */
    fun undo() {
        engine.undo()
    }

    /** Redo. */
    fun redo() {
        engine.redo()
    }

    /** Add an effect to the selected clip. */
    fun addEffect(descriptor: EffectDescriptor) {
        val clip = selectedClip() ?: return
        engine.execute(
            VimoCommand.ADD_EFFECT,
            a = 0, // the owner is a clip
            b = clip.id.toLong(),
            c = descriptor.index.toLong(),
        )
    }

    /** Set a parameter of an effect. */
    fun setParam(effectId: Int, paramIndex: Int, value: Float) {
        engine.execute(
            VimoCommand.SET_PARAM,
            a = effectId.toLong(),
            b = paramIndex.toLong(),
            c = java.lang.Float.floatToRawIntBits(value).toLong(),
        )
    }

    /** Set the opacity of the selected clip. */
    fun setClipOpacity(value: Float) {
        val clip = selectedClip() ?: return
        engine.execute(
            VimoCommand.SET_CLIP_OPACITY,
            a = clip.id.toLong(),
            b = java.lang.Float.floatToRawIntBits(value).toLong(),
        )
    }

    /** Toggle whether the selected clip is enabled. */
    fun toggleClipEnabled() {
        val clip = selectedClip() ?: return
        engine.execute(
            VimoCommand.SET_CLIP_ENABLED,
            a = clip.id.toLong(),
            b = if (clip.enabled) 0L else 1L,
        )
    }

    // --- files -------------------------------------------------------------

    /** Save the project. */
    fun saveProject(path: String) {
        engine.saveProject(path)
        dirty = false
        message = "Saved"
    }

    /** Load a project. */
    fun loadProject(path: String) {
        engine.loadProject(path)
        refresh()
        message = "Loaded"
    }

    /** Start an export. */
    fun startExport(preset: Int) {
        engine.execute(VimoCommand.START_EXPORT, a = preset.toLong())
        message = "Export started"
    }

    /** Cancel the export. */
    fun cancelExport() {
        engine.execute(VimoCommand.CANCEL_EXPORT)
    }

    /** Ask the engine for a snapshot event. */
    fun requestSnapshot() {
        engine.requestSnapshot()
    }

    /** Report a thermal reading from the platform. */
    fun reportThermal(status: Int) {
        engine.execute(VimoCommand.THERMAL, a = status.toLong())
    }

    /** Report the memory usage. */
    fun reportMemory() {
        engine.execute(VimoCommand.MEMORY)
    }

    /** Clear the transient message. */
    fun clearMessage() {
        message = null
        error = null
    }

    override fun onCleared() {
        pollJob?.cancel()
        engine.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "VimoViewModel"

        /** Where the effect manifest lives in the APK's assets. */
        const val MANIFEST_PATH = "vimo/manifests/vimo.effects.json"
    }
}
