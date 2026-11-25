plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.uwiii.nav"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.uwiii.nav"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("signing/uWiiiNav.keystore")
            storePassword = "uWiiiNavSecret"
            keyAlias = "uWiiiNavKey"
            keyPassword = "uWiiiNavSecret"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // MapLibre Maps SDK for Android (Open Source OSM Renderer)
    implementation(libs.maplibre.android.sdk)

    // Location & GPS Services
    implementation(libs.play.services.location)

    // Networking (for Overpass / OSM routing & GitHub updater)
    implementation(libs.okhttp)

    // Android Car App Library (Android Auto)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    debugImplementation(libs.androidx.ui.tooling)
}
