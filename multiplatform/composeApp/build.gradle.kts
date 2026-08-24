@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
}

group = "com.lamndt.smartmovie"
version = "3.0.0"

kotlin {
    jvm("desktop") {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    js {
        browser {
            commonWebpackConfig { outputFileName = "smartmovie.js" }
        }
        binaries.executable()
    }

    wasmJs {
        browser {
            commonWebpackConfig { outputFileName = "smartmovie.js" }
        }
        binaries.executable()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        named("desktopTest") {
            resources.srcDir(rootProject.file("../catalog-contract/v1/fixtures"))
        }
        named("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.cio)
            }
        }
        webMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.ktor.client.js)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.lamndt.smartmovie.multiplatform.generated.resources"
}

compose.desktop {
    application {
        from(kotlin.targets["desktop"])
        mainClass = "com.lamndt.smartmovie.multiplatform.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "SmartMovie"
            packageVersion = "3.0.0"
            description = "A cinematic movie and television catalog."
            copyright = "© 2026 SmartMovie"
            vendor = "Lam NDT"

            macOS {
                bundleID = "com.lamndt.smartmovie.desktop"
                dockName = "SmartMovie"
                minimumSystemVersion = "13.0"
            }
            windows {
                menuGroup = "SmartMovie"
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6b3c47f4-b12d-4c18-8b0d-49f2328295a7"
            }
            linux {
                packageName = "smartmovie"
                menuGroup = "AudioVideo"
                appCategory = "Video"
                debMaintainer = "support@smartmovie.app"
                rpmLicenseType = "Proprietary"
            }
        }
    }
}
