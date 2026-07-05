package fail.tiger.komgarot.data.local

data class AiSettings(
    val enabled: Boolean,
    val baseUrl: String,
    val modelName: String,
    val targetLocale: String,
    val targetLanguageName: String,
    val preferredMode: AiTranslationMode,
    val sourceTextProfile: AiSourceTextProfile,
    val localModelSource: AiLocalModelSource,
    val modelCollectionId: String,
    val modelRevision: String,
    val downloadLatestModel: Boolean,
    val autoSelectDeviceTier: Boolean,
    val imageTransport: AiImageTransport,
    val requestMode: AiTranslationRequestMode,
    val pagesPerRequest: Int,
    val concurrentRequests: Int,
    val maxImagesPerRequest: Int,
    val timeoutSeconds: Int,
    val imageMaxEdge: AiImageMaxEdge,
    val customInstructions: String,
    val testModeEnabled: Boolean,
    val configurationTestPassed: Boolean
) {
    fun hasCompleteModelConfiguration(apiKey: String): Boolean =
        baseUrl.isNotBlank() && modelName.isNotBlank() && apiKey.isNotBlank()

    companion object {
        fun defaults(
            targetLocale: String = "",
            targetLanguageName: String = ""
        ): AiSettings = AiSettings(
            enabled = false,
            baseUrl = "",
            modelName = "",
            targetLocale = targetLocale,
            targetLanguageName = targetLanguageName,
            preferredMode = AiTranslationMode.LOCAL_DETECTION,
            sourceTextProfile = AiSourceTextProfile.AUTO,
            localModelSource = AiLocalModelSource.HUGGING_FACE,
            modelCollectionId = "PaddlePaddle/pp-ocrv6",
            modelRevision = "main",
            downloadLatestModel = true,
            autoSelectDeviceTier = true,
            imageTransport = AiImageTransport.BASE64,
            requestMode = AiTranslationRequestMode.SERIAL,
            pagesPerRequest = 10,
            concurrentRequests = 8,
            maxImagesPerRequest = 20,
            timeoutSeconds = 30,
            imageMaxEdge = AiImageMaxEdge.PX_1600,
            customInstructions = "",
            testModeEnabled = false,
            configurationTestPassed = false
        )

        fun normalizePagesPerRequest(value: Int): Int = value.coerceAtLeast(1)
        fun normalizeConcurrentRequests(value: Int): Int = value.coerceIn(1, 8)
        fun normalizeMaxImagesPerRequest(value: Int): Int = value.coerceAtLeast(2)
        fun normalizeTimeoutSeconds(value: Int): Int = value.coerceAtLeast(0)
    }
}

enum class AiTranslationMode(val storedValue: String) {
    LOCAL_DETECTION("local_detection");

    companion object {
        fun fromStoredValue(value: String): AiTranslationMode =
            entries.firstOrNull { it.storedValue == value } ?: LOCAL_DETECTION
    }
}

enum class AiSourceTextProfile(val storedValue: String) {
    AUTO("auto"),
    JAPANESE_MANGA("japanese_manga"),
    KOREAN_HORIZONTAL_WEBTOON("korean_horizontal_webtoon");

    companion object {
        fun fromStoredValue(value: String): AiSourceTextProfile =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}

enum class AiLocalModelSource(val storedValue: String) {
    HUGGING_FACE("huggingface");

    companion object {
        fun fromStoredValue(value: String): AiLocalModelSource =
            entries.firstOrNull { it.storedValue == value } ?: HUGGING_FACE
    }
}

enum class AiImageTransport(val storedValue: String) {
    BASE64("base64"),
    IMAGE_URL("image_url");

    companion object {
        fun fromStoredValue(value: String): AiImageTransport =
            entries.firstOrNull { it.storedValue == value } ?: BASE64
    }
}

enum class AiTranslationRequestMode(val storedValue: String) {
    SERIAL("serial"),
    PARALLEL("parallel");

    companion object {
        fun fromStoredValue(value: String): AiTranslationRequestMode =
            entries.firstOrNull { it.storedValue == value } ?: SERIAL
    }
}

enum class AiImageMaxEdge(val storedValue: String, val pixels: Int?) {
    PX_1024("1024", 1024),
    PX_1600("1600", 1600),
    PX_2048("2048", 2048),
    ORIGINAL("original", null);

    companion object {
        fun fromStoredValue(value: String): AiImageMaxEdge =
            entries.firstOrNull { it.storedValue == value } ?: PX_1600
    }
}
