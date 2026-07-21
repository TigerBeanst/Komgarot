package fail.tiger.komgarot.data.repository

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DeviceProfile(
    val ramGb: Float,
    val cpuCores: Int,
    val supportedAbis: List<String>
)

enum class AiLocalModelTier {
    LOW,
    BALANCED,
    HIGH
}

data class AiLocalModelPlan(
    val tier: AiLocalModelTier,
    val detRepoId: String
)

data class AiLocalModelInstallSummary(
    val plan: AiLocalModelPlan,
    val revision: String,
    val downloadedFiles: List<File>
)

class AiLocalModelRepository(
    private val filesDir: File,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val rootDir = File(filesDir, "ai_local_models")

    fun modelFile(repoId: String, revision: String, fileName: String): File =
        File(modelDir(repoId, revision), fileName.trim())

    fun installedFiles(repoId: String, revision: String): List<File> =
        modelDir(repoId, revision)
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile }
            .orEmpty()

    fun isPlanInstalled(plan: AiLocalModelPlan, revision: String): Boolean =
        installedFiles(plan.detRepoId, revision).hasOnnxModel()

    fun deletePlan(plan: AiLocalModelPlan, revision: String): Boolean {
        val dir = modelDir(plan.detRepoId, revision)
        return dir.exists() && dir.deleteRecursively()
    }

    suspend fun downloadPlan(plan: AiLocalModelPlan, revision: String): Result<AiLocalModelInstallSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val downloaded = mutableListOf<File>()
                downloaded += downloadRepo(plan.detRepoId, revision)
                AiLocalModelInstallSummary(plan = plan, revision = revision, downloadedFiles = downloaded)
            }
        }

    private fun downloadRepo(repoId: String, revision: String): List<File> {
        val files = resolveDownloadableFiles(repoId, revision)
        require(files.isNotEmpty()) { "No downloadable model file was found for $repoId@$revision" }
        val dir = modelDir(repoId, revision).apply { mkdirs() }
        return files.map { fileName ->
            val target = File(dir, fileName.substringAfterLast('/'))
            val request = Request.Builder()
                .url(huggingFaceModelFileUrl(repoId, revision, fileName))
                .build()
            httpClient.newCall(request).execute().use { response ->
                require(response.isSuccessful) { "Download failed for $repoId/$fileName: ${response.code} ${response.message}" }
                val body = response.body ?: error("Empty body for $repoId/$fileName")
                target.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            target
        }
    }

    private fun resolveDownloadableFiles(repoId: String, revision: String): List<String> {
        val modelInfo = fetchModelInfo(repoId, revision)
        val siblings = modelInfo.getAsJsonArray("siblings")
            ?.mapNotNull { element ->
                element.asJsonObject.get("rfilename")?.asString
            }
            .orEmpty()
        val preferred = selectDownloadableModelFiles(siblings)
        return when {
            preferred.isNotEmpty() -> preferred.distinct()
            siblings.any { it.endsWith(".onnx", ignoreCase = true) } -> siblings.filter { it.endsWith(".onnx", ignoreCase = true) }.distinct()
            else -> listOf("model.onnx")
        }
    }

    private fun fetchModelInfo(repoId: String, revision: String): JsonObject {
        val revisionUrl = "https://huggingface.co/api/models/${repoId.trim()}/revision/${urlEncodePathSegment(revision.trim().ifBlank { "main" })}"
        val fallbackUrl = "https://huggingface.co/api/models/${repoId.trim()}"
        return requestJsonObject(revisionUrl) ?: requestJsonObject(fallbackUrl)
        ?: error("Unable to load Hugging Face model info for $repoId@$revision")
    }

    private fun requestJsonObject(url: String): JsonObject? {
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            JsonParser.parseString(body).asJsonObject
        }
    }

    private fun modelDir(repoId: String, revision: String): File =
        File(rootDir, "${sanitizePathSegment(repoId)}__${sanitizePathSegment(revision)}")

    private fun sanitizePathSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun List<File>.hasOnnxModel(): Boolean =
    any { it.isFile && it.name.endsWith(".onnx", ignoreCase = true) }

internal fun selectDownloadableModelFiles(siblings: List<String>): List<String> =
    siblings.filter { name ->
        name.endsWith(".onnx", ignoreCase = true)
    }.distinct()

fun deviceProfile(context: Context): DeviceProfile {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memoryInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return DeviceProfile(
        ramGb = memoryInfo.totalMem / (1024f * 1024f * 1024f),
        cpuCores = Runtime.getRuntime().availableProcessors(),
        supportedAbis = android.os.Build.SUPPORTED_ABIS.toList()
    )
}

fun recommendAiLocalModelTier(profile: DeviceProfile): AiLocalModelTier {
    val arm64 = profile.supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }
    return when {
        arm64 && profile.ramGb >= 10f && profile.cpuCores >= 8 -> AiLocalModelTier.HIGH
        arm64 && profile.ramGb >= 6f && profile.cpuCores >= 8 -> AiLocalModelTier.BALANCED
        else -> AiLocalModelTier.LOW
    }
}

fun defaultAiLocalModelPlan(
    collectionId: String,
    revision: String,
    tier: AiLocalModelTier
): AiLocalModelPlan {
    val namespace = collectionId.substringBefore('/')
    val detSize = if (tier == AiLocalModelTier.LOW) "tiny" else "small"
    return AiLocalModelPlan(
        tier = tier,
        detRepoId = "$namespace/PP-OCRv6_${detSize}_det_onnx"
    )
}

internal fun paddleDetectorInputMaxSide(
    tier: AiLocalModelTier,
    sourceTextProfile: AiSourceTextProfile
): Int = when (sourceTextProfile) {
    AiSourceTextProfile.JAPANESE_MANGA -> 1600
    AiSourceTextProfile.HORIZONTAL_COMIC,
    AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON -> when (tier) {
        AiLocalModelTier.LOW -> 1280
        AiLocalModelTier.BALANCED,
        AiLocalModelTier.HIGH -> 1536
    }
    AiSourceTextProfile.AUTO -> when (tier) {
        AiLocalModelTier.LOW -> 1280
        AiLocalModelTier.BALANCED -> 1536
        AiLocalModelTier.HIGH -> 1600
    }
}

fun huggingFaceCollectionApiUrl(collectionId: String): String =
    "https://huggingface.co/api/collections/${collectionId.trim()}"

fun huggingFaceModelFileUrl(repoId: String, revision: String, fileName: String): String {
    val encodedRevision = urlEncodePathSegment(revision.trim().ifBlank { "main" })
    return "https://huggingface.co/${repoId.trim()}/resolve/$encodedRevision/${fileName.trim()}?download=1"
}

private fun urlEncodePathSegment(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name())
