package fail.tiger.komgarot.build

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.gradle.testfixtures.ProjectBuilder

class ReleaseVersioningTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `first release appends generated number after user controlled base version`() {
        val state = ReleaseVersioning.nextState(
            baseVersion = "v1.0.0",
            previousState = null,
            epochSeconds = 1_800_000_000L,
        )

        assertEquals("v1.0.0", state.baseVersion)
        assertEquals(1, state.generatedVersion)
        assertEquals("v1.0.0.1", state.versionName)
        assertEquals(1_800_000_000, state.versionCode)
    }

    @Test
    fun `same second release increments code and patch`() {
        val firstState = ReleaseVersioning.nextState(
            baseVersion = "v1.0.0",
            previousState = null,
            epochSeconds = 1_800_000_000L,
        )

        val secondState = ReleaseVersioning.nextState(
            baseVersion = "v1.0.0",
            previousState = firstState,
            epochSeconds = 1_800_000_000L,
        )

        assertEquals(2, secondState.generatedVersion)
        assertEquals("v1.0.0.2", secondState.versionName)
        assertEquals(1_800_000_001, secondState.versionCode)
    }

    @Test
    fun `base version change starts a new patch sequence`() {
        val previousState = ReleaseVersionState(
            baseVersion = "v1.0.0",
            generatedVersion = 42,
            versionCode = 1_800_000_100,
        )

        val state = ReleaseVersioning.nextState(
            baseVersion = "v1.1.0",
            previousState = previousState,
            epochSeconds = 1_800_000_200L,
        )

        assertEquals("v1.1.0", state.baseVersion)
        assertEquals(1, state.generatedVersion)
        assertEquals("v1.1.0.1", state.versionName)
        assertEquals(1_800_000_200, state.versionCode)
    }

    @Test
    fun `clock rollback still increments version code`() {
        val previousState = ReleaseVersionState(
            baseVersion = "v1.0.0",
            generatedVersion = 3,
            versionCode = 1_800_000_100,
        )

        val state = ReleaseVersioning.nextState(
            baseVersion = "v1.0.0",
            previousState = previousState,
            epochSeconds = 1_799_999_000L,
        )

        assertEquals(4, state.generatedVersion)
        assertEquals("v1.0.0.4", state.versionName)
        assertEquals(1_800_000_101, state.versionCode)
    }

    @Test
    fun `release apk file name includes version name and code`() {
        assertEquals(
            "Komgarot_v1.0.0.3123_1800000000.apk",
            ReleaseVersioning.apkFileName(
                versionName = "v1.0.0.3123",
                versionCode = 1_800_000_000,
            ),
        )
    }

    @Test
    fun `abi release apk file name keeps shared version and adds abi`() {
        assertEquals(
            "Komgarot_v1.0.0.3123_1800000000_arm64-v8a.apk",
            ReleaseVersioning.apkFileName(
                versionName = "v1.0.0.3123",
                versionCode = 1_800_000_000,
                abi = "arm64-v8a",
            ),
        )
    }

    @Test
    fun `lite abi release apk file name keeps shared version and adds lite marker`() {
        assertEquals(
            "Komgarot_lite_v1.1.0.81_1781759271_arm64-v8a.apk",
            ReleaseVersioning.apkFileName(
                versionName = "v1.1.0.81",
                versionCode = 1_781_759_271,
                abi = "arm64-v8a",
                edition = "lite",
            ),
        )
    }

    @Test
    fun `state can be persisted and loaded from properties file`() {
        val file = temporaryFolder.newFile("version.properties")
        val state = ReleaseVersionState(
            baseVersion = "v1.2.0",
            generatedVersion = 3123,
            versionCode = 1_800_003_123,
        )

        ReleaseVersioning.writeState(file, state)

        assertEquals(state, ReleaseVersioning.readState(file))
    }

    @Test
    fun `generate release version task can use execution time`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder("project"))
            .build()
        val stateFile = temporaryFolder.newFile("task-state.properties")
        val outputFile = temporaryFolder.newFile("task-output.properties")
        stateFile.delete()
        outputFile.delete()
        val task = project.tasks.register(
            "generateReleaseVersion",
            GenerateReleaseVersionTask::class.java,
        ) {
            baseVersion.set("v2.0.0")
            this.stateFile.set(stateFile)
            this.outputFile.set(outputFile)
        }.get()

        task.generate()

        val state = ReleaseVersioning.readState(outputFile)
        requireNotNull(state)
        assertEquals("v2.0.0", state.baseVersion)
        assertEquals(1, state.generatedVersion)
        assertEquals("v2.0.0.1", state.versionName)
    }
}
