plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Android and desktop ship together under the workspace version in Cargo.toml, so one changelog
// section and one GitHub release cover both.
val encoreVersion: String = rootProject.file("../Cargo.toml").readLines()
    .first { it.startsWith("version = ") }
    .substringAfter('"')
    .substringBefore('"')
val encoreVersionCode: Int = encoreVersion.split('.').map(String::toInt)
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// The release key comes from the environment (CI secrets). Without it the release build falls
// back to the debug key, which installs but cannot update a copy signed with the release key.
val releaseKeystore: String? = System.getenv("ENCORE_KEYSTORE")

android {
    namespace = "dev.scorpiontaj.encore"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.scorpiontaj.encore"
        minSdk = 29
        targetSdk = 36
        versionCode = encoreVersionCode
        versionName = encoreVersion

        // Encore for Android ships for 64-bit ARM phones only. Compose pulls in one small native
        // library; the filter packages it for arm64-v8a alone.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ENCORE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ENCORE_KEY_ALIAS")
                keyPassword = System.getenv("ENCORE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    splits {
        abi { isEnable = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

base {
    archivesName.set("encore-${android.defaultConfig.versionName}-arm64-v8a")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.guava)
}
