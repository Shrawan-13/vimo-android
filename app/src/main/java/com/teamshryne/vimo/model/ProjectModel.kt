package com.teamshryne.vimo.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The timeline model the UI works with.
 *
 * The engine owns the project; the UI keeps a parsed, read-only view of it so
 * that drawing the timeline never touches the native side.
 */
data class ProjectModel(
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Float,
    val durationNs: Long,
    val tracks: List<TrackModel>,
) {
    /** The total number of clips. */
    val clipCount: Int get() = tracks.sumOf { it.clips.size }

    companion object {
        /** The empty project. */
        val EMPTY = ProjectModel("", 1920, 1080, 30f, 0L, emptyList())
    }
}

/** A track on the timeline. */
data class TrackModel(
    val id: Int,
    val name: String,
    val kind: TrackKind,
    val enabled: Boolean,
    val muted: Boolean,
    val clips: List<ClipModel>,
)

/** Whether a track carries pictures or sound. */
enum class TrackKind {
    /** A video track. */
    VIDEO,

    /** An audio track. */
    AUDIO,
}

/** A clip on the timeline. */
data class ClipModel(
    val id: Int,
    val asset: Int,
    val startNs: Long,
    val endNs: Long,
    val sourceInNs: Long,
    val speed: Float,
    val opacity: Float,
    val blend: String,
    val enabled: Boolean,
    val effectCount: Int,
) {
    /** The duration in nanoseconds. */
    val durationNs: Long get() = (endNs - startNs).coerceAtLeast(0)
}

/** The effect attached to a clip. */
data class EffectModel(
    val id: Int,
    val type: Int,
    val enabled: Boolean,
    val params: List<ParamModel>,
    val keyframeCount: Int,
)

/** One parameter of an effect. */
data class ParamModel(
    val index: Int,
    val type: String,
    val value: Double,
)

/**
 * Parses the engine's JSON snapshot.
 *
 * The parser is deliberately forgiving: a field that is missing or has an
 * unexpected type falls back to a sensible default rather than throwing, so a
 * snapshot from a newer core never crashes the app.
 */
object SnapshotParser {

    /** Parse a snapshot. */
    fun parse(json: String): ProjectModel {
        if (json.isBlank()) return ProjectModel.EMPTY
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return ProjectModel.EMPTY

        val meta = root.optJSONObject("meta") ?: JSONObject()
        val fpsNum = meta.optLong("fpsNum", 30L)
        val fpsDen = meta.optLong("fpsDen", 1L).coerceAtLeast(1L)
        val tracks = root.optJSONArray("tracks")?.let { parseTracks(it) } ?: emptyList()

        return ProjectModel(
            name = meta.optString("name", "Untitled"),
            width = meta.optInt("width", 1920),
            height = meta.optInt("height", 1080),
            fps = fpsNum.toFloat() / fpsDen.toFloat(),
            durationNs = meta.optLong("durationNs", 0L),
            tracks = tracks,
        )
    }

    private fun parseTracks(array: JSONArray): List<TrackModel> = buildList {
        for (i in 0 until array.length()) {
            val track = array.optJSONObject(i) ?: continue
            add(
                TrackModel(
                    id = track.optInt("id", i),
                    name = track.optString("name", "Track ${i + 1}"),
                    kind = if (track.optString("kind") == "audio") TrackKind.AUDIO else TrackKind.VIDEO,
                    enabled = track.optBoolean("enabled", true),
                    muted = track.optBoolean("muted", false),
                    clips = track.optJSONArray("clips")?.let { parseClips(it) } ?: emptyList(),
                ),
            )
        }
    }

    private fun parseClips(array: JSONArray): List<ClipModel> = buildList {
        for (i in 0 until array.length()) {
            val clip = array.optJSONObject(i) ?: continue
            add(
                ClipModel(
                    id = clip.optInt("id", i),
                    asset = clip.optInt("asset", 0),
                    startNs = clip.optLong("startNs", 0L),
                    endNs = clip.optLong("endNs", 0L),
                    sourceInNs = clip.optLong("sourceInNs", 0L),
                    speed = clip.optDouble("speed", 1.0).toFloat(),
                    opacity = clip.optDouble("opacity", 1.0).toFloat(),
                    blend = clip.optString("blend", "normal"),
                    enabled = clip.optBoolean("enabled", true),
                    effectCount = clip.optJSONArray("effects")?.length() ?: 0,
                ),
            )
        }
    }

    /**
     * Count the keyframes of an effect.
     *
     * The animation tracks are an array of objects, each with an array of
     * keys, so the count is the sum of their lengths.
     */
    private fun countKeys(tracks: JSONArray?): Int {
        if (tracks == null) return 0
        var total = 0
        for (i in 0 until tracks.length()) {
            total += tracks.optJSONObject(i)?.optJSONArray("keys")?.length() ?: 0
        }
        return total
    }

    /** Parse the effects of a clip out of the snapshot. */
    fun parseEffects(clip: JSONObject): List<EffectModel> =        clip.optJSONArray("effects")?.let { effects ->
            buildList {
                for (i in 0 until effects.length()) {
                    val effect = effects.optJSONObject(i) ?: continue
                    add(
                        EffectModel(
                            id = effect.optInt("id", i),
                            type = effect.optInt("type", 0),
                            enabled = effect.optBoolean("enabled", true),
                            params = effect.optJSONArray("params")?.let { params ->
                                buildList {
                                    for (p in 0 until params.length()) {
                                        val param = params.optJSONObject(p) ?: continue
                                        val value = param.optJSONObject("value")
                                        add(
                                            ParamModel(
                                                index = param.optInt("index", p),
                                                type = value?.optString("type", "float") ?: "float",
                                                value = value?.optDouble("value", 0.0) ?: 0.0,
                                            ),
                                        )
                                    }
                                }
                            } ?: emptyList(),
                            keyframeCount = countKeys(effect.optJSONArray("keyframes")),
                        ),
                    )
                }
            }
        } ?: emptyList()
}
