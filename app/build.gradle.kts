plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "eu.depau.activelooknotifications"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "eu.depau.activelooknotifications"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // foss = no proprietary deps (F-Droid-friendly); nonfree = adds the Garmin ConnectIQ Mobile SDK
    // for auto-pause during a Garmin workout. The Garmin code lives in src/<flavor>/ (see GarminBridge).
    flavorDimensions += "license"
    productFlavors {
        create("nonfree") { dimension = "license"; isDefault = true }
        create("foss") { dimension = "license" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // for BuildConfig.FLAVOR, shown in Settings
    }
}

dependencies {
    implementation("com.github.activelook:android-sdk:4.5.9")
    // Garmin ConnectIQ Mobile SDK — nonfree flavor only (proprietary; needs Garmin Connect Mobile).
    "nonfreeImplementation"("com.garmin.connectiq:ciq-companion-app-sdk:2.4.0@aar")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
