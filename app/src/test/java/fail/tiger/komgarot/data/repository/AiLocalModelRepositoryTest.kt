package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslationMode
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

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
    fun paddleDetectionInputUsesFineResolutionForMangaAndHighTier() {
        assertEquals(
            1600,
            paddleDetectorInputMaxSide(AiLocalModelTier.LOW, AiSourceTextProfile.JAPANESE_MANGA)
        )
        assertEquals(
            2048,
            paddleDetectorInputMaxSide(
                AiLocalModelTier.LOW,
                AiSourceTextProfile.JAPANESE_MANGA,
                AiTranslationMode.HIGH_ACCURACY
            )
        )
        assertEquals(
            1600,
            paddleDetectorInputMaxSide(AiLocalModelTier.HIGH, AiSourceTextProfile.AUTO)
        )
        assertEquals(
            1280,
            paddleDetectorInputMaxSide(AiLocalModelTier.LOW, AiSourceTextProfile.AUTO)
        )
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
        val model = repo.modelFile(plan.detRepoId, "main", "model.onnx")
        java.io.File(model.parentFile, ".manifest").writeText(
            "${model.name}\t${model.length()}\t${fileSha256Hex(model)}\n"
        )

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

        assertFalse(repo.isPlanInstalled(plan, "main"))
        val model = repo.modelFile(plan.detRepoId, "main", "model.onnx")
        java.io.File(model.parentFile, ".manifest").writeText(
            "${model.name}\t${model.length()}\t${fileSha256Hex(model)}\n"
        )
        assertTrue(repo.isPlanInstalled(plan, "main"))
        model.writeText("changed")
        assertFalse(repo.isPlanInstalled(plan, "main"))
    }

    @Test
    fun downloadableFilesIncludeOnlyOnnxModelFiles() {
        val siblings = listOf("README.md", "inference.onnx", "inference.yml")

        val selected = selectDownloadableModelFiles(siblings)

        assertEquals(listOf("inference.onnx"), selected)
    }

    @Test
    fun mangaLensAssetUsesPinnedOnnxAndChecksum() {
        assertEquals("mangalens.onnx", MANGA_LENS_MODEL_ASSET.fileName)
        assertEquals(
            "https://www.modelscope.cn/models/hgmzhn/manga-translator-ui/resolve/master/mangalens.onnx",
            MANGA_LENS_MODEL_ASSET.url
        )
        assertEquals(
            "257b4f46917d1f012a1f05179ca5aea2136eca9d8af69702090c23c2f482938a",
            MANGA_LENS_MODEL_ASSET.sha256
        )
    }

    @Test
    fun mangaLensInstallStateRequiresChecksumAndDeleteRemovesOnlyItsDirectory() {
        val root = Files.createTempDirectory("ai-mangalens-model-test").toFile()
        val repo = AiLocalModelRepository(root)
        val paddlePlan = defaultAiLocalModelPlan(
            collectionId = "PaddlePaddle/pp-ocrv6",
            revision = "main",
            tier = AiLocalModelTier.LOW
        )
        val paddleFile = repo.modelFile(paddlePlan.detRepoId, "main", "model.onnx").apply {
            parentFile?.mkdirs()
            writeText("paddle")
        }
        repo.mangaLensModelFile().apply {
            parentFile?.mkdirs()
            writeText("invalid")
        }

        assertFalse(repo.isMangaLensInstalled())
        assertTrue(repo.deleteMangaLens())
        assertTrue(paddleFile.isFile)
    }

    @Test
    fun sha256UsesFileBytes() {
        val file = Files.createTempFile("ai-model-sha", ".bin").toFile().apply {
            writeText("abc")
        }

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            fileSha256Hex(file)
        )
    }

    @Test
    fun failedPlanDownloadKeepsPreviouslyInstalledModel() = runBlocking {
        val root = Files.createTempDirectory("ai-model-download-test").toFile()
        val plan = defaultAiLocalModelPlan(
            collectionId = "PaddlePaddle/pp-ocrv6",
            revision = "main",
            tier = AiLocalModelTier.LOW
        )
        val existingFirst = File(root, "ai_local_models/PaddlePaddle_PP-OCRv6_tiny_det_onnx__main/first.onnx")
        val existingSecond = File(existingFirst.parentFile, "second.onnx")
        existingFirst.parentFile?.mkdirs()
        existingFirst.writeText("old-first")
        existingSecond.writeText("old-second")
        File(existingFirst.parentFile, ".manifest").writeText(
            listOf(existingFirst, existingSecond).joinToString("\n") {
                "${it.name}\t${it.length()}\t${fileSha256Hex(it)}"
            } + "\n"
        )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("/api/models/")) {
                    response(chain, "{\"siblings\":[{\"rfilename\":\"first.onnx\"},{\"rfilename\":\"second.onnx\"}]}", "application/json")
                } else if (url.endsWith("/first.onnx?download=1")) {
                    response(chain, "new-first", "application/octet-stream")
                } else {
                    throw IOException("simulated second file failure")
                }
            }
            .build()
        val repo = AiLocalModelRepository(root, client)

        assertTrue(repo.isPlanInstalled(plan, "main"))
        assertTrue(repo.downloadPlan(plan, "main").isFailure)
        assertTrue(repo.isPlanInstalled(plan, "main"))
        assertEquals("old-first", existingFirst.readText())
        assertEquals("old-second", existingSecond.readText())
    }

    @Test
    fun remoteChecksumMismatchDoesNotInstallModel() = runBlocking {
        val root = Files.createTempDirectory("ai-model-checksum-test").toFile()
        val plan = defaultAiLocalModelPlan(
            collectionId = "PaddlePaddle/pp-ocrv6",
            revision = "main",
            tier = AiLocalModelTier.LOW
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("/api/models/")) {
                    response(
                        chain,
                        "{\"siblings\":[{\"rfilename\":\"model.onnx\",\"lfs\":{\"oid\":\"${"00".repeat(32)}\"}}]}",
                        "application/json"
                    )
                } else {
                    response(chain, "unexpected", "application/octet-stream")
                }
            }
            .build()

        val repo = AiLocalModelRepository(root, client)

        assertTrue(repo.downloadPlan(plan, "main").isFailure)
        assertFalse(repo.isPlanInstalled(plan, "main"))
    }

    private fun response(
        chain: okhttp3.Interceptor.Chain,
        body: String,
        mediaType: String
    ): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody(mediaType.toMediaType()))
        .build()
}
