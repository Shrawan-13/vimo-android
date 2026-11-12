import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val persistentDebugKeystorePath = providers.environmentVariable("ANDROID_DEBUG_KEYSTORE_PATH").orNull
val persistentDebugKeystorePassword = providers.environmentVariable("ANDROID_DEBUG_KEYSTORE_PASSWORD").orNull
val persistentDebugKeyAlias = providers.environmentVariable("ANDROID_DEBUG_KEY_ALIAS").orNull

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
val vimoCoreRepo: String =
    (project.findProperty("vimo.core.repo") as String?) ?: "TeamShryne/vimo-core"
val vimoNativeSha256: String =
    (project.findProperty("vimo.native.sha256") as String?) ?: ""
val vimoAbis: List<String> =
    ((project.findProperty("vimo.abis") as String?) ?: "arm64-v8a").split(",").map { it.trim() }

/** Where the native libraries are unpacked. */
val vimoNativeDir = layout.buildDirectory.dir("vimo-native")

/**
 * The download cache.
 *
 * It lives under the XDG cache directory, so it is kept with the other
 * regenerable data instead of in a dot-folder in the home directory.
 */
val vimoNativeCacheDir: File = run {
    val xdg = providers.environmentVariable("XDG_CACHE_HOME").orNull
    if (!xdg.isNullOrBlank()) File(xdg, "vimo-native") else File(gradle.gradleUserHomeDir, "vimo-native")
}

/** The SHA-256 of a file. */
fun sha256(file: File): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

val downloadVimoNative by tasks.registering {
    group = "build"
    description = "Downloads the Vimo native libraries from the core release."

    val version = vimoNativeVersion
    val cacheDir = vimoNativeCacheDir
    val outputDir = vimoNativeDir.get().asFile
    val local = vimoNativeLocal
    val coreDir = vimoCoreDir
    val coreRepo = vimoCoreRepo
    val pinned = vimoNativeSha256
    val abis = vimoAbis

    inputs.property("version", version)
    inputs.property("local", local)
    inputs.property("abis", abis)
    outputs.dir(outputDir)

    doLast {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        if (local) {
            // Build the libraries from a local checkout of the core.
            logger.lifecycle("Building the native libraries from $coreDir")
            val jniLibs = File(outputDir, "jniLibs")
            jniLibs.mkdirs()
            val command = listOf(
                "cargo", "ndk",
                *abis.flatMap { listOf("-t", it) }.toTypedArray(),
                "-o", jniLibs.absolutePath,
                "build", "--release", "-p", "vimo-jni",
                "--manifest-path", File(coreDir, "Cargo.toml").absolutePath,
            )
            val builder = ProcessBuilder(command)
            builder.environment()["VIMO_NATIVE_LIB_DIR"] = jniLibs.absolutePath
            val process = builder.inheritIO().start()
            check(process.waitFor() == 0) { "the native libraries could not be built" }

            // The effect manifest and the shaders travel with the libraries.
            val assets = File(outputDir, "assets/vimo")
            assets.mkdirs()
            File(coreDir, "assets/manifests").copyRecursively(File(assets, "manifests"), overwrite = true)
            File(coreDir, "assets/shaders").copyRecursively(File(assets, "shaders"), overwrite = true)
            return@doLast
        }

        val artifact = "vimo-native-$version-android.zip"
        val baseUrl = "https://github.com/$coreRepo/releases/download/v$version"
        val cached = File(cacheDir, artifact)
        val checksum = File(cacheDir, "$artifact.sha256")

        if (!cached.exists() || !checksum.exists()) {
            cacheDir.mkdirs()
            logger.lifecycle("Downloading $artifact")
            try {
                java.net.URL("$baseUrl/$artifact").openStream().use { input ->
                    cached.outputStream().use { output -> input.copyTo(output) }
                }
                java.net.URL("$baseUrl/$artifact.sha256").openStream().use { input ->
                    checksum.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (error: Exception) {
                throw GradleException(
                    "The native libraries could not be downloaded from $baseUrl: ${error.message}\n" +
                        "Publish a core release first, or build them from a local checkout with " +
                        "-Pvimo.native.local=true.",
                    error,
                )
            }
        }

        // The checksum that was published next to the artifact must match it.
        val expected = checksum.readText().trim().split(" ")[0].lowercase()
        val actual = sha256(cached)
        check(expected == actual) {
            "the downloaded native artifact does not match its published checksum"
        }
        // When the checksum is pinned in gradle.properties, that wins: it
        // protects against a release that has been tampered with.
        if (pinned.isNotBlank()) {
            check(pinned.equals(actual, ignoreCase = true)) {
                "the downloaded native artifact does not match the pinned checksum"
            }
        }

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

    if (persistentDebugKeystorePath != null && persistentDebugKeystorePassword != null && persistentDebugKeyAlias != null) {
        signingConfigs {
            create("persistentDebug") {
                storeFile = file(persistentDebugKeystorePath)
                storePassword = persistentDebugKeystorePassword
                keyAlias = persistentDebugKeyAlias
                keyPassword = persistentDebugKeystorePassword
            }
        }
        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("persistentDebug")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    // The effect manifest and the shaders come with the native libraries.
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

tasks.withType<com.android.build.gradle.tasks.MergeSourceSetFolders>().configureEach {
    dependsOn(downloadVimoNative)
    jniLibs.srcDir(vimoNativeDir.map { it.dir("jniLibs") })
}

tasks.withType<com.android.build.gradle.tasks.StripDebugSymbols>().configureEach {
    // The native libraries are already stripped.
    keepDebugSymbols += setOf("**/*.so")
}
