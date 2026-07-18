package fail.tiger.komgarot.build

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFlavorStructureTest {
    private val buildFile = File("build.gradle.kts").readText()

    @Test
    fun appDefinesFullAndLiteFlavorsWithSharedApplicationId() {
        assertTrue(buildFile.contains("applicationId = \"fail.tiger.komgarot\""))
        assertTrue(buildFile.contains("flavorDimensions += \"edition\""))
        assertTrue(buildFile.contains("create(\"full\")"))
        assertTrue(buildFile.contains("create(\"lite\")"))
        assertTrue(buildFile.contains("dimension = \"edition\""))
        assertTrue(!buildFile.contains("applicationIdSuffix"))
    }

    @Test
    fun flavorsExposeAiTranslationAvailabilityBuildConfig() {
        assertTrue(buildFile.contains("buildConfigField(\"boolean\", \"AI_TRANSLATION_AVAILABLE\", \"true\")"))
        assertTrue(buildFile.contains("buildConfigField(\"boolean\", \"AI_TRANSLATION_AVAILABLE\", \"false\")"))
        assertTrue(buildFile.contains("buildFeatures"))
        assertTrue(buildFile.contains("buildConfig = true"))
    }

    @Test
    fun onnxRuntimeDependencyIsFullOnly() {
        assertTrue(buildFile.contains("add(\"fullImplementation\", libs.onnxruntime.android)"))
        assertTrue(!buildFile.contains("implementation(libs.onnxruntime.android)"))
        assertTrue(buildFile.contains("ReleaseVersioning.apkFileName("))
        assertTrue(buildFile.contains("val edition = variant.flavorName"))
        assertTrue(buildFile.contains("edition = edition"))
        assertFalse(buildFile.contains("edition = variant.flavorName,"))
    }

    @Test
    fun releaseBuildsPublishFullAndLiteArtifactsThroughOneFlatDirectoryTask() {
        assertTrue(buildFile.contains("import org.gradle.api.file.DuplicatesStrategy"))
        assertTrue(buildFile.contains("import org.gradle.api.tasks.Copy"))
        assertTrue(buildFile.contains("import org.gradle.api.tasks.Delete"))
        assertTrue(buildFile.contains("val variantName = variant.name"))
        assertTrue(buildFile.contains("val apkOutputDirectory = \"outputs/apk/${'$'}edition/release\""))
        assertTrue(buildFile.contains("val projectFlavorReleaseDirectory = layout.projectDirectory.dir(\"${'$'}edition/release\")"))
        assertTrue(buildFile.contains("val releaseArtifactSuffix = if (edition == \"lite\") \"_lite\" else \"\""))
        assertTrue(buildFile.contains("val outputMetadataFileName = \"output-metadata${'$'}releaseArtifactSuffix.json\""))
        assertTrue(buildFile.contains("val baselineProfilesDirectoryName = \"baselineProfiles${'$'}releaseArtifactSuffix\""))
        assertTrue(buildFile.contains("val releaseDirectory = layout.projectDirectory.dir(\"release\")"))
        assertTrue(buildFile.contains("tasks.register<Copy>(\"copyReleaseArtifactsToProjectRelease\")"))
        assertFalse(buildFile.contains("tasks.register<Copy>(\"copy${'$'}{capitalizedVariantName}ArtifactsToProjectRelease\")"))
        assertTrue(buildFile.contains("val releaseApkIncludePattern = if (edition == \"lite\") \"Komgarot_lite_*.apk\" else \"Komgarot_*.apk\""))
        assertTrue(buildFile.contains("val releaseApkExcludePattern = if (edition == \"lite\") null else \"Komgarot_lite_*.apk\""))
        assertTrue(buildFile.contains("val deleteExistingProjectReleaseArtifacts = tasks.register<Delete>(\"delete${'$'}{capitalizedVariantName}ExistingProjectReleaseArtifacts\")"))
        assertTrue(buildFile.contains("delete(releaseDirectory.asFileTree.matching"))
        assertTrue(buildFile.contains("include(releaseApkIncludePattern)"))
        assertTrue(buildFile.contains("exclude(it)"))
        assertTrue(buildFile.contains("delete(releaseDirectory.file(outputMetadataFileName))"))
        assertTrue(buildFile.contains("delete(releaseDirectory.dir(baselineProfilesDirectoryName))"))
        assertTrue(buildFile.contains("delete(releaseDirectory.dir(requireNotNull(edition)))"))
        assertTrue(buildFile.contains("from(layout.buildDirectory.dir(apkOutputDirectory))"))
        assertTrue(buildFile.contains("from(projectFlavorReleaseDirectory)"))
        assertTrue(buildFile.contains("include(\"*.apk\")"))
        assertTrue(buildFile.contains("include(\"output-metadata.json\")"))
        assertTrue(buildFile.contains("rename { outputMetadataFileName }"))
        assertTrue(buildFile.contains("from(layout.buildDirectory.dir(\"${'$'}apkOutputDirectory/baselineProfiles\"))"))
        assertTrue(buildFile.contains("into(baselineProfilesDirectoryName)"))
        assertTrue(buildFile.contains("into(releaseDirectory)"))
        assertTrue(buildFile.contains("duplicatesStrategy = DuplicatesStrategy.INCLUDE"))
        assertTrue(buildFile.contains("tasks.register<Delete>(\"delete${'$'}{capitalizedVariantName}ProjectReleaseDirectory\")"))
        assertTrue(buildFile.contains("delete(projectFlavorReleaseDirectory)"))
        assertTrue(buildFile.contains("tasks.matching { it.name == \"assemble${'$'}capitalizedVariantName\" }.configureEach"))
        assertTrue(buildFile.contains("tasks.matching { it.name == \"package${'$'}capitalizedVariantName\" }.configureEach"))
        assertTrue(buildFile.contains("finalizedBy(copyReleaseArtifactsToProjectRelease)"))
        assertTrue(buildFile.contains("copyReleaseArtifactsToProjectRelease.configure"))
        assertTrue(buildFile.contains("dependsOn(deleteExistingProjectReleaseArtifacts)"))
        assertTrue(buildFile.contains("finalizedBy(deleteProjectFlavorReleaseDirectory)"))
    }
}
