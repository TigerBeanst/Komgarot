package fail.tiger.komgarot.data.repository

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslationMode
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

data class AiMangaLensModelAsset(
    val fileName: String,
    val url: String,
    val sha256: String,
    val license: String
)

data class AiMangaLensInstallSummary(
    val asset: AiMangaLensModelAsset,
    val installedFile: File
)

internal val MANGA_LENS_MODEL_ASSET = AiMangaLensModelAsset(
    fileName = "mangalens.onnx",
    url = "https://www.modelscope.cn/models/hgmzhn/manga-translator-ui/resolve/master/mangalens.onnx",
    sha256 = "257b4f46917d1f012a1f05179ca5aea2136eca9d8af69702090c23c2f482938a",
    license = "CC-BY-NC-4.0"
)

class AiLocalModelRepository(
    private val filesDir: File,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val rootDir = File(filesDir, "ai_local_models")
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()
    @Volatile
    private var mangaLensVerification: MangaLensVerification? = null

    fun modelFile(repoId: String, revision: String, fileName: String): File =
        File(modelDir(repoId, revision), fileName.trim())

    fun installedFiles(repoId: String, revision: String): List<File> =
        modelDir(repoId, revision)
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile }
            .orEmpty()

    fun isPlanInstalled(plan: AiLocalModelPlan, revision: String): Boolean {
        val dir = modelDir(plan.detRepoId, revision)
        val manifest = readModelManifest(dir) ?: return false
        if (manifest.none { it.fileName.endsWith(".onnx", ignoreCase = true) }) return false
        return manifest.all { entry ->
            val file = File(dir, entry.fileName)
            file.name == entry.fileName &&
                file.isFile &&
                file.length() == entry.length &&
                fileSha256Hex(file).equals(entry.sha256, ignoreCase = true)
        }
    }

    fun deletePlan(plan: AiLocalModelPlan, revision: String): Boolean {
        val dir = modelDir(plan.detRepoId, revision)
        return dir.exists() && dir.deleteRecursively()
    }

    fun mangaLensModelFile(): File = File(mangaLensDir(), MANGA_LENS_MODEL_ASSET.fileName)

    @Synchronized
    fun isMangaLensInstalled(): Boolean {
        val file = mangaLensModelFile()
        if (!file.isFile) {
            mangaLensVerification = null
            return false
        }
        val cached = mangaLensVerification
        if (cached?.matches(file) == true) return cached.installed
        return fileSha256Hex(file)
            .equals(MANGA_LENS_MODEL_ASSET.sha256, ignoreCase = true)
            .also { installed -> mangaLensVerification = MangaLensVerification.from(file, installed) }
    }

    fun deleteMangaLens(): Boolean {
        mangaLensVerification = null
        val dir = mangaLensDir()
        return dir.exists() && dir.deleteRecursively()
    }

    suspend fun downloadMangaLens(): Result<AiMangaLensInstallSummary> =
        downloadLock("mangalens").withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val target = mangaLensModelFile()
                    if (isMangaLensInstalled()) {
                        return@runCatching AiMangaLensInstallSummary(MANGA_LENS_MODEL_ASSET, target)
                    }
                    val dir = mangaLensDir().apply { mkdirs() }
                    val temporary = File(
                        dir,
                        "${MANGA_LENS_MODEL_ASSET.fileName}.${UUID.randomUUID()}.download"
                    )
                    try {
                        val request = Request.Builder().url(MANGA_LENS_MODEL_ASSET.url).build()
                        httpClient.newCall(request).execute().use { response ->
                            require(response.isSuccessful) {
                                "Download failed for MangaLens: ${response.code} ${response.message}"
                            }
                            val body = response.body ?: error("Empty body for MangaLens")
                            temporary.outputStream().use { output ->
                                body.byteStream().use { input -> input.copyTo(output) }
                            }
                        }
                        val actualSha256 = fileSha256Hex(temporary)
                        require(actualSha256.equals(MANGA_LENS_MODEL_ASSET.sha256, ignoreCase = true)) {
                            "MangaLens checksum mismatch: expected ${MANGA_LENS_MODEL_ASSET.sha256}, got $actualSha256"
                        }
                        target.delete()
                        require(temporary.renameTo(target)) { "Unable to install MangaLens model" }
                        mangaLensVerification = MangaLensVerification.from(target, installed = true)
                        AiMangaLensInstallSummary(MANGA_LENS_MODEL_ASSET, target)
                    } finally {
                        temporary.delete()
                    }
                }
            }
        }

    suspend fun downloadPlan(plan: AiLocalModelPlan, revision: String): Result<AiLocalModelInstallSummary> =
        downloadLock("${plan.detRepoId}@${revision.trim()}").withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val downloaded = mutableListOf<File>()
                    downloaded += downloadRepo(plan.detRepoId, revision)
                    AiLocalModelInstallSummary(plan = plan, revision = revision, downloadedFiles = downloaded)
                }
            }
        }

    private fun downloadLock(key: String): Mutex =
        downloadLocks.getOrPut(key) { Mutex() }

    private fun downloadRepo(repoId: String, revision: String): List<File> {
        val files = resolveDownloadableFiles(repoId, revision)
        require(files.isNotEmpty()) { "No downloadable model file was found for $repoId@$revision" }
        val dir = modelDir(repoId, revision).apply { mkdirs() }
        val targetNames = files.map { it.fileName.substringAfterLast('/') }
        require(targetNames.distinct().size == targetNames.size) {
            "Model files have duplicate names after flattening: $repoId@$revision"
        }
        val temporaryDir = File(rootDir, ".${dir.name}.download-${UUID.randomUUID()}")
        temporaryDir.mkdirs()
        try {
            val downloaded = files.map { remoteFile ->
                val target = File(temporaryDir, remoteFile.fileName.substringAfterLast('/'))
                val request = Request.Builder()
                    .url(huggingFaceModelFileUrl(repoId, revision, remoteFile.fileName))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    require(response.isSuccessful) {
                        "Download failed for $repoId/${remoteFile.fileName}: ${response.code} ${response.message}"
                    }
                    val body = response.body ?: error("Empty body for $repoId/${remoteFile.fileName}")
                    target.outputStream().use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                }
                remoteFile.sha256?.let { expectedSha256 ->
                    val actualSha256 = fileSha256Hex(target)
                    require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        "Checksum mismatch for $repoId/${remoteFile.fileName}: expected $expectedSha256, got $actualSha256"
                    }
                }
                target
            }
            writeModelManifest(temporaryDir, downloaded)
            replaceModelDirectory(temporaryDir, dir)
            return downloaded.map { File(dir, it.name) }
        } finally {
            temporaryDir.deleteRecursively()
        }
    }

    private fun writeModelManifest(directory: File, files: List<File>) {
        File(directory, MODEL_MANIFEST_FILE).writeText(
            files.joinToString(separator = "\n") { file ->
                "${file.name}\t${file.length()}\t${fileSha256Hex(file)}"
            } + "\n"
        )
    }

    private fun readModelManifest(directory: File): List<ModelManifestEntry>? {
        val manifest = File(directory, MODEL_MANIFEST_FILE)
        if (!manifest.isFile) return null
        return runCatching {
            manifest.readLines().filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t')
                require(parts.size == 3)
                ModelManifestEntry(
                    fileName = parts[0],
                    length = parts[1].toLong(),
                    sha256 = parts[2]
                )
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun replaceModelDirectory(temporaryDir: File, targetDir: File) {
        val backupDir = File(rootDir, ".${targetDir.name}.backup-${UUID.randomUUID()}")
        var backupCreated = false
        try {
            if (targetDir.exists()) {
                require(targetDir.renameTo(backupDir)) { "Unable to stage existing model directory" }
                backupCreated = true
            }
            require(temporaryDir.renameTo(targetDir)) { "Unable to install model directory" }
            if (backupCreated) backupDir.deleteRecursively()
        } catch (throwable: Throwable) {
            targetDir.deleteRecursively()
            if (backupCreated && backupDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            throw throwable
        }
    }

    private fun resolveDownloadableFiles(repoId: String, revision: String): List<RemoteModelFile> {
        val modelInfo = fetchModelInfo(repoId, revision)
        val siblings = modelInfo.getAsJsonArray("siblings")
            ?.mapNotNull { element ->
                element.asJsonObject.get("rfilename")?.asString?.let { fileName ->
                    RemoteModelFile(fileName = fileName, sha256 = expectedSha256(element.asJsonObject))
                }
            }
            .orEmpty()
        val siblingNames = siblings.map { it.fileName }
        val preferred = selectDownloadableModelFiles(siblingNames)
        val selectedNames = when {
            preferred.isNotEmpty() -> preferred.distinct()
            siblingNames.any { it.endsWith(".onnx", ignoreCase = true) } ->
                siblingNames.filter { it.endsWith(".onnx", ignoreCase = true) }.distinct()
            else -> listOf("model.onnx")
        }
        return selectedNames.map { name -> siblings.firstOrNull { it.fileName == name } ?: RemoteModelFile(name, null) }
    }

    private fun expectedSha256(element: JsonObject): String? {
        val lfs = element.get("lfs")?.takeIf { it.isJsonObject }?.asJsonObject
        return sequenceOf(
            element.get("sha256"),
            element.get("sha"),
            lfs?.get("sha256"),
            lfs?.get("oid")
        ).filterNotNull()
            .filter { !it.isJsonNull && it.isJsonPrimitive }
            .mapNotNull { it.asString.removePrefix("sha256:").lowercase() }
            .firstOrNull { it.matches(Regex("[0-9a-f]{64}")) }
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

    private fun mangaLensDir(): File = File(rootDir, "mangalens__${MANGA_LENS_MODEL_ASSET.sha256.take(12)}")

    private fun sanitizePathSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private data class MangaLensVerification(
        val path: String,
        val length: Long,
        val lastModified: Long,
        val installed: Boolean
    ) {
        fun matches(file: File): Boolean =
            path == file.absolutePath && length == file.length() && lastModified == file.lastModified()

        companion object {
            fun from(file: File, installed: Boolean): MangaLensVerification = MangaLensVerification(
                path = file.absolutePath,
                length = file.length(),
                lastModified = file.lastModified(),
                installed = installed
            )
        }
    }

    private data class ModelManifestEntry(
        val fileName: String,
        val length: Long,
        val sha256: String
    )

    private data class RemoteModelFile(
        val fileName: String,
        val sha256: String?
    )
}

private const val MODEL_MANIFEST_FILE = ".manifest"

internal fun fileSha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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
    sourceTextProfile: AiSourceTextProfile,
    translationMode: AiTranslationMode = AiTranslationMode.LOCAL_DETECTION
): Int = when {
    sourceTextProfile == AiSourceTextProfile.JAPANESE_MANGA &&
        translationMode == AiTranslationMode.HIGH_ACCURACY -> 2048
    sourceTextProfile == AiSourceTextProfile.JAPANESE_MANGA -> 1600
    sourceTextProfile == AiSourceTextProfile.HORIZONTAL_COMIC ||
        sourceTextProfile == AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON -> when (tier) {
        AiLocalModelTier.LOW -> 1280
        AiLocalModelTier.BALANCED,
        AiLocalModelTier.HIGH -> 1536
    }
    else -> when (tier) {
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
