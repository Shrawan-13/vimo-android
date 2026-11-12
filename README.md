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

## How the pieces fit together

```text
  Kotlin UI  ──commands──►  vimo-jni  ──►  vimo-core (the engine)
      ▲                        │
      └──────events────────────┘
                                   │
                 vimo-android ────┤  MediaCodec · Oboe · EGL · Perfetto
                                   ▼
                            the device
```

* The **UI** never queries the engine. It sends commands and receives events;
  when it needs to redraw the timeline it asks for a JSON snapshot.
* The **engine** owns the project, the render graph, the clock and the export
  pipeline. It knows nothing about Android.
* The **platform layer** owns everything device specific. The core reaches it
  through a handful of traits (`Decoder`, `Encoder`, `GlApi`, `AudioOutput`,
  `TraceSink`, `DeviceProbe`, `SurfaceSource`).

## Building

```sh
./gradlew :app:assembleDebug
```

The build downloads `vimo-native-<version>-android.zip` from the core
repository's releases. It is cached in `~/.gradle/vimo-native`, so only the
first build pays for it.

The version is pinned in `gradle.properties`:

```properties
vimo.native.version=0.1.0
```

If the artifact cannot be downloaded (before the first release, for example),
the CI workflow can build it from the core instead:

```sh
gh workflow run android.yml -f build_native=true
```

## CI

`.github/workflows/android.yml` has two jobs:

* **rust** — clones the core, formats, lints, tests it, and builds the
  Android libraries for `arm64-v8a` and `x86_64` with `cargo ndk`.
* **android** — builds the debug APK (which runs the Rust build again through
  Gradle) and uploads it as an artefact.

The Android libraries are built by CI rather than committed, so the repository
stays free of binaries.

## The native bindings are declared by hand

`crates/vimo-android/src/ffi.rs` declares EGL, OpenGL ES 3 and `MediaCodec`
itself instead of generating bindings from the NDK headers. That is
deliberate: it means the crate **compiles on a desktop**, so the Rust code is
type checked by CI even when the Android build is not running.

```sh
cargo build   # works on a desktop; the Android paths are compiled but not run
```

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
