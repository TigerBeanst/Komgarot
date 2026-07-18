plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "fail.tiger.komgarot.baselineprofile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = 30
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("full") { dimension = "edition" }
        create("lite") { dimension = "edition" }
    }

}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
