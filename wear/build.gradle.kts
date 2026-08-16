plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.lamndt.smartmovie.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lamndt.smartmovie"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures.compose = true
    androidResources {
        localeFilters += listOf("en", "vi", "ja", "ko", "zh-rCN", "zh-rTW")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    signingConfigs {
        create("release") {
            val path = providers.environmentVariable("SMARTMOVIE_KEYSTORE_PATH").orNull
            if (path != null) {
                storeFile = file(path)
                storePassword = providers.environmentVariable("SMARTMOVIE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SMARTMOVIE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SMARTMOVIE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes.named("release") {
        if (providers.environmentVariable("SMARTMOVIE_KEYSTORE_PATH").isPresent) {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:remote"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
