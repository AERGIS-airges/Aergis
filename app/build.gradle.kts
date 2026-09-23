import java.net.URI
import java.io.File
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedFontResDir = layout.buildDirectory.dir("generated/aergis-font-res")

android {
    namespace = "com.airgesture.control"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.airgesture.control"
        minSdk = 26
        targetSdk = 36
        versionCode = 63
        versionName = "0.18.38-preview"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main").res.directories.add(generatedFontResDir.get().asFile.path)

    // Debug builds use Android's ephemeral debug keystore. A preview/release
    // keystore must never be required from, or embedded in, source control.
    signingConfigs {
        create("release") {
            val keystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
            if (!keystorePath.isNullOrBlank()) {
                storeFile = rootProject.file(keystorePath)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Deliberately use the Android debug key instead of a repository key.
            isDebuggable = true
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-service:2.9.1")
    implementation("androidx.core:core-ktx:1.19.0")

    val cameraX = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")

    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

val gestureModel = layout.projectDirectory.file("src/main/assets/gesture_recognizer.task").asFile
val expectedGestureModelSha256 =
    "97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482"

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

val downloadAergisFont by tasks.registering {
    val output = generatedFontResDir.map { it.file("font/aergis_display.ttf") }
    outputs.file(output)

    doLast {
        val fontFile = output.get().asFile
        fontFile.parentFile.mkdirs()
        val fontUrl =
            "https://raw.githubusercontent.com/google/fonts/" +
                "809e4d8b8d7e9364a914909bb777679606c178b8/" +
                "ofl/rajdhani/Rajdhani-SemiBold.ttf"

        if (!fontFile.exists() || fontFile.length() < 40_000L) {
            fontFile.delete()
            val temporary = File(fontFile.parentFile, fontFile.name + ".download")
            temporary.delete()
            try {
                val connection = URI(fontUrl).toURL().openConnection().apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    useCaches = false
                }
                connection.getInputStream().buffered().use { input ->
                    temporary.outputStream().buffered().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
                val magic = temporary.inputStream().use { input ->
                    ByteArray(4).also { bytes ->
                        if (input.read(bytes) != 4) {
                            throw GradleException("Bundled Aergis font download was truncated")
                        }
                    }
                }
                val validSfnt =
                    magic.contentEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00)) ||
                        magic.contentEquals(byteArrayOf('O'.code.toByte(), 'T'.code.toByte(), 'T'.code.toByte(), 'O'.code.toByte()))
                if (!validSfnt || temporary.length() < 40_000L) {
                    throw GradleException("Bundled Aergis font failed SFNT integrity validation")
                }
                temporary.copyTo(fontFile, overwrite = true)
            } finally {
                temporary.delete()
            }
        }
    }
}

val downloadGestureModel by tasks.registering {
    outputs.file(gestureModel)
    outputs.upToDateWhen { false }

    doLast {
        val modelUrl =
            "https://storage.googleapis.com/mediapipe-models/" +
                "gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task"

        fun isValidModel(): Boolean =
            gestureModel.exists() &&
                gestureModel.length() >= 1_000_000L &&
                sha256(gestureModel) == expectedGestureModelSha256

        if (!isValidModel()) {
            gestureModel.parentFile.mkdirs()
            gestureModel.delete()
            val temporary = File(gestureModel.parentFile, gestureModel.name + ".download")
            temporary.delete()

            try {
                var lastFailure: Throwable? = null
                for (attempt in 1..3) {
                    temporary.delete()
                    try {
                        val connection = URI(modelUrl).toURL().openConnection().apply {
                            connectTimeout = 15_000
                            readTimeout = 30_000
                            useCaches = false
                        }
                        connection.getInputStream().buffered().use { input ->
                            temporary.outputStream().buffered().use { outputStream ->
                                input.copyTo(outputStream)
                            }
                        }
                        val actualHash = sha256(temporary)
                        if (actualHash != expectedGestureModelSha256) {
                            throw GradleException(
                                "Gesture model SHA-256 mismatch. Expected " +
                                    expectedGestureModelSha256 + ", got " + actualHash + "."
                            )
                        }
                        temporary.copyTo(target = gestureModel, overwrite = true)
                        lastFailure = null
                        break
                    } catch (failure: Throwable) {
                        lastFailure = failure
                        if (attempt < 3) Thread.sleep(1_000L * attempt)
                    }
                }
                if (!gestureModel.exists()) {
                    throw GradleException("Gesture model download failed after 3 attempts.", lastFailure)
                }
            } finally {
                temporary.delete()
            }
        }

        val finalHash = sha256(gestureModel)
        if (finalHash != expectedGestureModelSha256) {
            gestureModel.delete()
            throw GradleException("Gesture model integrity verification failed.")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadGestureModel)
    dependsOn(downloadAergisFont)
}
