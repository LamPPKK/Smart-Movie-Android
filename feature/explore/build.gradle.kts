plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.lamndt.smartmovie.feature.explore"; compileSdk = 36
    defaultConfig { minSdk = 26 }; buildFeatures.compose = true
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:model")); implementation(project(":core:data")); implementation(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.viewmodel.compose); implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.paging.compose); implementation(platform(libs.androidx.compose.bom)); implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.foundation); implementation(libs.androidx.compose.material3); implementation(libs.androidx.compose.material.icons)
    testImplementation(project(":core:testing")); testImplementation(libs.junit); testImplementation(libs.truth); testImplementation(libs.kotlinx.coroutines.test)
}
