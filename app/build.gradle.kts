import fail.tiger.komgarot.build.GenerateReleaseVersionTask
import fail.tiger.komgarot.build.ReleaseVersioning
import fail.tiger.komgarot.build.ReleaseVersionState

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val komgarotVersionBase = providers.gradleProperty("komgarotVersionBase")
val releaseVersionFile = layout.buildDirectory.file("generated/komgarotReleaseVersion/release-version.properties")
val generateReleaseVersion = tasks.register<GenerateReleaseVersionTask>("generateReleaseVersion") {
    baseVersion.set(komgarotVersionBase)
    epochSeconds.set(providers.systemProperty("komgarot.versionEpochSeconds").map(String::toLong))
    stateFile.set(rootProject.layout.projectDirectory.file(".gradle/komgarot-release-version.properties"))
    outputFile.set(releaseVersionFile)
}

fun releaseVersionStateProvider(): Provider<ReleaseVersionState> =
    generateReleaseVersion.flatMap { it.outputFile }.map { file ->
        ReleaseVersioning.readState(file.asFile)
            ?: error("Release version state was not generated at ${file.asFile.absolutePath}")
    }

val releaseVersionName = releaseVersionStateProvider().map { it.versionName }
val releaseVersionCode = releaseVersionStateProvider().map { it.versionCode }
val releaseApkFileName = releaseVersionStateProvider().map { state ->
    ReleaseVersioning.apkFileName(
        versionName = state.versionName,
        versionCode = state.versionCode,
    )
}

android {
    namespace = "fail.tiger.komgarot"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "fail.tiger.komgarot"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = komgarotVersionBase.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(releaseVersionCode)
            output.versionName.set(releaseVersionName)
            output.outputFileName.set(releaseApkFileName)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.zoomable)
    implementation(libs.biometric)
    implementation(libs.appcompat)
    implementation(libs.material.motion.compose.core)
    implementation(libs.security.crypto)
}
