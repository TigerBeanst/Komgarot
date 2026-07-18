package fail.tiger.komgarot

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceBaselineStructureTest {
    @Test
    fun debugBuildInstallsStrictModeDiagnostics() {
        val appSource = File("src/main/java/fail/tiger/komgarot/KomgarotApp.kt").readText()
        val diagnosticsSource = File("src/main/java/fail/tiger/komgarot/DebugPerformanceDiagnostics.kt")

        assertTrue(diagnosticsSource.isFile)
        assertTrue(appSource.contains("DebugPerformanceDiagnostics.install()"))
        assertTrue(diagnosticsSource.readText().contains("StrictMode.ThreadPolicy.Builder()"))
        assertTrue(diagnosticsSource.readText().contains("StrictMode.VmPolicy.Builder()"))
    }

    @Test
    fun projectDefinesMacrobenchmarkAndBaselineProfileModule() {
        val settings = File("../settings.gradle.kts").readText()
        val benchmarkBuild = File("../baselineprofile/build.gradle.kts")
        val benchmarkSource = File("../baselineprofile/src/main/java/fail/tiger/komgarot/baselineprofile/KomgarotBenchmarks.kt")

        assertTrue(settings.contains("include(\":baselineprofile\")"))
        assertTrue(benchmarkBuild.isFile)
        assertTrue(benchmarkSource.isFile)
        assertTrue(benchmarkSource.readText().contains("StartupTimingMetric()"))
        assertTrue(benchmarkSource.readText().contains("FrameTimingMetric()"))
        assertTrue(benchmarkSource.readText().contains("BaselineProfileRule"))
    }
}
