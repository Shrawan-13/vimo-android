import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vimoCoreDir: String = (project.findProperty("vimo.core.dir") as String?) ?: "../vimo"
val vimoAbis: List<String> =
    ((project.findProperty("vimo.abis") as String?) ?: "arm64-v8a").split(",").map { it.trim() }

/** The directory the Rust libraries are written to. */
val jniLibsDir = layout.buildDirectory.dir("jniLibs")

/**
 * Builds the Rust libraries for every Android ABI and copies the resulting
 * shared objects into the APK.
 *
 * `cargo ndk` is used because it sets the linker and the API level correctly
 * for each ABI; the CI workflow installs it with `taiki-e/install-action`.
 */
val buildRust by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the Vimo Rust libraries for Android."

    workingDir = file(vimoCoreDir)
    val outputDir = jniLibsDir.get().asFile
    // The Oboe shim is built by CMake into the same directory, so the Rust
    // linker can find it.
    val nativeLibDir = layout.projectDirectory.dir("src/main/jniLibs").asFile
    val profile = if (gradle.startParameter.taskNames.any { it.contains("Release", true) }) {
        "release"
    } else {
        "debug"
    }

    commandLine(
        "cargo", "ndk",
        "-t", vimoAbis.joinToString(","),
        "-o", outputDir.absolutePath,
        "build", "--profile", profile,
        "-p", "vimo-jni",
        "-p", "vimo-android",
    )
    environment("CARGO_TERM_COLOR", "always")
    environment("VIMO_NATIVE_LIB_DIR", nativeLibDir.absolutePath)
    // The core is a path dependency; keep it out of the Android lock file.
    environment("CARGO_NET_OFFLINE", "false")
}

// The Rust libraries link against the Oboe shim, so CMake has to run first.
buildRust.configure {
    dependsOn("configureCMakeDebug[arm64-v8a]")
}

tasks.withType<com.android.build.gradle.tasks.MergeSourceSetFolders>().configureEach {
    dependsOn(buildRust)
    jniLibs.srcDir(jniLibsDir)
}

tasks.withType<com.android.build.gradle.tasks.StripDebugSymbols>().configureEach {
    // The Rust libraries are already stripped in release.
    if (name.contains("Debug")) {
        keepDebugSymbols += setOf("**/*.so")
    }
}

android {
    namespace = "com.teamshryne.vimo"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.teamshryne.vimo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-O2", "-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        prefab = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Oboe is used through prefab, so the CMake build can `find_package(oboe)`.
    implementation("com.google.oboe:oboe:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
