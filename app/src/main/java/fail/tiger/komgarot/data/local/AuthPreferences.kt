package fail.tiger.komgarot.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.Locale

private val Context.dataStore by preferencesDataStore("auth")

enum class LandscapePageSplitOrder(val storedValue: String) {
    RIGHT_FIRST("right_first"),
    LEFT_FIRST("left_first");

    companion object {
        fun fromStoredValue(value: String): LandscapePageSplitOrder =
            entries.firstOrNull { it.storedValue == value } ?: RIGHT_FIRST
    }
}

class AuthPreferences(private val context: Context) {
    private val SERVER_URL = stringPreferencesKey("server_url")
    private val USERNAME = stringPreferencesKey("username")
    private val PASSWORD = stringPreferencesKey("password")
    private val ALWAYS_INCOGNITO = booleanPreferencesKey("always_incognito")
    private val PRELOAD_PAGES = intPreferencesKey("preload_pages")
    private val READING_DIRECTION = stringPreferencesKey("reading_direction")
    private val PAGE_FIT = stringPreferencesKey("page_fit")
    private val SPLIT_LANDSCAPE_PAGES = booleanPreferencesKey("split_landscape_pages")
    private val LANDSCAPE_PAGE_SPLIT_ORDER = stringPreferencesKey("landscape_page_split_order")
    private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val EINK_MODE = booleanPreferencesKey("eink_mode")
    private val TAP_PAGE_TURN = booleanPreferencesKey("tap_page_turn")
    private val SHOW_BOOK_THUMBNAILS = booleanPreferencesKey("show_book_thumbnails")
    private val COVER_CACHE_SIZE_MB = intPreferencesKey("cover_cache_size_mb")
    private val READER_CACHE_SIZE_MB = intPreferencesKey("reader_cache_size_mb")
    private val CLEAR_CACHE_ON_STARTUP = booleanPreferencesKey("clear_cache_on_startup")
    private val AI_TRANSLATION_ENABLED = booleanPreferencesKey("ai_translation_enabled")
    private val AI_BASE_URL = stringPreferencesKey("ai_base_url")
    private val AI_MODEL_NAME = stringPreferencesKey("ai_model_name")
    private val AI_TARGET_LOCALE = stringPreferencesKey("ai_target_locale")
    private val AI_TARGET_LANGUAGE_NAME = stringPreferencesKey("ai_target_language_name")
    private val AI_SOURCE_TEXT_PROFILE = stringPreferencesKey("ai_source_text_profile")
    private val AI_LOCAL_MODEL_SOURCE = stringPreferencesKey("ai_local_model_source")
    private val AI_MODEL_COLLECTION_ID = stringPreferencesKey("ai_model_collection_id")
    private val AI_MODEL_REVISION = stringPreferencesKey("ai_model_revision")
    private val AI_DOWNLOAD_LATEST_MODEL = booleanPreferencesKey("ai_download_latest_model")
    private val AI_AUTO_SELECT_DEVICE_TIER = booleanPreferencesKey("ai_auto_select_device_tier")
    private val AI_IMAGE_TRANSPORT = stringPreferencesKey("ai_image_transport")
    private val AI_TRANSLATION_REQUEST_MODE = stringPreferencesKey("ai_translation_request_mode")
    private val AI_PAGES_PER_REQUEST = intPreferencesKey("ai_pages_per_request")
    private val AI_CONCURRENT_REQUESTS = intPreferencesKey("ai_concurrent_requests")
    private val AI_MAX_IMAGES_PER_REQUEST = intPreferencesKey("ai_max_images_per_request")
    private val AI_TIMEOUT_SECONDS = intPreferencesKey("ai_timeout_seconds")
    private val AI_IMAGE_MAX_EDGE = stringPreferencesKey("ai_image_max_edge")
    private val AI_SKIP_SOUND_EFFECTS = booleanPreferencesKey("ai_skip_sound_effects")
    private val AI_REASONING_EFFORT = stringPreferencesKey("ai_reasoning_effort")
    private val AI_CUSTOM_INSTRUCTIONS = stringPreferencesKey("ai_custom_instructions")
    private val AI_TEST_MODE_ENABLED = booleanPreferencesKey("ai_test_mode_enabled")
    private val AI_CONFIGURATION_TEST_PASSED = booleanPreferencesKey("ai_configuration_test_passed")
    private val AI_TRANSLATION_DISPLAY_MODE = stringPreferencesKey("ai_translation_display_mode")
    private val AI_VERTICAL_GLYPH_SPACING_PERCENT = intPreferencesKey("ai_vertical_glyph_spacing_percent")

    private val _serverUrl = MutableStateFlow("")
    private val _username = MutableStateFlow("")
    private val _password = MutableStateFlow("")
    private val secureAuthStore = SecureAuthStore(context)

    val serverUrl = _serverUrl.asStateFlow()
    val username = _username.asStateFlow()
    val alwaysIncognito: Flow<Boolean> = context.dataStore.data.map { it[ALWAYS_INCOGNITO] ?: false }
    val preloadPages: Flow<Int> = context.dataStore.data.map { it[PRELOAD_PAGES] ?: 5 }
    val readingDirection: Flow<String> = context.dataStore.data.map { it[READING_DIRECTION] ?: "LTR" }
    val pageFit: Flow<String> = context.dataStore.data.map { it[PAGE_FIT] ?: "FIT" }
    val splitLandscapePages: Flow<Boolean> = context.dataStore.data.map { it[SPLIT_LANDSCAPE_PAGES] ?: false }
    val landscapePageSplitOrder: Flow<LandscapePageSplitOrder> = context.dataStore.data.map {
        LandscapePageSplitOrder.fromStoredValue(it[LANDSCAPE_PAGE_SPLIT_ORDER].orEmpty())
    }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[KEEP_SCREEN_ON] ?: true }
    val einkMode: Flow<Boolean> = context.dataStore.data.map { it[EINK_MODE] ?: false }
    val tapPageTurn: Flow<Boolean> = context.dataStore.data.map { it[TAP_PAGE_TURN] ?: false }
    val showBookThumbnails: Flow<Boolean> = context.dataStore.data.map { it[SHOW_BOOK_THUMBNAILS] ?: true }
    val coverCacheSizeMb: Flow<Int> = context.dataStore.data.map { it[COVER_CACHE_SIZE_MB] ?: CacheSizeOption.default.sizeMb }
    val readerCacheSizeMb: Flow<Int> = context.dataStore.data.map { it[READER_CACHE_SIZE_MB] ?: CacheSizeOption.default.sizeMb }
    val clearCacheOnStartup: Flow<Boolean> = context.dataStore.data.map { it[CLEAR_CACHE_ON_STARTUP] ?: false }
    val aiTranslationEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_TRANSLATION_ENABLED] ?: false }
    val aiBaseUrl: Flow<String> = context.dataStore.data.map { it[AI_BASE_URL] ?: "" }
    val aiModelName: Flow<String> = context.dataStore.data.map { it[AI_MODEL_NAME] ?: "" }
    val aiTargetLocale: Flow<String> = context.dataStore.data.map { it[AI_TARGET_LOCALE] ?: systemTargetLocale() }
    val aiTargetLanguageName: Flow<String> = context.dataStore.data.map {
        it[AI_TARGET_LANGUAGE_NAME] ?: systemTargetLanguageName()
    }
    val aiSourceTextProfile: Flow<AiSourceTextProfile> = context.dataStore.data.map {
        AiSourceTextProfile.fromStoredValue(it[AI_SOURCE_TEXT_PROFILE].orEmpty())
    }
    val aiLocalModelSource: Flow<AiLocalModelSource> = context.dataStore.data.map {
        AiLocalModelSource.fromStoredValue(it[AI_LOCAL_MODEL_SOURCE].orEmpty())
    }
    val aiModelCollectionId: Flow<String> = context.dataStore.data.map {
        it[AI_MODEL_COLLECTION_ID] ?: "PaddlePaddle/pp-ocrv6"
    }
    val aiModelRevision: Flow<String> = context.dataStore.data.map { it[AI_MODEL_REVISION] ?: "main" }
    val aiDownloadLatestModel: Flow<Boolean> = context.dataStore.data.map { it[AI_DOWNLOAD_LATEST_MODEL] ?: true }
    val aiAutoSelectDeviceTier: Flow<Boolean> = context.dataStore.data.map { it[AI_AUTO_SELECT_DEVICE_TIER] ?: true }
    val aiImageTransport: Flow<AiImageTransport> = context.dataStore.data.map {
        AiImageTransport.fromStoredValue(it[AI_IMAGE_TRANSPORT].orEmpty())
    }
    val aiTranslationRequestMode: Flow<AiTranslationRequestMode> = context.dataStore.data.map {
        AiTranslationRequestMode.fromStoredValue(it[AI_TRANSLATION_REQUEST_MODE].orEmpty())
    }
    val aiPagesPerRequest: Flow<Int> = context.dataStore.data.map {
        AiSettings.normalizePagesPerRequest(it[AI_PAGES_PER_REQUEST] ?: 10)
    }
    val aiConcurrentRequests: Flow<Int> = context.dataStore.data.map {
        AiSettings.normalizeConcurrentRequests(it[AI_CONCURRENT_REQUESTS] ?: AiSettings.defaults().concurrentRequests)
    }
    val aiMaxImagesPerRequest: Flow<Int> = context.dataStore.data.map {
        AiSettings.normalizeMaxImagesPerRequest(it[AI_MAX_IMAGES_PER_REQUEST] ?: 20)
    }
    val aiTimeoutSeconds: Flow<Int> = context.dataStore.data.map {
        AiSettings.normalizeTimeoutSeconds(it[AI_TIMEOUT_SECONDS] ?: 30)
    }
    val aiImageMaxEdge: Flow<AiImageMaxEdge> = context.dataStore.data.map {
        AiImageMaxEdge.fromStoredValue(it[AI_IMAGE_MAX_EDGE].orEmpty())
    }
    val aiSkipSoundEffects: Flow<Boolean> = context.dataStore.data.map { it[AI_SKIP_SOUND_EFFECTS] ?: false }
    val aiReasoningEffort: Flow<String> = context.dataStore.data.map { it[AI_REASONING_EFFORT] ?: "" }
    val aiCustomInstructions: Flow<String> = context.dataStore.data.map { it[AI_CUSTOM_INSTRUCTIONS] ?: "" }
    val aiTestModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_TEST_MODE_ENABLED] ?: false }
    val aiConfigurationTestPassed: Flow<Boolean> = context.dataStore.data.map { it[AI_CONFIGURATION_TEST_PASSED] ?: false }
    val aiTranslationDisplayMode: Flow<String> = context.dataStore.data.map { it[AI_TRANSLATION_DISPLAY_MODE] ?: "off" }
    val aiVerticalGlyphSpacingPercent: Flow<Int> = context.dataStore.data.map {
        (it[AI_VERTICAL_GLYPH_SPACING_PERCENT] ?: 86).coerceIn(70, 130)
    }

    init {
        runBlocking {
            val data = context.dataStore.data.first()
            if (data.contains(SERVER_URL) || data.contains(USERNAME) || data.contains(PASSWORD)) {
                context.dataStore.edit {
                    it.remove(SERVER_URL)
                    it.remove(USERNAME)
                    it.remove(PASSWORD)
                }
                secureAuthStore.clear()
            }
            val credentials = secureAuthStore.read()
            updateCredentials(credentials)
        }
    }

    val serverUrlBlocking: String get() = _serverUrl.value
    val usernameBlocking: String get() = _username.value
    val passwordBlocking: String get() = _password.value
    val alwaysIncognitoBlocking: Boolean get() = runBlocking { alwaysIncognito.first() }
    val coverCacheSizeBytesBlocking: Long get() = CacheSizeOption.fromMb(runBlocking { coverCacheSizeMb.first() }).bytes
    val readerCacheSizeBytesBlocking: Long get() = CacheSizeOption.fromMb(runBlocking { readerCacheSizeMb.first() }).bytes
    val clearCacheOnStartupBlocking: Boolean get() = runBlocking { clearCacheOnStartup.first() }

    suspend fun save(url: String, user: String, pass: String) {
        val cleanUrl = url.trimEnd('/')
        context.dataStore.edit {
            it.remove(SERVER_URL)
            it.remove(USERNAME)
            it.remove(PASSWORD)
        }
        secureAuthStore.save(cleanUrl, user, pass)
        updateCredentials(AuthCredentials(cleanUrl, user, pass))
    }

    suspend fun setAlwaysIncognito(value: Boolean) {
        context.dataStore.edit { it[ALWAYS_INCOGNITO] = value }
    }

    suspend fun setPreloadPages(value: Int) {
        context.dataStore.edit { it[PRELOAD_PAGES] = value }
    }

    suspend fun setReadingDirection(value: String) {
        context.dataStore.edit { it[READING_DIRECTION] = value }
    }

    suspend fun setPageFit(value: String) {
        context.dataStore.edit { it[PAGE_FIT] = value }
    }

    suspend fun setSplitLandscapePages(value: Boolean) {
        context.dataStore.edit { it[SPLIT_LANDSCAPE_PAGES] = value }
    }

    suspend fun setLandscapePageSplitOrder(value: LandscapePageSplitOrder) {
        context.dataStore.edit { it[LANDSCAPE_PAGE_SPLIT_ORDER] = value.storedValue }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[KEEP_SCREEN_ON] = value }
    }

    suspend fun setEinkMode(value: Boolean) {
        context.dataStore.edit { it[EINK_MODE] = value }
    }

    suspend fun setTapPageTurn(value: Boolean) {
        context.dataStore.edit { it[TAP_PAGE_TURN] = value }
    }

    suspend fun setShowBookThumbnails(value: Boolean) {
        context.dataStore.edit { it[SHOW_BOOK_THUMBNAILS] = value }
    }

    suspend fun setCoverCacheSizeMb(value: Int) {
        context.dataStore.edit { it[COVER_CACHE_SIZE_MB] = CacheSizeOption.fromMb(value).sizeMb }
    }

    suspend fun setReaderCacheSizeMb(value: Int) {
        context.dataStore.edit { it[READER_CACHE_SIZE_MB] = CacheSizeOption.fromMb(value).sizeMb }
    }

    suspend fun setClearCacheOnStartup(value: Boolean) {
        context.dataStore.edit { it[CLEAR_CACHE_ON_STARTUP] = value }
    }

    suspend fun setAiTranslationEnabled(value: Boolean) {
        context.dataStore.edit { it[AI_TRANSLATION_ENABLED] = value }
    }

    suspend fun setAiBaseUrl(value: String) {
        context.dataStore.edit { it[AI_BASE_URL] = value.trimEnd('/') }
    }

    suspend fun setAiModelName(value: String) {
        context.dataStore.edit { it[AI_MODEL_NAME] = value.trim() }
    }

    suspend fun setAiTargetLocale(value: String, languageName: String) {
        context.dataStore.edit {
            it[AI_TARGET_LOCALE] = value.trim()
            it[AI_TARGET_LANGUAGE_NAME] = languageName.trim()
        }
    }

    suspend fun setAiSourceTextProfile(value: AiSourceTextProfile) {
        context.dataStore.edit { it[AI_SOURCE_TEXT_PROFILE] = value.storedValue }
    }

    suspend fun setAiLocalModelSource(value: AiLocalModelSource) {
        context.dataStore.edit { it[AI_LOCAL_MODEL_SOURCE] = value.storedValue }
    }

    suspend fun setAiModelCollectionId(value: String) {
        context.dataStore.edit { it[AI_MODEL_COLLECTION_ID] = value.trim() }
    }

    suspend fun setAiModelRevision(value: String) {
        context.dataStore.edit { it[AI_MODEL_REVISION] = value.trim().ifBlank { "main" } }
    }

    suspend fun setAiDownloadLatestModel(value: Boolean) {
        context.dataStore.edit { it[AI_DOWNLOAD_LATEST_MODEL] = value }
    }

    suspend fun setAiAutoSelectDeviceTier(value: Boolean) {
        context.dataStore.edit { it[AI_AUTO_SELECT_DEVICE_TIER] = value }
    }

    suspend fun setAiImageTransport(value: AiImageTransport) {
        context.dataStore.edit { it[AI_IMAGE_TRANSPORT] = value.storedValue }
    }

    suspend fun setAiTranslationRequestMode(value: AiTranslationRequestMode) {
        context.dataStore.edit { it[AI_TRANSLATION_REQUEST_MODE] = value.storedValue }
    }

    suspend fun setAiPagesPerRequest(value: Int) {
        context.dataStore.edit { it[AI_PAGES_PER_REQUEST] = AiSettings.normalizePagesPerRequest(value) }
    }

    suspend fun setAiConcurrentRequests(value: Int) {
        context.dataStore.edit { it[AI_CONCURRENT_REQUESTS] = AiSettings.normalizeConcurrentRequests(value) }
    }

    suspend fun setAiMaxImagesPerRequest(value: Int) {
        context.dataStore.edit { it[AI_MAX_IMAGES_PER_REQUEST] = AiSettings.normalizeMaxImagesPerRequest(value) }
    }

    suspend fun setAiTimeoutSeconds(value: Int) {
        context.dataStore.edit { it[AI_TIMEOUT_SECONDS] = AiSettings.normalizeTimeoutSeconds(value) }
    }

    suspend fun setAiImageMaxEdge(value: AiImageMaxEdge) {
        context.dataStore.edit { it[AI_IMAGE_MAX_EDGE] = value.storedValue }
    }

    suspend fun setAiSkipSoundEffects(value: Boolean) {
        context.dataStore.edit { it[AI_SKIP_SOUND_EFFECTS] = value }
    }

    suspend fun setAiReasoningEffort(value: String) {
        context.dataStore.edit { it[AI_REASONING_EFFORT] = value.trim() }
    }

    suspend fun setAiCustomInstructions(value: String) {
        context.dataStore.edit { it[AI_CUSTOM_INSTRUCTIONS] = value }
    }

    suspend fun setAiTestModeEnabled(value: Boolean) {
        context.dataStore.edit { it[AI_TEST_MODE_ENABLED] = value }
    }

    suspend fun setAiConfigurationTestPassed(value: Boolean) {
        context.dataStore.edit { it[AI_CONFIGURATION_TEST_PASSED] = value }
    }

    suspend fun setAiTranslationDisplayMode(value: String) {
        val normalized = if (value == "on") "on" else "off"
        context.dataStore.edit { it[AI_TRANSLATION_DISPLAY_MODE] = normalized }
    }

    suspend fun setAiVerticalGlyphSpacingPercent(value: Int) {
        context.dataStore.edit { it[AI_VERTICAL_GLYPH_SPACING_PERCENT] = value.coerceIn(70, 130) }
    }

    private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    private val APP_LOCK_TIMEOUT = intPreferencesKey("app_lock_timeout_minutes")

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[APP_LOCK_ENABLED] ?: false }
    val appLockTimeout: Flow<Int> = context.dataStore.data.map { it[APP_LOCK_TIMEOUT] ?: 0 }
    val appLockEnabledBlocking: Boolean get() = runBlocking { appLockEnabled.first() }
    val appLockTimeoutBlocking: Int get() = runBlocking { appLockTimeout.first() }

    suspend fun setAppLockEnabled(value: Boolean) {
        context.dataStore.edit { it[APP_LOCK_ENABLED] = value }
    }

    suspend fun setAppLockTimeout(minutes: Int) {
        context.dataStore.edit { it[APP_LOCK_TIMEOUT] = minutes }
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(SERVER_URL)
            it.remove(USERNAME)
            it.remove(PASSWORD)
        }
        secureAuthStore.clear()
        updateCredentials(AuthCredentials("", "", ""))
    }

    private fun updateCredentials(credentials: AuthCredentials) {
        _serverUrl.value = credentials.serverUrl
        _username.value = credentials.username
        _password.value = credentials.password
    }

    private fun systemTargetLocale(): String = Locale.getDefault().toLanguageTag()

    private fun systemTargetLanguageName(): String = Locale.getDefault().displayName
}
