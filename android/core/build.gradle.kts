plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// The shared core: everything that's identical between the free and paid
// tiers -- data fetch/parse/cache, chart rendering logic, map projection.
// Free-vs-paid is a single constructor parameter (ForecastTier) already
// threaded through this code, not a fork; the paid app depends on this
// module rather than copying it, per PROJECT_SPEC.md Section 9.
android {
    namespace = "com.trozovka.wxlite.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
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
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
}
