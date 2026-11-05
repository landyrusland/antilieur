plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.antilieur"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.antilieur"
        minSdk = 26          // Android 8.0 ke atas
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Kode diperkecil oleh R8 sehingga APK sangat ringan
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Ditandatangani dengan kunci debug supaya APK bisa langsung dipasang.
            // Ganti dengan kunci sendiri jika ingin rilis ke Play Store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Lewati pemeriksaan lint saat build agar lebih cepat dan hemat memori
        checkReleaseBuilds = false
        abortOnError = false
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Sengaja tanpa library tambahan (AndroidX dll.) agar build cepat dan APK kecil.
