import fail.tiger.komgarot.build.GenerateReleaseVersionTask
import fail.tiger.komgarot.build.ReleaseVersioning
import fail.tiger.komgarot.build.ReleaseVersionState
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
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

    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "AI_TRANSLATION_AVAILABLE", "true")
        }
        create("lite") {
            dimension = "edition"
            buildConfigField("boolean", "AI_TRANSLATION_AVAILABLE", "false")
        }
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
        buildConfig = true
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }
}

val releaseDirectory = layout.projectDirectory.dir("release")
val copyReleaseArtifactsToProjectRelease = tasks.register<Copy>("copyReleaseArtifactsToProjectRelease") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    into(releaseDirectory)
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val variantName = variant.name
        val capitalizedVariantName = variantName.replaceFirstChar { it.titlecase() }
        val edition = variant.flavorName
        val apkOutputDirectory = "outputs/apk/$edition/release"
        val projectFlavorReleaseDirectory = layout.projectDirectory.dir("$edition/release")
        val releaseArtifactSuffix = if (edition == "lite") "_lite" else ""
        val outputMetadataFileName = "output-metadata$releaseArtifactSuffix.json"
        val baselineProfilesDirectoryName = "baselineProfiles$releaseArtifactSuffix"
        val releaseApkIncludePattern = if (edition == "lite") "Komgarot_lite_*.apk" else "Komgarot_*.apk"
        val releaseApkExcludePattern = if (edition == "lite") null else "Komgarot_lite_*.apk"
        val deleteExistingProjectReleaseArtifacts = tasks.register<Delete>("delete${capitalizedVariantName}ExistingProjectReleaseArtifacts") {
            delete(releaseDirectory.asFileTree.matching {
                include(releaseApkIncludePattern)
                releaseApkExcludePattern?.let { exclude(it) }
            })
            delete(releaseDirectory.file(outputMetadataFileName))
            delete(releaseDirectory.dir(baselineProfilesDirectoryName))
            delete(releaseDirectory.dir(requireNotNull(edition)))
        }
        copyReleaseArtifactsToProjectRelease.configure {
            from(projectFlavorReleaseDirectory) {
                include("*.apk")
            }
            from(layout.buildDirectory.dir(apkOutputDirectory)) {
                include("*.apk")
            }

            from(projectFlavorReleaseDirectory) {
                include("output-metadata.json")
                rename { outputMetadataFileName }
            }
            from(layout.buildDirectory.dir(apkOutputDirectory)) {
                include("output-metadata.json")
                rename { outputMetadataFileName }
            }

            from(projectFlavorReleaseDirectory.dir("baselineProfiles")) {
                into(baselineProfilesDirectoryName)
            }
            from(layout.buildDirectory.dir("$apkOutputDirectory/baselineProfiles")) {
                into(baselineProfilesDirectoryName)
            }
        }
        val deleteProjectFlavorReleaseDirectory = tasks.register<Delete>("delete${capitalizedVariantName}ProjectReleaseDirectory") {
            delete(projectFlavorReleaseDirectory)
        }
        tasks.matching { it.name == "assemble$capitalizedVariantName" }.configureEach {
            finalizedBy(copyReleaseArtifactsToProjectRelease)
        }
        tasks.matching { it.name == "package$capitalizedVariantName" }.configureEach {
            finalizedBy(copyReleaseArtifactsToProjectRelease)
        }
        copyReleaseArtifactsToProjectRelease.configure {
            dependsOn(deleteExistingProjectReleaseArtifacts)
            finalizedBy(deleteProjectFlavorReleaseDirectory)
        }
        variant.outputs.forEach { output ->
            output.versionCode.set(releaseVersionCode)
            output.versionName.set(releaseVersionName)
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            output.outputFileName.set(
                releaseVersionStateProvider().map { state ->
                    ReleaseVersioning.apkFileName(
                        versionName = state.versionName,
                        versionCode = state.versionCode,
                        abi = abi,
                        edition = edition,
                    )
                }
            )
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
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    add("fullImplementation", libs.onnxruntime.android)
}
