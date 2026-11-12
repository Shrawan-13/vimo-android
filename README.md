# Vimo for Android

The Vimo Android application.

**This repository contains no native code.** The Rust core — including the
Android platform layer (MediaCodec, Oboe, EGL) — lives in its own repository
and is published as a GitHub release. The build downloads the release
artifact, verifies its checksum and unpacks it into the APK.

That keeps this repository small, means the native code is built and tested in
exactly one place, and lets the app and the core move independently.

| Path | What |
|------|------|
| `app` | The Kotlin / Compose application. |
| `app/src/main/java/.../ui` | The editor: the timeline, the preview, the inspector. |
| `app/src/main/java/.../model` | The parsed view of the engine's snapshot. |
| `app/build.gradle.kts` | The build, including the download of the native libraries. |

## Building

```sh
./gradlew :app:assembleDebug
```

The build downloads `vimo-native-<version>-android.zip` from the core
repository's releases. It is cached under `$XDG_CACHE_HOME/vimo-native` (or
`~/.gradle/vimo-native` when `XDG_CACHE_HOME` is not set), so only the first
build pays for it.

The version is pinned in `gradle.properties`:

```properties
vimo.native.version=0.1.0
vimo.core.repo=TeamShryne/vimo-core
```

The checksum that is published next to the artifact is always verified. For
extra safety, pin the artifact's own checksum:

```properties
vimo.native.sha256=<the sha256 of the zip>
```

If the artifact cannot be downloaded (before the first release, for example),
the libraries can be built from a local checkout of the core instead:

```sh
./gradlew -Pvimo.native.local=true :app:assembleDebug
```

The CI workflow can do the same:

```sh
gh workflow run android.yml -f build_native=true
```

## The editor

The layout adapts to the space it has:

* **Wide** (a tablet, or a phone in landscape) — the preview and the
  inspector sit side by side above the timeline.
* **Narrow** (a phone in portrait) — the preview is on top and the timeline
  takes the bottom third.

The timeline is drawn on a single canvas rather than composed from views: a
project can have hundreds of clips and the timeline has to stay at 60 fps
while the user scrubs.

| Gesture | What it does |
|---------|--------------|
| Tap a clip | Select it |
| Drag a clip | Move it on the timeline |
| Drag a clip's edge | Trim it (the picture stays in sync) |
| Drag the ruler | Scrub |
| Pinch | Zoom the timeline |
| Two fingers | Scroll the timeline |

## CI

`.github/workflows/android.yml` builds the debug APK and uploads it.

The build is fast because the caches are aggressive:

* the Gradle user home (dependencies, the build cache and the downloaded
  native artifact) is restored as one cache;
* the build directory is cached separately;
* the Gradle configuration cache is enabled.

A **prune job** runs after every build and deletes every repository cache
except the twenty newest, so the cache never grows without bound.

## What is still missing

* The EGLImage path for the *true* zero-copy decode (`Surface → texture`).
  The decoder already renders into a surface; the missing piece is the
  `EGLImage` binding that turns it into a `GL_TEXTURE_EXTERNAL_OES` texture.
* The real `MediaCodecList` capability query (the fallback reports a typical
  phone).
* The export encoder and the muxer.
* Thumbnails in the media bin, and the drag-and-drop of media onto the
  timeline.
