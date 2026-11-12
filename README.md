# Vimo for Android

The Android app and the platform layer for the [Vimo Core](../vimo).

The core is a separate, platform independent repository. This repository holds:

| Path | What |
|------|------|
| `crates/vimo-android` | The platform layer: EGL + OpenGL ES 3, MediaCodec, Oboe, the device profile, the Perfetto sink and the zero-copy surface path. |
| `crates/vimo-jni` | The JNI bindings between Kotlin and the core. |
| `app` | The Kotlin / Compose application. |
| `app/src/main/cpp` | The Oboe shim (Oboe is C++, so the platform layer reaches it through a small C interface). |

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

The core is expected as a **sibling checkout**:

```sh
git clone https://github.com/Shrawan-13/vimo-core.git ../vimo
cd vimo-android
./gradlew :app:assembleDebug
```

The Gradle build shells out to `cargo ndk` (see `app/build.gradle.kts`), so
the NDK and `cargo-ndk` have to be installed:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64
cargo install cargo-ndk
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

## What is still missing

* The EGLImage path for the *true* zero-copy decode (`Surface → texture`).
  The decoder already renders into a surface; the missing piece is the
  `EGLImage` binding that turns it into a `GL_TEXTURE_EXTERNAL_OES` texture.
* The real `MediaCodecList` capability query (the fallback in
  `vimo_android::device` reports a typical phone).
* The export encoder and the muxer.
* The Kotlin timeline UI (the current activity shows the preview surface and
  the engine's status).
