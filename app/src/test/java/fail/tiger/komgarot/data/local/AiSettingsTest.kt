package fail.tiger.komgarot.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSettingsTest {
    @Test
    fun defaultsMatchAiTranslationSpec() {
        val settings = AiSettings.defaults()

        assertFalse(settings.enabled)
        assertEquals("", settings.baseUrl)
        assertEquals("", settings.modelName)
        assertEquals(AiImageTransport.BASE64, settings.imageTransport)
        assertEquals(AiTranslationRequestMode.PARALLEL, settings.requestMode)
        assertEquals(10, settings.pagesPerRequest)
        assertEquals(8, settings.concurrentRequests)
        assertEquals(20, settings.maxImagesPerRequest)
        assertEquals(30, settings.timeoutSeconds)
        assertEquals(AiImageMaxEdge.PX_1600, settings.imageMaxEdge)
        assertEquals(AiTranslationMode.LOCAL_DETECTION, settings.preferredMode)
        assertEquals(AiSourceTextProfile.AUTO, settings.sourceTextProfile)
        assertEquals(AiLocalModelSource.HUGGING_FACE, settings.localModelSource)
        assertEquals("PaddlePaddle/pp-ocrv6", settings.modelCollectionId)
        assertEquals("main", settings.modelRevision)
        assertTrue(settings.downloadLatestModel)
        assertTrue(settings.autoSelectDeviceTier)
        assertEquals("", settings.customInstructions)
        assertFalse(settings.testModeEnabled)
        assertFalse(settings.configurationTestPassed)
    }

    @Test
    fun pagesPerRequestAcceptsPositiveManualValues() {
        assertEquals(1, AiSettings.normalizePagesPerRequest(-1))
        assertEquals(1, AiSettings.normalizePagesPerRequest(0))
        assertEquals(10, AiSettings.normalizePagesPerRequest(10))
        assertEquals(80, AiSettings.normalizePagesPerRequest(80))
    }

    @Test
    fun concurrentRequestsUseSafeUpperBound() {
        assertEquals(1, AiSettings.normalizeConcurrentRequests(-3))
        assertEquals(1, AiSettings.normalizeConcurrentRequests(0))
        assertEquals(3, AiSettings.normalizeConcurrentRequests(3))
        assertEquals(9, AiSettings.normalizeConcurrentRequests(9))
        assertEquals(32, AiSettings.normalizeConcurrentRequests(32))
        assertEquals(32, AiSettings.normalizeConcurrentRequests(80))
    }

    @Test
    fun requestModeFallsBackToBoundedParallel() {
        assertEquals(AiTranslationRequestMode.PARALLEL, AiTranslationRequestMode.fromStoredValue(""))
        assertEquals(AiTranslationRequestMode.PARALLEL, AiTranslationRequestMode.fromStoredValue("unknown"))
        assertEquals(AiTranslationRequestMode.SERIAL, AiTranslationRequestMode.fromStoredValue("serial"))
        assertEquals(AiTranslationRequestMode.PARALLEL, AiTranslationRequestMode.fromStoredValue("parallel"))
    }

    @Test
    fun maxImagesPerRequestKeepsRoomForPageContextAndOneCrop() {
        assertEquals(2, AiSettings.normalizeMaxImagesPerRequest(-3))
        assertEquals(2, AiSettings.normalizeMaxImagesPerRequest(1))
        assertEquals(20, AiSettings.normalizeMaxImagesPerRequest(20))
        assertEquals(80, AiSettings.normalizeMaxImagesPerRequest(80))
    }

    @Test
    fun timeoutAcceptsPositiveManualValues() {
        assertEquals(0, AiSettings.normalizeTimeoutSeconds(0))
        assertEquals(60, AiSettings.normalizeTimeoutSeconds(60))
        assertEquals(300, AiSettings.normalizeTimeoutSeconds(300))
    }

    @Test
    fun completeConfigurationRequiresBaseUrlModelAndApiKey() {
        val settings = AiSettings.defaults().copy(
            baseUrl = "https://api.example.test/v1",
            modelName = "vision-model"
        )

        assertFalse(settings.hasCompleteModelConfiguration(apiKey = ""))
        assertTrue(settings.hasCompleteModelConfiguration(apiKey = "key"))
    }

    @Test
    fun secureAiSettingsUsesExpectedPreferenceFileAndKeys() {
        assertEquals("secure_ai_settings", SecureAiSettingsStore.FILE_NAME)
        assertEquals("api_key", SecureAiSettingsStore.API_KEY)
        assertEquals("image_url_extra_query", SecureAiSettingsStore.IMAGE_URL_EXTRA_QUERY)
        assertEquals("s3_endpoint", SecureAiSettingsStore.S3_ENDPOINT)
        assertEquals("s3_region", SecureAiSettingsStore.S3_REGION)
        assertEquals("s3_bucket", SecureAiSettingsStore.S3_BUCKET)
        assertEquals("s3_access_key", SecureAiSettingsStore.S3_ACCESS_KEY)
        assertEquals("s3_secret_key", SecureAiSettingsStore.S3_SECRET_KEY)
        assertEquals("s3_path_prefix", SecureAiSettingsStore.S3_PATH_PREFIX)
        assertEquals("s3_ttl_seconds", SecureAiSettingsStore.S3_TTL_SECONDS)
        assertEquals("s3_path_style", SecureAiSettingsStore.S3_PATH_STYLE)
        assertTrue(
            SecureAiSettings(
                s3Endpoint = "https://s3.example.test",
                s3Region = "us-east-1",
                s3Bucket = "komgarot-ai",
                s3AccessKey = "AKID",
                s3SecretKey = "SECRET"
            ).hasCompleteS3ImageUrlConfiguration()
        )
    }

    @Test
    fun secureWebDavSettingsUsesExpectedPreferenceFileAndKeys() {
        assertEquals("secure_webdav_settings", SecureWebDavSettingsStore.FILE_NAME)
        assertEquals("url", SecureWebDavSettingsStore.URL)
        assertEquals("username", SecureWebDavSettingsStore.USERNAME)
        assertEquals("password", SecureWebDavSettingsStore.PASSWORD)
    }

    @Test
    fun secureWebDavUrlKeepsTrailingSlash() {
        assertEquals("", normalizeWebDavUrl(""))
        assertEquals("https://dav.example.test/", normalizeWebDavUrl(" https://dav.example.test "))
        assertEquals("https://dav.example.test/base/", normalizeWebDavUrl("https://dav.example.test/base/"))
        assertEquals("https://dav.example.test/base/", normalizeWebDavUrl("https://dav.example.test/base///"))
    }

    @Test
    fun authPreferencesClearKeepsLocalSettingsAndClearsOnlyCredentials() {
        val source = java.io.File("src/main/java/fail/tiger/komgarot/data/local/AuthPreferences.kt").readText()
        val clearBody = Regex("suspend fun clear\\(\\) \\{([\\s\\S]*?)\\n    \\}")
            .find(source)
            ?.groupValues
            ?.get(1)
            .orEmpty()

        assertTrue(clearBody.contains("it.remove(SERVER_URL)"))
        assertTrue(clearBody.contains("it.remove(USERNAME)"))
        assertTrue(clearBody.contains("it.remove(PASSWORD)"))
        assertTrue(clearBody.contains("secureAuthStore.clear()"))
        assertFalse(clearBody.contains("it.clear()"))
    }

    @Test
    fun modelCollectionDefaultsCanBeOverridden() {
        val settings = AiSettings.defaults().copy(
            modelCollectionId = "Custom/collection",
            modelRevision = "refs/tags/v1.0.0",
            downloadLatestModel = false
        )

        assertEquals("Custom/collection", settings.modelCollectionId)
        assertEquals("refs/tags/v1.0.0", settings.modelRevision)
        assertFalse(settings.downloadLatestModel)
    }

    @Test
    fun sourceTextProfileStoredValuesRoundTrip() {
        assertEquals(AiSourceTextProfile.AUTO, AiSourceTextProfile.fromStoredValue(""))
        assertEquals(AiSourceTextProfile.JAPANESE_MANGA, AiSourceTextProfile.fromStoredValue("japanese_manga"))
        assertEquals(AiSourceTextProfile.HORIZONTAL_COMIC, AiSourceTextProfile.fromStoredValue("horizontal_comic"))
        assertEquals(AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON, AiSourceTextProfile.fromStoredValue("korean_horizontal_webtoon"))
        assertEquals("korean_horizontal_webtoon", AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON.storedValue)
    }
}
