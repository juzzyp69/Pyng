plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.jpetrydesign.pyng"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jpetrydesign.pyng"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.9.0"
        buildConfigField("String", "GIPHY_KEY", "\"" + (System.getenv("GIPHY_KEY") ?: "") + "\"")
        buildConfigField("String", "KLIPY_KEY", "\"" + (System.getenv("KLIPY_KEY") ?: "") + "\"")
    }

    signingConfigs {
        create("test") {
            storeFile = file("pyng-test.keystore")
            storePassword = "pyngtest"
            keyAlias = "pyng"
            keyPassword = "pyngtest"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            // Strip unused code and resources: the app's own code shrinks from about 42 MB to a few.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("test")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs.useLegacyPackaging = false
    }
    // One APK per phone type: arm64 for real phones, x86_64 for the test emulator. Nobody downloads the other.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation(files("libs/engine.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Applies the startup profile below on install, so launch and first scrolls run precompiled.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("dev.chrisbanes.haze:haze:1.5.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    // Trimming and cropping videos before they send, on the phone.
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
}
