package fail.tiger.komgarot.build

import java.io.File
import java.util.Properties

data class ReleaseVersionState(
    val baseVersion: String,
    val generatedVersion: Int,
    val versionCode: Int,
) {
    val versionName: String = "$baseVersion.$generatedVersion"
}

object ReleaseVersioning {
    private const val BASE_VERSION_KEY = "baseVersion"
    private const val GENERATED_VERSION_KEY = "generatedVersion"
    private const val VERSION_CODE_KEY = "versionCode"

    fun nextState(
        baseVersion: String,
        previousState: ReleaseVersionState?,
        epochSeconds: Long,
    ): ReleaseVersionState {
        val nextGeneratedVersion = when {
            previousState?.baseVersion == baseVersion -> previousState.generatedVersion + 1
            else -> 1
        }
        val timeCode = epochSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val previousCode = previousState?.versionCode ?: 0
        val nextCode = maxOf(timeCode, previousCode + 1)

        return ReleaseVersionState(
            baseVersion = baseVersion,
            generatedVersion = nextGeneratedVersion,
            versionCode = nextCode,
        )
    }

    fun apkFileName(versionName: String, versionCode: Int, abi: String? = null): String {
        val abiSuffix = abi?.takeIf { it.isNotBlank() }?.let { "_${it}" }.orEmpty()
        return "Komgarot_${versionName}_${versionCode}${abiSuffix}.apk"
    }

    fun readState(file: File): ReleaseVersionState? {
        if (!file.exists()) return null

        val properties = Properties()
        file.inputStream().use(properties::load)

        val baseVersion = properties.getProperty(BASE_VERSION_KEY) ?: return null
        val generatedVersion = properties.getProperty(GENERATED_VERSION_KEY)?.toIntOrNull()
            ?: properties.getProperty("patchVersion")?.toIntOrNull()
            ?: return null
        val versionCode = properties.getProperty(VERSION_CODE_KEY)?.toIntOrNull() ?: return null

        return ReleaseVersionState(
            baseVersion = baseVersion,
            generatedVersion = generatedVersion,
            versionCode = versionCode,
        )
    }

    fun writeState(file: File, state: ReleaseVersionState) {
        file.parentFile?.mkdirs()

        val properties = Properties().apply {
            setProperty(BASE_VERSION_KEY, state.baseVersion)
            setProperty(GENERATED_VERSION_KEY, state.generatedVersion.toString())
            setProperty(VERSION_CODE_KEY, state.versionCode.toString())
        }
        file.outputStream().use { output ->
            properties.store(output, "Komgarot release version state")
        }
    }
}
