plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.trozovka.wxlite"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trozovka.wxlite"
        // minSdk 21 (Android 5.0, 2014) deliberately low — the whole point
        // of this app is running on old, low-spec devices, not just
        // whatever's current.
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // No Compose, no Material — plain Views only. Every dependency here is
    // bytes in the APK and cycles on a phone this app is specifically meant
    // to be gentle to.
    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
