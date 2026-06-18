package fail.tiger.komgarot.data.repository

import com.google.gson.GsonBuilder
import fail.tiger.komgarot.data.local.AiLocalModelSource
import fail.tiger.komgarot.data.local.AiTranslatedBook
import fail.tiger.komgarot.data.local.AiTranslationStore
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.SecureAiSettingsStore
import fail.tiger.komgarot.data.local.SecureWebDavSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
            val body = buildBackupPayload()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(webDav.url.trimEnd('/') + "/komgarot-ai-translation-backup.json")
                .header("Authorization", Credentials.basic(webDav.username, webDav.password))
                .put(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                require(response.isSuccessful) { response.message }
            }
        }
    }

    suspend fun buildBackupPayload(): String {
        val secureAi = secureAiSettingsStore.read()
        val payload = WebDavBackupPayload(
            settings = WebDavBackupSettings(
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
                aiPagesPerRequest = prefs.aiPagesPerRequest.first(),
                aiConcurrentRequests = prefs.aiConcurrentRequests.first(),
                aiMaxImagesPerRequest = prefs.aiMaxImagesPerRequest.first(),
                aiTimeoutSeconds = prefs.aiTimeoutSeconds.first(),
                aiImageMaxEdge = prefs.aiImageMaxEdge.first().storedValue,
                aiCustomInstructions = prefs.aiCustomInstructions.first(),
                aiImageUrlExtraQuery = secureAi.imageUrlExtraQuery
            ),
            translations = aiTranslationStore.exportBooks()
        )
        return webDavBackupGson.toJson(payload)
    }
}

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
    val aiPagesPerRequest: Int = 10,
    val aiConcurrentRequests: Int = 3,
    val aiMaxImagesPerRequest: Int = 20,
    val aiTimeoutSeconds: Int = 30,
    val aiImageMaxEdge: String = "",
    val aiCustomInstructions: String = "",
    val aiImageUrlExtraQuery: String = ""
)

private val webDavBackupGson = GsonBuilder().disableHtmlEscaping().create()
