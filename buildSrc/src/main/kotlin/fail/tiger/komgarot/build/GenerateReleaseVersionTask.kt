package fail.tiger.komgarot.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateReleaseVersionTask : DefaultTask() {
    @get:Input
    abstract val baseVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val epochSeconds: Property<Long>

    @get:Internal
    abstract val stateFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun generate() {
        val statePath = stateFile.get().asFile
        val nextState = ReleaseVersioning.nextState(
            baseVersion = baseVersion.get(),
            previousState = ReleaseVersioning.readState(statePath),
            epochSeconds = epochSeconds.orNull ?: System.currentTimeMillis() / 1_000,
        )

        ReleaseVersioning.writeState(statePath, nextState)
        ReleaseVersioning.writeState(outputFile.get().asFile, nextState)
    }
}
