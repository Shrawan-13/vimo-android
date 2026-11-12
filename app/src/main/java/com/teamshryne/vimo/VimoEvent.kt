package com.teamshryne.vimo

/**
 * An event sent by the engine.
 *
 * @property a the first integer payload.
 * @property b the second integer payload.
 * @property tag one of [VimoEventTag].
 * @property code an error code, for [VimoEventTag.ERROR].
 * @property message an optional message, for errors and snapshots.
 */
data class VimoEvent(
    val a: Long,
    val b: Long,
    val tag: Int,
    val code: Int,
    val message: String?,
)

/** The event tags. These mirror the core's `event_tag` module. */
object VimoEventTag {
    /** The project changed. */
    const val PROJECT_CHANGED = 1

    /** The playback state changed. */
    const val PLAYBACK_STATE = 2

    /** The playhead moved. */
    const val POSITION = 3

    /** A frame was rendered. */
    const val FRAME_RENDERED = 4

    /** The memory pressure level changed. */
    const val MEMORY_PRESSURE = 5

    /** The thermal state changed. */
    const val THERMAL = 6

    /** The export progressed. */
    const val EXPORT_PROGRESS = 7

    /** The export finished. */
    const val EXPORT_FINISHED = 8

    /** An error occurred. */
    const val ERROR = 9

    /** A project snapshot is available. */
    const val SNAPSHOT = 10
}

/** The command tags. These mirror the core's `command_tag` module. */
object VimoCommand {
    /** Start playback. */
    const val PLAY = 1

    /** Pause playback. */
    const val PAUSE = 2

    /** Stop playback and rewind. */
    const val STOP = 3

    /** Seek to a position, in nanoseconds. */
    const val SEEK = 4

    /** Set the playback rate, in thousandths (`1000` is normal speed). */
    const val SET_RATE = 5

    /** Undo the last edit. */
    const val UNDO = 6

    /** Redo the last undone edit. */
    const val REDO = 7

    /** Add a clip: `a` = track, `b` = asset, `c` = start in nanoseconds. */
    const val ADD_CLIP = 8

    /** Remove a clip: `a` = track, `b` = clip. */
    const val REMOVE_CLIP = 9

    /** Set an effect parameter: `a` = effect, `b` = parameter, `c` = float bits. */
    const val SET_PARAM = 10

    /** Load a project from a path. */
    const val LOAD_PROJECT = 11

    /** Save the project to a path. */
    const val SAVE_PROJECT = 12

    /** Start an export: `a` = preset (0 = 1080p, 1 = 720p, 2 = 4K). */
    const val START_EXPORT = 13

    /** Cancel the running export. */
    const val CANCEL_EXPORT = 14

    /** Hand over the output surface. */
    const val SET_SURFACE = 15

    /** Report a thermal reading: `a` = the Android thermal status. */
    const val THERMAL = 16

    /** Report the memory usage. */
    const val MEMORY = 17

    /** Set the preview scale, in thousandths. */
    const val SET_PREVIEW_SCALE = 18

    /** Move a clip: `a` = clip, `b` = track, `c` = new start in nanoseconds. */
    const val MOVE_CLIP = 19

    /** Trim a clip: `a` = clip, `b` = new start, `c` = new end. */
    const val TRIM_CLIP = 20

    /** Split a clip: `a` = clip, `b` = the time to split at. */
    const val SPLIT_CLIP = 21

    /** Enable or disable a clip: `a` = clip, `b` = 0 or 1. */
    const val SET_CLIP_ENABLED = 22

    /** Set a clip's opacity: `a` = clip, `b` = float bits. */
    const val SET_CLIP_OPACITY = 23

    /** Set a clip's blend mode: `a` = clip, `b` = the blend mode. */
    const val SET_CLIP_BLEND = 24

    /** Set a clip's transform: `a` = clip, the floats carry the values. */
    const val SET_CLIP_TRANSFORM = 25

    /** Set a clip's volume: `a` = clip, `b` = float bits. */
    const val SET_CLIP_VOLUME = 26

    /** Set a clip's speed: `a` = clip, `b` = float bits. */
    const val SET_CLIP_SPEED = 27

    /** Mute or unmute a clip: `a` = clip, `b` = 0 or 1. */
    const val SET_CLIP_MUTED = 28

    /** Add a track: `a` = the track kind (0 = video, 1 = audio). */
    const val ADD_TRACK = 29

    /** Remove a track: `a` = track. */
    const val REMOVE_TRACK = 30

    /** Enable or disable a track: `a` = track, `b` = 0 or 1. */
    const val SET_TRACK_ENABLED = 31

    /** Mute or unmute a track: `a` = track, `b` = 0 or 1. */
    const val SET_TRACK_MUTED = 32

    /** Solo a track: `a` = track, `b` = 0 or 1. */
    const val SET_TRACK_SOLO = 33

    /** Lock or unlock a track: `a` = track, `b` = 0 or 1. */
    const val SET_TRACK_LOCKED = 34

    /** Set a track's volume: `a` = track, `b` = float bits. */
    const val SET_TRACK_VOLUME = 35

    /** Add an effect: `a` = the owner kind (0 = clip, 1 = track), `b` = the owner, `c` = the effect type. */
    const val ADD_EFFECT = 36

    /** Remove an effect: `a` = the owner kind, `b` = the owner, `c` = the effect. */
    const val REMOVE_EFFECT = 37

    /** Enable or disable an effect: `a` = the effect, `b` = 0 or 1. */
    const val SET_EFFECT_ENABLED = 38

    /** Add a keyframe: `a` = the effect, `b` = the parameter, `c` = the time. */
    const val ADD_KEYFRAME = 39

    /** Remove a keyframe: `a` = the effect, `b` = the parameter, `c` = the time. */
    const val REMOVE_KEYFRAME = 40

    /** Set a keyframe's interpolation: `a` = the effect, `b` = the parameter, `c` = the mode. */
    const val SET_KEYFRAME_INTERPOLATION = 41

    /** Add a media source: the text is the path, the floats carry the duration. */
    const val ADD_ASSET = 42

    /** Remove a media source: `a` = the asset. */
    const val REMOVE_ASSET = 43

    /** Add a transition: `a` = track, `b` = from clip, `c` = to clip. */
    const val ADD_TRANSITION = 44

    /** Remove a transition: `a` = the transition. */
    const val REMOVE_TRANSITION = 45

    /** Set the project name. */
    const val SET_PROJECT_NAME = 46

    /** Set the resolution: `a` = width, `b` = height. */
    const val SET_RESOLUTION = 47

    /** Set the frame rate: `a` = numerator, `b` = denominator. */
    const val SET_FPS = 48

    /** Set the autosave directory. */
    const val SET_AUTOSAVE_DIR = 49

    /** Recover the newest autosave. */
    const val RECOVER_AUTOSAVE = 50

    /** Loop the playback: `a` = 0 or 1. */
    const val SET_LOOP = 51

    /** Pause the export. */
    const val EXPORT_PAUSE = 52

    /** Resume the export. */
    const val EXPORT_RESUME = 53

    /** Ask the engine for a snapshot event. */
    const val REQUEST_SNAPSHOT = 54
}

/** The status codes returned by the core. */
object VimoStatus {
    /** The call succeeded. */
    const val OK = 0

    /** The handle was null or already destroyed. */
    const val INVALID_HANDLE = -1

    /** The command was malformed. */
    const val INVALID_ARGUMENT = -2

    /** The engine reported an error. */
    const val ERROR = -3

    /** A panic was caught at the boundary. */
    const val PANIC = -4

    /** The feature is not implemented on this platform. */
    const val UNSUPPORTED = -5
}
