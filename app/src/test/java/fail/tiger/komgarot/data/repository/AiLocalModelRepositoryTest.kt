package fail.tiger.komgarot.data.repository

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLocalModelRepositoryTest {
    @Test
    fun recommendTierUsesRamAbiAndCpuSignals() {
        assertEquals(
            AiLocalModelTier.LOW,
            recommendAiLocalModelTier(DeviceProfile(ramGb = 4f, cpuCores = 6, supportedAbis = listOf("arm64-v8a")))
        )
        assertEquals(
            AiLocalModelTier.BALANCED,
            recommendAiLocalModelTier(DeviceProfile(ramGb = 8f, cpuCores = 8, supportedAbis = listOf("arm64-v8a")))
        )
        assertEquals(
            AiLocalModelTier.HIGH,
            recommendAiLocalModelTier(DeviceProfile(ramGb = 12f, cpuCores = 8, supportedAbis = listOf("arm64-v8a")))
        )
    }

    @Test
    fun defaultPlanPrefersTinyForLowTierAndSmallForHigherTiers() {
        val lowPlan = defaultAiLocalModelPlan(
            collectionId = "PaddlePaddle/pp-ocrv6",
            revision = "main",
            tier = AiLocalModelTier.LOW
        )
        val highPlan = defaultAiLocalModelPlan(
            collectionId = "PaddlePaddle/pp-ocrv6",
            revision = "main",
            tier = AiLocalModelTier.HIGH
        )

        assertTrue(lowPlan.detRepoId.contains("tiny_det_onnx"))
        assertTrue(highPlan.detRepoId.contains("small_det_onnx"))
    }

    @Test
    fun huggingFaceUrlsUseCollectionAndRevision() {
        assertEquals(
            "https://huggingface.co/api/collections/PaddlePaddle/pp-ocrv6",
            huggingFaceCollectionApiUrl("PaddlePaddle/pp-ocrv6")
        )
        assertEquals(
            "https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det_onnx/resolve/refs%2Ftags%2Fv1.0.0/model.onnx?download=1",
            huggingFaceModelFileUrl(
                repoId = "PaddlePaddle/PP-OCRv6_tiny_det_onnx",
                revision = "refs/tags/v1.0.0",
                fileName = "model.onnx"
            )
        )
    }

    @Test
    fun installedModelLayoutUsesRepoAndRevisionDirectories() {
        val repo = AiLocalModelRepository(File("/tmp/models"))

        val file = repo.modelFile(
            repoId = "PaddlePaddle/PP-OCRv6_tiny_det_onnx",
            revision = "refs/tags/v1.0.0",
            fileName = "model.onnx"
        )

        assertTrue(file.path.contains("PaddlePaddle_PP-OCRv6_tiny_det_onnx"))
        assertTrue(file.path.contains("refs_tags_v1.0.0"))
        assertTrue(file.path.endsWith("model.onnx"))
    }

    @Test
    fun deletePlanRemovesDetectionModelDirectory() {
        val root = Files.createTempDirectory("ai-local-models-test").toFile()
        val repo = AiLocalModelRepository(root)
        val plan = defaultAiLocalModelPlan(
            collectionId = "PaddlePaddle/pp-ocrv6",
            revision = "main",
            tier = AiLocalModelTier.LOW
        )
        repo.modelFile(plan.detRepoId, "main", "model.onnx").apply {
            parentFile?.mkdirs()
            writeText("det")
        }

        assertTrue(repo.isPlanInstalled(plan, "main"))

        assertTrue(repo.deletePlan(plan, "main"))
        assertFalse(repo.isPlanInstalled(plan, "main"))
    }

    @Test
    fun installedPlanRequiresOnlyDetectionOnnx() {
        val root = Files.createTempDirectory("ai-local-models-missing-dict-test").toFile()
        val repo = AiLocalModelRepository(root)
        val plan = defaultAiLocalModelPlan(
            collectionId = "PaddlePaddle/pp-ocrv6",
            revision = "main",
            tier = AiLocalModelTier.LOW
        )
        repo.modelFile(plan.detRepoId, "main", "model.onnx").apply {
            parentFile?.mkdirs()
            writeText("det")
        }

        assertTrue(repo.isPlanInstalled(plan, "main"))
    }

    @Test
    fun downloadableFilesIncludeOnlyOnnxModelFiles() {
        val siblings = listOf("README.md", "inference.onnx", "inference.yml")

        val selected = selectDownloadableModelFiles(siblings)

        assertEquals(listOf("inference.onnx"), selected)
    }
}
