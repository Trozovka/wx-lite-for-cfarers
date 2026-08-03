import java.util.Properties

// Release signing: read from local.properties (gitignored) or environment
// variables, never hardcoded -- same pattern as wx-pro-for-cfarers. The
// keystore itself lives outside this repo entirely. Falls back to
// unsigned if not configured on a given machine, so ./gradlew test and
// similar still work without a keystore present.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseKeystorePath = localProps.getProperty("RELEASE_KEYSTORE_PATH") ?: System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = localProps.getProperty("RELEASE_KEYSTORE_PASSWORD") ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = localProps.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")

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
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseKeystorePath != null && releaseKeystorePassword != null && releaseKeyAlias != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword ?: releaseKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
}
