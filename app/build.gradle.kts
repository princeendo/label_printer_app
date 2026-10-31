// SPDX-License-Identifier: GPL-3.0-only

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.github.princeendo.labelprinter"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.princeendo.labelprinter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-bringup"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":printer"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.zxing.core)

    // Android test tasks do not let kotlin("test") infer JUnit 5, so name the variant.
    testImplementation(kotlin("test-junit5"))
}
