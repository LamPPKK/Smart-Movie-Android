plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lamndt.smartmovie.data"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(libs.androidx.paging.runtime)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
