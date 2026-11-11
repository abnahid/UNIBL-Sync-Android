import java.util.Properties

val appConfig = Properties().apply {
    rootProject.file("app-config.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun Properties.publicConfig(name: String): String =
    getProperty(name, "")
        .trim()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "com.abnahid.uniblsync"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.abnahid.uniblsync"

        minSdk = 24
        targetSdk = 36

        versionCode =
            providers.gradleProperty("VERSION_CODE")
                .orNull
                ?.toIntOrNull()
                ?: 2

        versionName =
            providers.gradleProperty("VERSION_NAME")
                .orNull
                ?: "2.0.0"

        buildConfigField(
            "String",
            "PUBLIC_API_BASE_URL",
            "\"${appConfig.publicConfig("PUBLIC_API_BASE_URL")}\""
        )

        buildConfigField(
            "String",
            "PUBLIC_ROUTE_URL",
            "\"${appConfig.publicConfig("PUBLIC_ROUTE_URL")}\""
        )

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is supplied by CI/local environment variables.
    // Debug builds use the repository debug keystore (auto-generated if missing in CI).
    val debugKeystoreFile = rootProject.file("debug.keystore").apply {
        if (!exists()) {
            val userKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
            if (userKeystore.exists()) {
                userKeystore.copyTo(this, overwrite = true)
            } else {
                try {
                    val keytool = File(System.getProperty("java.home"), "bin/keytool")
                        .takeIf { it.exists() }?.absolutePath ?: "keytool"
                    ProcessBuilder(
                        keytool,
                        "-genkeypair",
                        "-keystore", absolutePath,
                        "-storepass", "android",
                        "-alias", "androiddebugkey",
                        "-keypass", "android",
                        "-keyalg", "RSA",
                        "-keysize", "2048",
                        "-validity", "10000",
                        "-dname", "CN=Android Debug,O=Android,C=US"
                    ).start().waitFor()
                } catch (_: Exception) {}
            }
        }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = if (debugKeystoreFile.exists()) debugKeystoreFile else file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        create("release") {
            val keystorePath =
                System.getenv("KEYSTORE_PATH")
                    ?: "${rootDir}/my-upload-key.jks"

            storeFile = file(keystorePath)

            storePassword =
                System.getenv("STORE_PASSWORD")

            keyAlias = "upload"

            keyPassword =
                System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )

            // Keep this release signing configuration.
            signingConfig =
                signingConfigs.getByName("release")
        }

        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

// Dependencies
dependencies {
    implementation(platform(libs.androidx.compose.bom))

    // implementation(libs.accompanist.permissions)

    implementation(libs.androidx.activity.compose)

    // implementation(libs.androidx.camera.camera2)
    // implementation(libs.androidx.camera.core)
    // implementation(libs.androidx.camera.lifecycle)
    // implementation(libs.androidx.camera.view)

    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.core.ktx)

    // implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)

    // implementation(libs.coil.compose)

    implementation(libs.converter.moshi)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)

    // implementation(libs.play.services.location)

    implementation(libs.retrofit)
    implementation(libs.androidx.work.runtime)

    // Unit tests
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    // Android tests
    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)

    // Debug dependencies
    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    // KSP
    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)
}
