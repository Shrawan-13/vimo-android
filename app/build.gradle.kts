import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The native libraries are **not** in this repository.
 *
 * They are built by the core repository and published as a GitHub release
 * asset; the build downloads them, checks their integrity and unpacks them.
 * That keeps this repository small and means the native code is built and
 * tested in exactly one place.
 *
 * Set `vimo.native.local=true` to build them from a local checkout instead
 * (useful while working on the core).
 */
val vimoNativeVersion: String =
    (project.findProperty("vimo.native.version") as String?) ?: "0.1.0"
val vimoNativeLocal: Boolean =
    (project.findProperty("vimo.native.local") as String?)?.toBoolean() ?: false
val vimoCoreDir: String = (project.findProperty("vimo.core.dir") as String?) ?: "../vimo"
val vimoAbis: List<String> =
    ((project.findProperty("vimo.abis") as String?) ?: "arm64-v8a").split(",").map { it.trim() }

/** Where the downloaded artifact is unpacked. */
val vimoNativeDir = layout.buildDirectory.dir("vimo-native")

/** The download cache, shared between builds. */
val vimoNativeCacheDir: File =
    File(gradle.gradleUserHomeDir, "vimo-native")

val downloadVimoNative by tasks.registering {
    group = "build"
    description = "Downloads the Vimo native libraries from the core release."

    val version = vimoNativeVersion
    val cacheDir = vimoNativeCacheDir
    val outputDir = vimoNativeDir.get().asFile

    inputs.property("version", version)
    outputs.dir(outputDir)

    doLast {
        val artifact = "vimo-native-$version-android.zip"
        val baseUrl = "https://github.com/Shrawan-13/vimo-core/releases/download/v$version"
        val cached = File(cacheDir, artifact)
        val checksum = File(cacheDir, "$artifact.sha256")

        if (!cached.exists() || !checksum.exists()) {
            cacheDir.mkdirs()
            logger.lifecycle("Downloading $artifact")
            val url = java.net.URL("$baseUrl/$artifact")
            url.openStream().use { input ->
                cached.outputStream().use { output -> input.copyTo(output) }
            }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(cached.readBytes())
                .joinToString("") { "%02x".format(it) }
            checksum.writeText("$digest  $artifact\n")
        }

        // Verify the download against the checksum that was published next to
        // it. A truncated or tampered download must never reach the APK.
        val expected = checksum.readText().trim().split(" ").first()
        val actual = java.security.MessageDigest.getInstance("SHA-256")
            .digest(cached.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(expected == actual) {
            "the downloaded native artifact does not match its checksum"
        }

        outputDir.deleteRecursively()
        outputDir.mkdirs()
        java.util.zip.ZipFile(cached).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        logger.lifecycle("Unpacked the native libraries into $outputDir")
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

    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].assets.srcDir(vimoNativeDir.map { it.dir("assets") })

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

    debugImplementation("androidx.compose.ui:ui-tooling")
}
