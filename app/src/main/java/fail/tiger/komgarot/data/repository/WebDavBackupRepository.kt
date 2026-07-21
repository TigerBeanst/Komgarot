package fail.tiger.komgarot.data.repository

import com.google.gson.GsonBuilder
import fail.tiger.komgarot.data.local.AiImageMaxEdge
import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiLocalModelSource
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslatedBook
import fail.tiger.komgarot.data.local.AiTranslationRequestMode
import fail.tiger.komgarot.data.local.AiTranslationStore
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.SecureAiSettings
import fail.tiger.komgarot.data.local.SecureAiSettingsStore
import fail.tiger.komgarot.data.local.SecureWebDavSettings
import fail.tiger.komgarot.data.local.SecureWebDavSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val WEB_DAV_BACKUP_DIR_NAME = "Komgarot"
private const val WEB_DAV_BACKUP_PREFIX = "Komgarot_backup_"
private const val WEB_DAV_BACKUP_EXTENSION = ".zip"
private const val WEB_DAV_APP_SETTINGS_ENTRY = "app-settings.json"
private const val WEB_DAV_AI_TRANSLATE_DIR = "ai-translate/"
private val webDavBackupFileRegex = Regex("""Komgarot_backup_\d{8}_\d{6}\.zip""")

class WebDavBackupRepository(
    private val prefs: AuthPreferences,
    private val secureAiSettingsStore: SecureAiSettingsStore,
    private val secureWebDavSettingsStore: SecureWebDavSettingsStore,
    private val aiTranslationStore: AiTranslationStore,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val webDav = secureWebDavSettingsStore.read()
            require(webDav.url.isNotBlank()) { "WebDAV URL is required" }
            val backupDirectoryUrl = webDav.url.trimEnd('/') + "/$WEB_DAV_BACKUP_DIR_NAME/"
            val directoryRequest = Request.Builder()
                .url(backupDirectoryUrl)
                .header("Authorization", Credentials.basic(webDav.username, webDav.password))
                .method("MKCOL", null)
                .build()
            httpClient.newCall(directoryRequest).execute().use { response ->
                require(response.isSuccessful || response.code == 405) { response.message }
            }
            val archive = buildBackupArchive()
            val body = archive.bytes.toRequestBody("application/zip".toMediaType())
            val request = Request.Builder()
                .url(backupDirectoryUrl + archive.fileName)
                .header("Authorization", Credentials.basic(webDav.username, webDav.password))
                .put(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                require(response.isSuccessful) { response.message }
            }
        }
    }

    suspend fun listBackups(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val webDav = secureWebDavSettingsStore.read()
            require(webDav.url.isNotBlank()) { "WebDAV URL is required" }
            listBackupFiles(webDav).take(5)
        }
    }

    suspend fun restoreBackup(fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(webDavBackupFileRegex.matches(fileName)) { "Invalid WebDAV backup file name" }
            val payload = downloadBackupPayload(fileName)
            applyBackupPayload(payload)
        }
    }

    suspend fun restoreNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val webDav = secureWebDavSettingsStore.read()
            require(webDav.url.isNotBlank()) { "WebDAV URL is required" }
            val fileName = listBackupFiles(webDav).firstOrNull()
                ?: error("No WebDAV backup found")
            val payload = downloadBackupPayload(fileName)
            applyBackupPayload(payload)
        }
    }

    suspend fun buildBackupPayload(): String {
        val payload = WebDavBackupPayload(
            settings = buildBackupSettings(),
            translations = aiTranslationStore.exportBooks()
        )
        return webDavBackupGson.toJson(payload)
    }

    suspend fun buildBackupArchive(now: Date = Date()): WebDavBackupArchive {
        val settings = buildBackupSettings()
        val books = aiTranslationStore.exportBooks()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(WEB_DAV_APP_SETTINGS_ENTRY))
            zip.write(webDavBackupGson.toJson(settings).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            books.forEach { book ->
                zip.putNextEntry(ZipEntry(WEB_DAV_AI_TRANSLATE_DIR + webDavBackupBookFileName(book.bookId)))
                zip.write(webDavBackupGson.toJson(book).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return WebDavBackupArchive(
            fileName = webDavBackupFileName(now),
            bytes = output.toByteArray()
        )
    }

    private suspend fun buildBackupSettings(): WebDavBackupSettings {
        val secureAi = secureAiSettingsStore.read()
        return WebDavBackupSettings(
            aiTranslationEnabled = prefs.aiTranslationEnabled.first(),
            aiBaseUrl = prefs.aiBaseUrl.first(),
            aiModelName = prefs.aiModelName.first(),
            aiTargetLocale = prefs.aiTargetLocale.first(),
            aiTargetLanguageName = prefs.aiTargetLanguageName.first(),
            aiLocalModelSource = prefs.aiLocalModelSource.first().storedValue,
            aiModelCollectionId = prefs.aiModelCollectionId.first(),
            aiModelRevision = prefs.aiModelRevision.first(),
            aiDownloadLatestModel = prefs.aiDownloadLatestModel.first(),
            aiAutoSelectDeviceTier = prefs.aiAutoSelectDeviceTier.first(),
            aiImageTransport = prefs.aiImageTransport.first().storedValue,
            aiTranslationRequestMode = prefs.aiTranslationRequestMode.first().storedValue,
            aiSourceTextProfile = prefs.aiSourceTextProfile.first().storedValue,
            aiPagesPerRequest = prefs.aiPagesPerRequest.first(),
            aiConcurrentRequests = prefs.aiConcurrentRequests.first(),
            aiMaxImagesPerRequest = prefs.aiMaxImagesPerRequest.first(),
            aiTimeoutSeconds = prefs.aiTimeoutSeconds.first(),
            aiImageMaxEdge = prefs.aiImageMaxEdge.first().storedValue,
            aiCustomInstructions = prefs.aiCustomInstructions.first(),
            aiApiKey = secureAi.apiKey,
            aiImageUrlExtraQuery = secureAi.imageUrlExtraQuery,
            s3Endpoint = secureAi.s3Endpoint,
            s3Region = secureAi.s3Region,
            s3Bucket = secureAi.s3Bucket,
            s3AccessKey = secureAi.s3AccessKey,
            s3SecretKey = secureAi.s3SecretKey,
            s3PathPrefix = secureAi.s3PathPrefix,
            s3TtlSeconds = secureAi.s3TtlSeconds,
            s3PathStyle = secureAi.s3PathStyle
        )
    }

    private fun downloadBackupPayload(fileName: String): WebDavBackupPayload {
        val webDav = secureWebDavSettingsStore.read()
        require(webDav.url.isNotBlank()) { "WebDAV URL is required" }
        val backupDirectoryUrl = webDav.url.trimEnd('/') + "/$WEB_DAV_BACKUP_DIR_NAME/"
        val request = Request.Builder()
            .url(backupDirectoryUrl + fileName)
            .header("Authorization", Credentials.basic(webDav.username, webDav.password))
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { response.message }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            require(bytes.isNotEmpty()) { "WebDAV backup file is empty" }
            return parseBackupArchive(bytes)
        }
    }

    private fun listBackupFiles(webDav: SecureWebDavSettings): List<String> {
        val backupDirectoryUrl = webDav.url.trimEnd('/') + "/$WEB_DAV_BACKUP_DIR_NAME/"
        val request = Request.Builder()
            .url(backupDirectoryUrl)
            .header("Authorization", Credentials.basic(webDav.username, webDav.password))
            .header("Depth", "1")
            .method(
                "PROPFIND",
                """<?xml version="1.0" encoding="utf-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:displayname/></D:prop></D:propfind>"""
                    .toRequestBody("application/xml; charset=utf-8".toMediaType())
            )
            .build()
        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { response.message }
            val text = response.body?.string().orEmpty()
            return webDavBackupFileRegex.findAll(text)
                .map { it.value }
                .distinct()
                .sortedDescending()
                .take(5)
                .toList()
        }
    }

    private fun parseBackupArchive(bytes: ByteArray): WebDavBackupPayload {
        var settings = WebDavBackupSettings()
        val translations = mutableListOf<AiTranslatedBook>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    when {
                        entry.name == WEB_DAV_APP_SETTINGS_ENTRY -> {
                            settings = webDavBackupGson.fromJson(text, WebDavBackupSettings::class.java)
                                ?: WebDavBackupSettings()
                        }
                        entry.name.startsWith(WEB_DAV_AI_TRANSLATE_DIR) && entry.name.endsWith(".json") -> {
                            webDavBackupGson.fromJson(text, AiTranslatedBook::class.java)
                                ?.takeIf { it.bookId.isNotBlank() }
                                ?.let(translations::add)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return WebDavBackupPayload(settings = settings, translations = translations)
    }

    private suspend fun applyBackupPayload(payload: WebDavBackupPayload) {
        val settings = payload.settings
        prefs.setAiTranslationEnabled(settings.aiTranslationEnabled)
        prefs.setAiBaseUrl(settings.aiBaseUrl)
        prefs.setAiModelName(settings.aiModelName)
        prefs.setAiTargetLocale(settings.aiTargetLocale, settings.aiTargetLanguageName)
        prefs.setAiLocalModelSource(AiLocalModelSource.fromStoredValue(settings.aiLocalModelSource))
        prefs.setAiModelCollectionId(settings.aiModelCollectionId)
        prefs.setAiModelRevision(settings.aiModelRevision)
        prefs.setAiDownloadLatestModel(settings.aiDownloadLatestModel)
        prefs.setAiAutoSelectDeviceTier(settings.aiAutoSelectDeviceTier)
        prefs.setAiImageTransport(AiImageTransport.fromStoredValue(settings.aiImageTransport))
        prefs.setAiTranslationRequestMode(AiTranslationRequestMode.fromStoredValue(settings.aiTranslationRequestMode))
        prefs.setAiSourceTextProfile(AiSourceTextProfile.fromStoredValue(settings.aiSourceTextProfile))
        prefs.setAiPagesPerRequest(settings.aiPagesPerRequest)
        prefs.setAiConcurrentRequests(settings.aiConcurrentRequests)
        prefs.setAiMaxImagesPerRequest(settings.aiMaxImagesPerRequest)
        prefs.setAiTimeoutSeconds(settings.aiTimeoutSeconds)
        prefs.setAiImageMaxEdge(AiImageMaxEdge.fromStoredValue(settings.aiImageMaxEdge))
        prefs.setAiCustomInstructions(settings.aiCustomInstructions)
        secureAiSettingsStore.saveApiKey(settings.aiApiKey)
        secureAiSettingsStore.saveImageUrlExtraQuery(settings.aiImageUrlExtraQuery)
        secureAiSettingsStore.saveS3Settings(
            SecureAiSettings(
                apiKey = settings.aiApiKey,
                imageUrlExtraQuery = settings.aiImageUrlExtraQuery,
                s3Endpoint = settings.s3Endpoint,
                s3Region = settings.s3Region,
                s3Bucket = settings.s3Bucket,
                s3AccessKey = settings.s3AccessKey,
                s3SecretKey = settings.s3SecretKey,
                s3PathPrefix = settings.s3PathPrefix,
                s3TtlSeconds = settings.s3TtlSeconds,
                s3PathStyle = settings.s3PathStyle
            )
        )
        aiTranslationStore.importBooks(payload.translations)
    }
}

data class WebDavBackupArchive(
    val fileName: String,
    val bytes: ByteArray
)

data class WebDavBackupPayload(
    val schemaVersion: Int = 1,
    val settings: WebDavBackupSettings = WebDavBackupSettings(),
    val translations: List<AiTranslatedBook> = emptyList()
)

data class WebDavBackupSettings(
    val aiTranslationEnabled: Boolean = false,
    val aiBaseUrl: String = "",
    val aiModelName: String = "",
    val aiTargetLocale: String = "",
    val aiTargetLanguageName: String = "",
    val aiLocalModelSource: String = AiLocalModelSource.HUGGING_FACE.storedValue,
    val aiModelCollectionId: String = "PaddlePaddle/pp-ocrv6",
    val aiModelRevision: String = "main",
    val aiDownloadLatestModel: Boolean = true,
    val aiAutoSelectDeviceTier: Boolean = true,
    val aiImageTransport: String = "",
    val aiTranslationRequestMode: String = AiTranslationRequestMode.PARALLEL.storedValue,
    val aiSourceTextProfile: String = AiSourceTextProfile.AUTO.storedValue,
    val aiPagesPerRequest: Int = 10,
    val aiConcurrentRequests: Int = 2,
    val aiMaxImagesPerRequest: Int = 20,
    val aiTimeoutSeconds: Int = 30,
    val aiImageMaxEdge: String = "",
    val aiCustomInstructions: String = "",
    val aiApiKey: String = "",
    val aiImageUrlExtraQuery: String = "",
    val s3Endpoint: String = "",
    val s3Region: String = "",
    val s3Bucket: String = "",
    val s3AccessKey: String = "",
    val s3SecretKey: String = "",
    val s3PathPrefix: String = SecureAiSettingsStore.DEFAULT_S3_PATH_PREFIX,
    val s3TtlSeconds: Int = SecureAiSettingsStore.DEFAULT_S3_TTL_SECONDS,
    val s3PathStyle: Boolean = true
)

private val webDavBackupGson = GsonBuilder().disableHtmlEscaping().create()

private fun webDavBackupFileName(now: Date): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
    return WEB_DAV_BACKUP_PREFIX + timestamp + WEB_DAV_BACKUP_EXTENSION
}

private fun webDavBackupBookFileName(bookId: String): String {
    val safeId = bookId.map { char ->
        if (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.') char else '_'
    }.joinToString("")
    return "${safeId.ifBlank { "unknown" }}.json"
}
