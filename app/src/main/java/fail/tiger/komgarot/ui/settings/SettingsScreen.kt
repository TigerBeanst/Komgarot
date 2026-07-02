package fail.tiger.komgarot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import fail.tiger.komgarot.BuildConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fail.tiger.komgarot.KomgarotApp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiLocalModelSource
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.BookDownloadIndex
import fail.tiger.komgarot.data.local.CacheClearTarget
import fail.tiger.komgarot.data.local.CacheMaintenance
import fail.tiger.komgarot.data.local.CacheSizeOption
import fail.tiger.komgarot.data.local.CachedBookEntry
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.local.SecureAiSettings
import fail.tiger.komgarot.data.local.SecureWebDavSettings
import fail.tiger.komgarot.data.repository.AiLocalModelTier
import fail.tiger.komgarot.data.repository.AppUpdateRepository
import fail.tiger.komgarot.data.repository.GithubRelease
import fail.tiger.komgarot.data.repository.defaultAiLocalModelPlan
import fail.tiger.komgarot.data.repository.deviceProfile
import fail.tiger.komgarot.data.repository.recommendAiLocalModelTier
import fail.tiger.komgarot.data.repository.s3ImageUrlConfigOrNull
import fail.tiger.komgarot.data.repository.testAiS3ImageUrlUpload
import fail.tiger.komgarot.ui.metadata.openExternalUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, prefs: AuthPreferences, aiTranslationAvailable: Boolean = BuildConfig.AI_TRANSLATION_AVAILABLE) {
    SettingsContent(
        prefs = prefs,
        aiTranslationAvailable = aiTranslationAvailable,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun MeScreen(
    userEmail: String?,
    isAdmin: Boolean,
    aiTranslationAvailable: Boolean = BuildConfig.AI_TRANSLATION_AVAILABLE,
    onCachedBooksClick: () -> Unit,
    onAiTranslationTasksClick: () -> Unit,
    appUpdateRepository: AppUpdateRepository = AppUpdateRepository(),
    onAdminClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.me)) })
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text(userEmail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown)) },
                supportingContent = { Text(stringResource(if (isAdmin) R.string.admin_admin_role else R.string.user_role)) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.cached_books)) },
                supportingContent = { Text(stringResource(R.string.cached_books_entry_desc)) },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onCachedBooksClick)
            )
            if (aiTranslationAvailable) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.ai_translation_tasks)) },
                    supportingContent = { Text(stringResource(R.string.ai_translation_tasks_entry_desc)) },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onAiTranslationTasksClick)
                )
            }
            if (isAdmin) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.admin)) },
                    supportingContent = { Text(stringResource(R.string.admin_entry_desc)) },
                    leadingContent = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onAdminClick)
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings)) },
                supportingContent = { Text(stringResource(R.string.settings_entry_desc)) },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onSettingsClick)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.logout)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onLogout)
            )
            Spacer(Modifier.weight(1f, fill = true))
            HorizontalDivider()
            AboutSection(appUpdateRepository = appUpdateRepository)
        }
    }
}

private enum class SettingsPage(val titleRes: Int, val icon: ImageVector) {
    CACHE(R.string.settings_section_cache, Icons.Default.Cached),
    READING(R.string.settings_section_reading, Icons.AutoMirrored.Filled.MenuBook),
    AI(R.string.settings_section_ai_translation, Icons.Default.AutoAwesome),
    MODELS(R.string.settings_section_ai_models, Icons.Default.Download),
    WEBDAV(R.string.settings_section_webdav_backup, Icons.Default.CloudUpload),
    SECURITY(R.string.settings_section_security, Icons.Default.Security)
}

private val aiOnlySettingsPages = setOf(SettingsPage.AI, SettingsPage.MODELS)

private data class AiTargetLanguageOption(
    val locale: String,
    val languageNameRes: Int
)

private val aiTargetLanguageOptions = listOf(
    AiTargetLanguageOption("zh-CN", R.string.settings_ai_language_zh_cn),
    AiTargetLanguageOption("zh-TW", R.string.settings_ai_language_zh_tw),
    AiTargetLanguageOption("en-US", R.string.settings_ai_language_en_us),
    AiTargetLanguageOption("ja-JP", R.string.settings_ai_language_ja_jp),
    AiTargetLanguageOption("ko-KR", R.string.settings_ai_language_ko_kr)
)

private data class AiSourceTextProfileOption(
    val profile: AiSourceTextProfile,
    val labelRes: Int
)

private val aiSourceTextProfileOptions = listOf(
    AiSourceTextProfileOption(AiSourceTextProfile.AUTO, R.string.settings_ai_source_text_profile_auto),
    AiSourceTextProfileOption(AiSourceTextProfile.JAPANESE_MANGA, R.string.settings_ai_source_text_profile_japanese_manga),
    AiSourceTextProfileOption(AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON, R.string.settings_ai_source_text_profile_korean_horizontal_webtoon)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
private fun SettingsContent(
    prefs: AuthPreferences,
    aiTranslationAvailable: Boolean,
    topBar: @Composable () -> Unit,
    headerContent: @Composable ColumnScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheSize by remember { mutableStateOf(CacheSizeUi.loading()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showCoverCacheSizeDialog by remember { mutableStateOf(false) }
    var showReaderCacheSizeDialog by remember { mutableStateOf(false) }
    val alwaysIncognito by prefs.alwaysIncognito.collectAsStateWithLifecycle(initialValue = false)
    val preloadPages by prefs.preloadPages.collectAsStateWithLifecycle(initialValue = 5)
    val readingDirection by prefs.readingDirection.collectAsStateWithLifecycle(initialValue = "LTR")
    val pageFit by prefs.pageFit.collectAsStateWithLifecycle(initialValue = "FIT")
    val keepScreenOn by prefs.keepScreenOn.collectAsStateWithLifecycle(initialValue = true)
    val einkMode by prefs.einkMode.collectAsStateWithLifecycle(initialValue = false)
    val tapPageTurn by prefs.tapPageTurn.collectAsStateWithLifecycle(initialValue = false)
    val coverCacheSizeMb by prefs.coverCacheSizeMb.collectAsStateWithLifecycle(initialValue = CacheSizeOption.default.sizeMb)
    val readerCacheSizeMb by prefs.readerCacheSizeMb.collectAsStateWithLifecycle(initialValue = CacheSizeOption.default.sizeMb)
    val clearCacheOnStartup by prefs.clearCacheOnStartup.collectAsStateWithLifecycle(initialValue = false)
    val aiTranslationEnabled by prefs.aiTranslationEnabled.collectAsStateWithLifecycle(initialValue = false)
    val aiBaseUrl by prefs.aiBaseUrl.collectAsStateWithLifecycle(initialValue = "")
    val aiModelName by prefs.aiModelName.collectAsStateWithLifecycle(initialValue = "")
    val aiTargetLocale by prefs.aiTargetLocale.collectAsStateWithLifecycle(initialValue = "")
    val aiTargetLanguageName by prefs.aiTargetLanguageName.collectAsStateWithLifecycle(initialValue = "")
    val aiSourceTextProfile by prefs.aiSourceTextProfile.collectAsStateWithLifecycle(initialValue = AiSourceTextProfile.AUTO)
    val aiLocalModelSource by prefs.aiLocalModelSource.collectAsStateWithLifecycle(initialValue = AiLocalModelSource.HUGGING_FACE)
    val aiModelCollectionId by prefs.aiModelCollectionId.collectAsStateWithLifecycle(initialValue = "PaddlePaddle/pp-ocrv6")
    val aiModelRevision by prefs.aiModelRevision.collectAsStateWithLifecycle(initialValue = "main")
    val aiDownloadLatestModel by prefs.aiDownloadLatestModel.collectAsStateWithLifecycle(initialValue = true)
    val aiAutoSelectDeviceTier by prefs.aiAutoSelectDeviceTier.collectAsStateWithLifecycle(initialValue = true)
    val aiImageTransport by prefs.aiImageTransport.collectAsStateWithLifecycle(initialValue = AiImageTransport.BASE64)
    val aiPagesPerRequest by prefs.aiPagesPerRequest.collectAsStateWithLifecycle(initialValue = 10)
    val aiConcurrentRequests by prefs.aiConcurrentRequests.collectAsStateWithLifecycle(initialValue = 3)
    val aiMaxImagesPerRequest by prefs.aiMaxImagesPerRequest.collectAsStateWithLifecycle(initialValue = 20)
    val aiTimeoutSeconds by prefs.aiTimeoutSeconds.collectAsStateWithLifecycle(initialValue = 30)
    val aiVerticalGlyphSpacingPercent by prefs.aiVerticalGlyphSpacingPercent.collectAsStateWithLifecycle(initialValue = 92)
    val aiTestModeEnabled by prefs.aiTestModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val app = context.applicationContext as? KomgarotApp
    var secureAiSettings by remember { mutableStateOf(app?.secureAiSettingsStore?.read() ?: SecureAiSettings()) }
    var secureWebDavSettings by remember { mutableStateOf(app?.secureWebDavSettingsStore?.read() ?: SecureWebDavSettings()) }
    var showPreloadDialog by remember { mutableStateOf(false) }
    var showReadingDialog by remember { mutableStateOf(false) }
    var showFitDialog by remember { mutableStateOf(false) }
    var showAiBaseUrlDialog by remember { mutableStateOf(false) }
    var showAiApiKeyDialog by remember { mutableStateOf(false) }
    var showAiModelDialog by remember { mutableStateOf(false) }
    var showAiTargetLanguageMenu by remember { mutableStateOf(false) }
    var showAiSourceTextProfileDialog by remember { mutableStateOf(false) }
    var showAiModelSourceDialog by remember { mutableStateOf(false) }
    var showAiModelCollectionDialog by remember { mutableStateOf(false) }
    var showAiModelRevisionDialog by remember { mutableStateOf(false) }
    var showAiImageTransportDialog by remember { mutableStateOf(false) }
    var showAiS3EndpointDialog by remember { mutableStateOf(false) }
    var showAiS3RegionDialog by remember { mutableStateOf(false) }
    var showAiS3BucketDialog by remember { mutableStateOf(false) }
    var showAiS3AccessKeyDialog by remember { mutableStateOf(false) }
    var showAiS3SecretKeyDialog by remember { mutableStateOf(false) }
    var showAiS3PathPrefixDialog by remember { mutableStateOf(false) }
    var showAiS3TtlDialog by remember { mutableStateOf(false) }
    var aiS3Testing by remember { mutableStateOf(false) }
    var showAiPagesPerRequestDialog by remember { mutableStateOf(false) }
    var showAiConcurrencyDialog by remember { mutableStateOf(false) }
    var showAiMaxImagesPerRequestDialog by remember { mutableStateOf(false) }
    var showAiTimeoutDialog by remember { mutableStateOf(false) }
    var showAiVerticalGlyphSpacingDialog by remember { mutableStateOf(false) }
    var showDeleteLocalModelsDialog by remember { mutableStateOf(false) }
    var showWebDavUrlDialog by remember { mutableStateOf(false) }
    var showWebDavUsernameDialog by remember { mutableStateOf(false) }
    var showWebDavPasswordDialog by remember { mutableStateOf(false) }
    var localModelDownloading by remember { mutableStateOf(false) }
    var localModelsInstalled by remember { mutableStateOf(false) }
    fun saveS3Settings(next: SecureAiSettings) {
        app?.secureAiSettingsStore?.saveS3Settings(next)
        secureAiSettings = app?.secureAiSettingsStore?.read() ?: next
    }
    val appLockEnabled by prefs.appLockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val appLockTimeout by prefs.appLockTimeout.collectAsStateWithLifecycle(initialValue = 0)
    var showLockTimeoutDialog by remember { mutableStateOf(false) }
    var selectedSettingsPage by remember { mutableStateOf<SettingsPage?>(null) }

    LaunchedEffect(Unit) {
        cacheSize = getCacheSize(context)
    }
    val currentDeviceProfile = remember(context) { deviceProfile(context) }
    val currentDeviceTier = remember(currentDeviceProfile) { recommendAiLocalModelTier(currentDeviceProfile) }
    val recommendedLocalModelPlan = remember(aiModelCollectionId, aiModelRevision, aiAutoSelectDeviceTier, currentDeviceTier) {
        defaultAiLocalModelPlan(
            collectionId = aiModelCollectionId,
            revision = aiModelRevision,
            tier = if (aiAutoSelectDeviceTier) currentDeviceTier else AiLocalModelTier.LOW
        )
    }
    LaunchedEffect(app, recommendedLocalModelPlan, aiModelRevision) {
        localModelsInstalled = app?.aiLocalModelRepository?.isPlanInstalled(recommendedLocalModelPlan, aiModelRevision) == true
    }

    Scaffold(topBar = topBar) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            headerContent()
            val page = selectedSettingsPage
            if (page == null) {
                SettingsCategoryList(aiTranslationAvailable = aiTranslationAvailable, onSelect = { selectedSettingsPage = it })
            } else {
                SettingsPageContent(
                    page = page,
                    onBack = { selectedSettingsPage = null }
                ) {
            when (page) {
                SettingsPage.CACHE -> {
            SettingsSectionHeader(stringResource(R.string.settings_section_cache))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
                supportingContent = { Text(cacheSize.displayText()) },
                modifier = Modifier.clickable { showClearDialog = true }
            )
            CacheHealthBreakdown(cacheSize)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_cover_cache_size)) },
                supportingContent = { Text(formatCacheSize(CacheSizeOption.fromMb(coverCacheSizeMb).bytes)) },
                modifier = Modifier.clickable { showCoverCacheSizeDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reader_cache_size)) },
                supportingContent = { Text(formatCacheSize(CacheSizeOption.fromMb(readerCacheSizeMb).bytes)) },
                modifier = Modifier.clickable { showReaderCacheSizeDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_cache_on_startup)) },
                supportingContent = { Text(stringResource(R.string.settings_clear_cache_on_startup_desc)) },
                trailingContent = {
                    Switch(
                        checked = clearCacheOnStartup,
                        onCheckedChange = { scope.launch { prefs.setClearCacheOnStartup(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setClearCacheOnStartup(!clearCacheOnStartup) }
                }
            )
                }
                SettingsPage.READING -> {
            SettingsSectionHeader(stringResource(R.string.settings_section_reading))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_eink_mode)) },
                supportingContent = { Text(stringResource(R.string.settings_eink_mode_desc)) },
                trailingContent = {
                    Switch(
                        checked = einkMode,
                        onCheckedChange = { scope.launch { prefs.setEinkMode(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setEinkMode(!einkMode) }
                }
            )
            if (!einkMode) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_tap_page_turn)) },
                    supportingContent = { Text(stringResource(R.string.settings_tap_page_turn_desc)) },
                    trailingContent = {
                        Switch(
                            checked = tapPageTurn,
                            onCheckedChange = { scope.launch { prefs.setTapPageTurn(it) } }
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch { prefs.setTapPageTurn(!tapPageTurn) }
                    }
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_always_incognito)) },
                supportingContent = { Text(stringResource(R.string.settings_always_incognito_desc)) },
                trailingContent = {
                    Switch(
                        checked = alwaysIncognito,
                        onCheckedChange = { scope.launch { prefs.setAlwaysIncognito(it) } }
                    )
                },
                modifier = Modifier.clickable { scope.launch { prefs.setAlwaysIncognito(!alwaysIncognito) } }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_preload_pages)) },
                supportingContent = { Text(stringResource(R.string.settings_preload_pages_desc, preloadPages)) },
                modifier = Modifier.clickable { showPreloadDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reading_direction)) },
                supportingContent = {
                    Text(stringResource(if (readingDirection == "RTL") R.string.settings_reading_rtl_desc else R.string.settings_reading_ltr_desc))
                },
                modifier = Modifier.clickable { showReadingDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_page_fit)) },
                supportingContent = {
                    Text(stringResource(if (pageFit == "WIDTH") R.string.settings_page_fit_width else R.string.settings_page_fit_fit))
                },
                modifier = Modifier.clickable { showFitDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
                supportingContent = { Text(stringResource(R.string.settings_keep_screen_on_desc)) },
                trailingContent = {
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { scope.launch { prefs.setKeepScreenOn(it) } }
                    )
                },
                modifier = Modifier.clickable { scope.launch { prefs.setKeepScreenOn(!keepScreenOn) } }
            )
                }
                SettingsPage.AI -> {
            SettingsSectionHeader(stringResource(R.string.settings_section_ai_translation))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_translation_enabled)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_translation_enabled_desc)) },
                trailingContent = {
                    Switch(
                        checked = aiTranslationEnabled,
                        onCheckedChange = { scope.launch { prefs.setAiTranslationEnabled(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setAiTranslationEnabled(!aiTranslationEnabled) }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_base_url)) },
                supportingContent = { Text(aiBaseUrl.ifBlank { stringResource(R.string.empty_dash) }) },
                modifier = Modifier.clickable { showAiBaseUrlDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_api_key)) },
                supportingContent = {
                    Text(stringResource(if (secureAiSettings.apiKey.isBlank()) R.string.settings_not_configured else R.string.settings_configured))
                },
                modifier = Modifier.clickable { showAiApiKeyDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_model_name)) },
                supportingContent = { Text(aiModelName.ifBlank { stringResource(R.string.settings_ai_model_requires_vision) }) },
                modifier = Modifier.clickable { showAiModelDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_target_language)) },
                supportingContent = { Text("$aiTargetLanguageName · $aiTargetLocale") },
                modifier = Modifier.clickable { showAiTargetLanguageMenu = true }
            )
            val sourceTextProfileOption = aiSourceTextProfileOptions.firstOrNull { it.profile == aiSourceTextProfile }
                ?: aiSourceTextProfileOptions.first()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_source_text_profile)) },
                supportingContent = { Text(stringResource(sourceTextProfileOption.labelRes)) },
                modifier = Modifier.clickable { showAiSourceTextProfileDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.ai_translation_mode_local_detection)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_local_detection_pipeline_desc)) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_image_transport)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (aiImageTransport == AiImageTransport.IMAGE_URL) {
                                R.string.settings_ai_image_transport_url
                            } else {
                                R.string.settings_ai_image_transport_base64
                            }
                        )
                    )
                },
                modifier = Modifier.clickable { showAiImageTransportDialog = true }
            )
            if (aiImageTransport == AiImageTransport.IMAGE_URL) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_endpoint)) },
                    supportingContent = {
                        Text(
                            secureAiSettings.s3Endpoint.ifBlank { stringResource(R.string.settings_ai_s3_config_required) }
                        )
                    },
                    modifier = Modifier.clickable { showAiS3EndpointDialog = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_region)) },
                    supportingContent = { Text(secureAiSettings.s3Region.ifBlank { stringResource(R.string.empty_dash) }) },
                    modifier = Modifier.clickable { showAiS3RegionDialog = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_bucket)) },
                    supportingContent = { Text(secureAiSettings.s3Bucket.ifBlank { stringResource(R.string.empty_dash) }) },
                    modifier = Modifier.clickable { showAiS3BucketDialog = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_access_key)) },
                    supportingContent = { Text(stringResource(if (secureAiSettings.s3AccessKey.isBlank()) R.string.settings_not_configured else R.string.settings_configured)) },
                    modifier = Modifier.clickable { showAiS3AccessKeyDialog = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_secret_key)) },
                    supportingContent = { Text(stringResource(if (secureAiSettings.s3SecretKey.isBlank()) R.string.settings_not_configured else R.string.settings_configured)) },
                    modifier = Modifier.clickable { showAiS3SecretKeyDialog = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_path_prefix)) },
                    supportingContent = { Text(secureAiSettings.s3PathPrefix.ifBlank { "ai-temp" }) },
                    modifier = Modifier.clickable { showAiS3PathPrefixDialog = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_ttl_seconds)) },
                    supportingContent = { Text(secureAiSettings.s3TtlSeconds.toString()) },
                    modifier = Modifier.clickable { showAiS3TtlDialog = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_path_style)) },
                    trailingContent = {
                        Switch(
                            checked = secureAiSettings.s3PathStyle,
                            onCheckedChange = { saveS3Settings(secureAiSettings.copy(s3PathStyle = it)) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_s3_test_upload)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (aiS3Testing) R.string.loading else R.string.settings_ai_s3_test_upload_desc
                            )
                        )
                    },
                    modifier = Modifier.clickable(enabled = !aiS3Testing) {
                        val config = secureAiSettings.s3ImageUrlConfigOrNull()
                        if (config == null) {
                            Toast.makeText(context, context.getString(R.string.settings_ai_s3_config_required), Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        scope.launch {
                            aiS3Testing = true
                            val result = withContext(Dispatchers.IO) { testAiS3ImageUrlUpload(config) }
                            aiS3Testing = false
                            val message = if (result.isSuccess) {
                                context.getString(R.string.settings_ai_s3_test_success)
                            } else {
                                context.getString(
                                    R.string.settings_ai_s3_test_failed,
                                    result.exceptionOrNull()?.message.orEmpty()
                                )
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_pages_per_request)) },
                supportingContent = { Text(aiPagesPerRequest.toString()) },
                modifier = Modifier.clickable { showAiPagesPerRequestDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_max_images_per_request)) },
                supportingContent = { Text(aiMaxImagesPerRequest.toString()) },
                modifier = Modifier.clickable { showAiMaxImagesPerRequestDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_concurrent_requests)) },
                supportingContent = { Text(aiConcurrentRequests.toString()) },
                modifier = Modifier.clickable { showAiConcurrencyDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_timeout)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_timeout_seconds, aiTimeoutSeconds)) },
                modifier = Modifier.clickable { showAiTimeoutDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_vertical_glyph_spacing)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_vertical_glyph_spacing_percent, aiVerticalGlyphSpacingPercent)) },
                modifier = Modifier.clickable { showAiVerticalGlyphSpacingDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_test_mode)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_model_requires_vision)) },
                trailingContent = {
                    Switch(
                        checked = aiTestModeEnabled,
                        onCheckedChange = { scope.launch { prefs.setAiTestModeEnabled(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setAiTestModeEnabled(!aiTestModeEnabled) }
                }
            )
                }
                SettingsPage.MODELS -> {
            SettingsSectionHeader(stringResource(R.string.settings_section_ai_models))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_model_source)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_model_source_huggingface)) },
                modifier = Modifier.clickable { showAiModelSourceDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_model_collection)) },
                supportingContent = { Text(aiModelCollectionId) },
                modifier = Modifier.clickable { showAiModelCollectionDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_model_revision)) },
                supportingContent = {
                    Text(if (aiDownloadLatestModel) stringResource(R.string.settings_ai_model_download_latest) else aiModelRevision)
                },
                modifier = Modifier.clickable { showAiModelRevisionDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_model_download_policy)) },
                supportingContent = {
                    Text(
                        if (aiDownloadLatestModel) {
                            stringResource(R.string.settings_ai_model_download_latest)
                        } else {
                            stringResource(R.string.settings_ai_model_download_fixed)
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = aiDownloadLatestModel,
                        onCheckedChange = { scope.launch { prefs.setAiDownloadLatestModel(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setAiDownloadLatestModel(!aiDownloadLatestModel) }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_model_auto_select)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_model_auto_select_desc)) },
                trailingContent = {
                    Switch(
                        checked = aiAutoSelectDeviceTier,
                        onCheckedChange = { scope.launch { prefs.setAiAutoSelectDeviceTier(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setAiAutoSelectDeviceTier(!aiAutoSelectDeviceTier) }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_model_device_tier)) },
                supportingContent = { Text(stringResource(deviceTierLabelRes(currentDeviceTier))) }
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(if (localModelsInstalled) R.string.settings_ai_model_delete_now else R.string.settings_ai_model_download_now))
                },
                supportingContent = {
                    Text(
                        stringResource(if (localModelsInstalled) R.string.settings_ai_model_delete_now_desc else R.string.settings_ai_model_download_now_desc) +
                            " · " +
                            stringResource(if (localModelsInstalled) R.string.settings_ai_model_installed else R.string.settings_ai_model_missing)
                    )
                },
                trailingContent = {
                    if (localModelDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (localModelsInstalled) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_ai_model_delete_now))
                    }
                },
                modifier = Modifier.clickable(enabled = !localModelDownloading) {
                    if (localModelsInstalled) {
                        showDeleteLocalModelsDialog = true
                        return@clickable
                    }
                    val repository = app?.aiLocalModelRepository ?: return@clickable
                    scope.launch {
                        localModelDownloading = true
                        val result = repository.downloadPlan(recommendedLocalModelPlan, aiModelRevision)
                        localModelDownloading = false
                        localModelsInstalled = result.isSuccess
                        val message = if (result.isSuccess) {
                            context.getString(R.string.settings_ai_model_download_success)
                        } else {
                            context.getString(R.string.settings_ai_model_download_failed) + ": " + result.exceptionOrNull()?.message.orEmpty()
                        }
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
                SettingsPage.WEBDAV -> {
            SettingsSectionHeader(stringResource(R.string.settings_section_webdav_backup))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webdav_url)) },
                supportingContent = { Text(if (secureWebDavSettings.url.isBlank()) stringResource(R.string.empty_dash) else secureWebDavSettings.url) },
                modifier = Modifier.clickable { showWebDavUrlDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webdav_username)) },
                supportingContent = {
                    Text(stringResource(if (secureWebDavSettings.username.isBlank()) R.string.settings_not_configured else R.string.settings_configured))
                },
                modifier = Modifier.clickable { showWebDavUsernameDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webdav_password)) },
                supportingContent = {
                    Text(stringResource(if (secureWebDavSettings.password.isBlank()) R.string.settings_not_configured else R.string.settings_configured))
                },
                modifier = Modifier.clickable { showWebDavPasswordDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webdav_backup_scope)) },
                supportingContent = { Text(stringResource(R.string.settings_webdav_backup_excludes_credentials)) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webdav_backup_now)) },
                supportingContent = { Text(stringResource(R.string.settings_webdav_backup_now_desc)) },
                modifier = Modifier.clickable {
                    scope.launch {
                        val result = app?.webDavBackupRepository?.backupNow()
                        val message = if (result?.isSuccess == true) {
                            context.getString(R.string.settings_webdav_backup_success)
                        } else {
                            context.getString(R.string.operation_failed)
                        }
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
                }
                SettingsPage.SECURITY -> {
            SettingsSectionHeader(stringResource(R.string.settings_section_security))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_app_lock)) },
                supportingContent = { Text(stringResource(R.string.settings_app_lock_desc)) },
                trailingContent = {
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = { scope.launch { prefs.setAppLockEnabled(it) } }
                    )
                },
                modifier = Modifier.clickable { scope.launch { prefs.setAppLockEnabled(!appLockEnabled) } }
            )
            if (appLockEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_lock_timeout)) },
                    supportingContent = {
                        Text(
                            if (appLockTimeout == 0) {
                                stringResource(R.string.settings_lock_every_time)
                            } else {
                                stringResource(R.string.settings_lock_after_minutes, appLockTimeout)
                            }
                        )
                    },
                    modifier = Modifier.clickable { showLockTimeoutDialog = true }
                )
            }
                }
            }
                }
            }
        }
    }

    if (showDeleteLocalModelsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteLocalModelsDialog = false },
            title = { Text(stringResource(R.string.settings_ai_model_delete_title)) },
            text = { Text(stringResource(R.string.settings_ai_model_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteLocalModelsDialog = false
                    scope.launch {
                        localModelDownloading = true
                        val deleted = app?.aiLocalModelRepository?.deletePlan(recommendedLocalModelPlan, aiModelRevision) == true
                        localModelsInstalled = app?.aiLocalModelRepository?.isPlanInstalled(recommendedLocalModelPlan, aiModelRevision) == true
                        localModelDownloading = false
                        val message = if (deleted) {
                            context.getString(R.string.settings_ai_model_delete_success)
                        } else {
                            context.getString(R.string.settings_ai_model_delete_failed)
                        }
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.settings_ai_model_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteLocalModelsDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showLockTimeoutDialog) {
        var sliderValue by remember { mutableFloatStateOf(appLockTimeout.toFloat()) }
        AlertDialog(
            onDismissRequest = { showLockTimeoutDialog = false },
            title = { Text(stringResource(R.string.settings_lock_timeout)) },
            text = {
                Column {
                    Text(
                        if (sliderValue.toInt() == 0) {
                            stringResource(R.string.settings_lock_every_time)
                        } else {
                            stringResource(R.string.settings_lock_after_minutes, sliderValue.toInt())
                        }
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..60f,
                        steps = 11
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.setAppLockTimeout(sliderValue.toInt()) }
                    showLockTimeoutDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLockTimeoutDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showPreloadDialog) {
        var sliderValue by remember { mutableFloatStateOf(preloadPages.toFloat()) }
        AlertDialog(
            onDismissRequest = { showPreloadDialog = false },
            title = { Text(stringResource(R.string.settings_preload_pages)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_preload_slider, sliderValue.toInt()))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.setPreloadPages(sliderValue.toInt()) }
                    showPreloadDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPreloadDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showReadingDialog) {
        AlertDialog(
            onDismissRequest = { showReadingDialog = false },
            title = { Text(stringResource(R.string.settings_reading_direction)) },
            text = {
                Column {
                    RadioOption("LTR", stringResource(R.string.settings_reading_ltr), readingDirection) {
                        scope.launch { prefs.setReadingDirection(it) }
                        showReadingDialog = false
                    }
                    RadioOption("RTL", stringResource(R.string.settings_reading_rtl), readingDirection) {
                        scope.launch { prefs.setReadingDirection(it) }
                        showReadingDialog = false
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showFitDialog) {
        AlertDialog(
            onDismissRequest = { showFitDialog = false },
            title = { Text(stringResource(R.string.settings_page_fit)) },
            text = {
                Column {
                    RadioOption("FIT", stringResource(R.string.settings_page_fit_fit), pageFit) {
                        scope.launch { prefs.setPageFit(it) }
                        showFitDialog = false
                    }
                    RadioOption("WIDTH", stringResource(R.string.settings_page_fit_width), pageFit) {
                        scope.launch { prefs.setPageFit(it) }
                        showFitDialog = false
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = {
                Column {
                    CacheClearOption(
                        label = stringResource(R.string.settings_clear_cache_all),
                        onClick = {
                            scope.launch {
                                CacheMaintenance.clear(context, CacheClearTarget.All)
                                cacheSize = getCacheSize(context)
                                showClearDialog = false
                            }
                        }
                    )
                    CacheClearOption(
                        label = stringResource(R.string.settings_clear_cache_covers),
                        onClick = {
                            scope.launch {
                                CacheMaintenance.clear(context, CacheClearTarget.Covers)
                                cacheSize = getCacheSize(context)
                                showClearDialog = false
                            }
                        }
                    )
                    CacheClearOption(
                        label = stringResource(R.string.settings_clear_cache_reader_pages),
                        onClick = {
                            scope.launch {
                                CacheMaintenance.clear(context, CacheClearTarget.ReaderPages)
                                cacheSize = getCacheSize(context)
                                showClearDialog = false
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCoverCacheSizeDialog) {
        CacheSizeDialog(
            title = stringResource(R.string.settings_cover_cache_size),
            selectedSizeMb = coverCacheSizeMb,
            onSelect = {
                scope.launch { prefs.setCoverCacheSizeMb(it) }
                showCoverCacheSizeDialog = false
            },
            onDismiss = { showCoverCacheSizeDialog = false }
        )
    }

    if (showReaderCacheSizeDialog) {
        CacheSizeDialog(
            title = stringResource(R.string.settings_reader_cache_size),
            selectedSizeMb = readerCacheSizeMb,
            onSelect = {
                scope.launch {
                    prefs.setReaderCacheSizeMb(it)
                    ReaderPageCache.prune(context, CacheSizeOption.fromMb(it).bytes)
                    cacheSize = getCacheSize(context)
                }
                showReaderCacheSizeDialog = false
            },
            onDismiss = { showReaderCacheSizeDialog = false }
        )
    }

    if (showAiBaseUrlDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_base_url),
            initialValue = aiBaseUrl,
            placeholder = stringResource(R.string.settings_ai_base_url_placeholder),
            onSave = { scope.launch { prefs.setAiBaseUrl(it) } },
            onDismiss = { showAiBaseUrlDialog = false }
        )
    }

    if (showAiModelDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_model_name),
            initialValue = aiModelName,
            onSave = { scope.launch { prefs.setAiModelName(it) } },
            onDismiss = { showAiModelDialog = false }
        )
    }

    if (showAiApiKeyDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_api_key),
            initialValue = secureAiSettings.apiKey,
            singleLine = false,
            minLines = 1,
            maxLines = 6,
            onSave = {
                app?.secureAiSettingsStore?.saveApiKey(it)
                secureAiSettings = app?.secureAiSettingsStore?.read() ?: secureAiSettings.copy(apiKey = it)
            },
            onDismiss = { showAiApiKeyDialog = false }
        )
    }

    if (showAiTargetLanguageMenu) {
        AlertDialog(
            onDismissRequest = { showAiTargetLanguageMenu = false },
            title = { Text(stringResource(R.string.settings_ai_target_language)) },
            text = {
                Column {
                    aiTargetLanguageOptions.forEach { option ->
                        val label = stringResource(option.languageNameRes)
                        RadioOption(option.locale, "$label · ${option.locale}", aiTargetLocale) {
                            scope.launch { prefs.setAiTargetLocale(option.locale, label) }
                            showAiTargetLanguageMenu = false
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAiSourceTextProfileDialog) {
        AlertDialog(
            onDismissRequest = { showAiSourceTextProfileDialog = false },
            title = { Text(stringResource(R.string.settings_ai_source_text_profile)) },
            text = {
                Column {
                    aiSourceTextProfileOptions.forEach { option ->
                        RadioOption(option.profile.storedValue, stringResource(option.labelRes), aiSourceTextProfile.storedValue) {
                            scope.launch { prefs.setAiSourceTextProfile(option.profile) }
                            showAiSourceTextProfileDialog = false
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAiModelSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAiModelSourceDialog = false },
            title = { Text(stringResource(R.string.settings_ai_model_source)) },
            text = {
                Column {
                    RadioOption(
                        AiLocalModelSource.HUGGING_FACE.storedValue,
                        stringResource(R.string.settings_ai_model_source_huggingface),
                        aiLocalModelSource.storedValue
                    ) {
                        scope.launch { prefs.setAiLocalModelSource(AiLocalModelSource.HUGGING_FACE) }
                        showAiModelSourceDialog = false
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAiModelCollectionDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_model_collection),
            initialValue = aiModelCollectionId,
            placeholder = stringResource(R.string.settings_ai_model_huggingface_collection_placeholder),
            onSave = { value -> scope.launch { prefs.setAiModelCollectionId(value) } },
            onDismiss = { showAiModelCollectionDialog = false }
        )
    }

    if (showAiModelRevisionDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_model_revision),
            initialValue = aiModelRevision,
            placeholder = "main",
            onSave = { value -> scope.launch { prefs.setAiModelRevision(value) } },
            onDismiss = { showAiModelRevisionDialog = false }
        )
    }

    if (showAiImageTransportDialog) {
        AlertDialog(
            onDismissRequest = { showAiImageTransportDialog = false },
            title = { Text(stringResource(R.string.settings_ai_image_transport)) },
            text = {
                Column {
                    RadioOption(AiImageTransport.BASE64.storedValue, stringResource(R.string.settings_ai_image_transport_base64), aiImageTransport.storedValue) {
                        scope.launch { prefs.setAiImageTransport(AiImageTransport.BASE64) }
                        showAiImageTransportDialog = false
                    }
                    RadioOption(AiImageTransport.IMAGE_URL.storedValue, stringResource(R.string.settings_ai_image_transport_url), aiImageTransport.storedValue) {
                        scope.launch { prefs.setAiImageTransport(AiImageTransport.IMAGE_URL) }
                        showAiImageTransportDialog = false
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAiS3EndpointDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_s3_endpoint),
            initialValue = secureAiSettings.s3Endpoint,
            placeholder = "https://s3.example.com",
            onSave = { saveS3Settings(secureAiSettings.copy(s3Endpoint = it)) },
            onDismiss = { showAiS3EndpointDialog = false }
        )
    }

    if (showAiS3RegionDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_s3_region),
            initialValue = secureAiSettings.s3Region,
            placeholder = "us-east-1",
            onSave = { saveS3Settings(secureAiSettings.copy(s3Region = it)) },
            onDismiss = { showAiS3RegionDialog = false }
        )
    }

    if (showAiS3BucketDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_s3_bucket),
            initialValue = secureAiSettings.s3Bucket,
            onSave = { saveS3Settings(secureAiSettings.copy(s3Bucket = it)) },
            onDismiss = { showAiS3BucketDialog = false }
        )
    }

    if (showAiS3AccessKeyDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_s3_access_key),
            initialValue = secureAiSettings.s3AccessKey,
            onSave = { saveS3Settings(secureAiSettings.copy(s3AccessKey = it)) },
            onDismiss = { showAiS3AccessKeyDialog = false }
        )
    }

    if (showAiS3SecretKeyDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_s3_secret_key),
            initialValue = secureAiSettings.s3SecretKey,
            onSave = { saveS3Settings(secureAiSettings.copy(s3SecretKey = it)) },
            onDismiss = { showAiS3SecretKeyDialog = false }
        )
    }

    if (showAiS3PathPrefixDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_s3_path_prefix),
            initialValue = secureAiSettings.s3PathPrefix,
            placeholder = "ai-temp",
            onSave = { saveS3Settings(secureAiSettings.copy(s3PathPrefix = it)) },
            onDismiss = { showAiS3PathPrefixDialog = false }
        )
    }

    if (showAiS3TtlDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_s3_ttl_seconds),
            initialValue = secureAiSettings.s3TtlSeconds.toString(),
            placeholder = "300",
            onSave = { value ->
                saveS3Settings(secureAiSettings.copy(s3TtlSeconds = value.toIntOrNull() ?: secureAiSettings.s3TtlSeconds))
            },
            onDismiss = { showAiS3TtlDialog = false }
        )
    }

    if (showAiPagesPerRequestDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_pages_per_request),
            initialValue = aiPagesPerRequest.toString(),
            onSave = { value -> scope.launch { prefs.setAiPagesPerRequest(value.toIntOrNull() ?: aiPagesPerRequest) } },
            onDismiss = { showAiPagesPerRequestDialog = false }
        )
    }

    if (showAiMaxImagesPerRequestDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_max_images_per_request),
            initialValue = aiMaxImagesPerRequest.toString(),
            onSave = { value -> scope.launch { prefs.setAiMaxImagesPerRequest(value.toIntOrNull() ?: aiMaxImagesPerRequest) } },
            onDismiss = { showAiMaxImagesPerRequestDialog = false }
        )
    }

    if (showAiConcurrencyDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_concurrent_requests),
            initialValue = aiConcurrentRequests.toString(),
            onSave = { value -> scope.launch { prefs.setAiConcurrentRequests(value.toIntOrNull() ?: aiConcurrentRequests) } },
            onDismiss = { showAiConcurrencyDialog = false }
        )
    }

    if (showAiTimeoutDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_timeout),
            initialValue = aiTimeoutSeconds.toString(),
            onSave = { value -> scope.launch { prefs.setAiTimeoutSeconds(value.toIntOrNull() ?: aiTimeoutSeconds) } },
            onDismiss = { showAiTimeoutDialog = false }
        )
    }

    if (showAiVerticalGlyphSpacingDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_vertical_glyph_spacing),
            initialValue = aiVerticalGlyphSpacingPercent.toString(),
            placeholder = "92",
            onSave = { value -> scope.launch { prefs.setAiVerticalGlyphSpacingPercent(value.toIntOrNull() ?: aiVerticalGlyphSpacingPercent) } },
            onDismiss = { showAiVerticalGlyphSpacingDialog = false }
        )
    }

    if (showWebDavUrlDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_webdav_url),
            initialValue = secureWebDavSettings.url,
            onSave = {
                val next = secureWebDavSettings.copy(url = it)
                app?.secureWebDavSettingsStore?.save(next)
                secureWebDavSettings = app?.secureWebDavSettingsStore?.read() ?: next
            },
            onDismiss = { showWebDavUrlDialog = false }
        )
    }

    if (showWebDavUsernameDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_webdav_username),
            initialValue = secureWebDavSettings.username,
            onSave = {
                val next = secureWebDavSettings.copy(username = it)
                app?.secureWebDavSettingsStore?.save(next)
                secureWebDavSettings = app?.secureWebDavSettingsStore?.read() ?: next
            },
            onDismiss = { showWebDavUsernameDialog = false }
        )
    }

    if (showWebDavPasswordDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_webdav_password),
            initialValue = secureWebDavSettings.password,
            onSave = {
                val next = secureWebDavSettings.copy(password = it)
                app?.secureWebDavSettingsStore?.save(next)
                secureWebDavSettings = app?.secureWebDavSettingsStore?.read() ?: next
            },
            onDismiss = { showWebDavPasswordDialog = false }
        )
    }
}

@Composable
private fun RadioOption(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label)
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCategoryList(aiTranslationAvailable: Boolean, onSelect: (SettingsPage) -> Unit) {
    SettingsPage.entries.filter { page -> aiTranslationAvailable || page !in aiOnlySettingsPages }.forEach { page ->
        SettingsCategoryItem(page = page, onClick = { onSelect(page) })
    }
}

@Composable
private fun SettingsCategoryItem(page: SettingsPage, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(page.titleRes)) },
        leadingContent = { Icon(page.icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun AboutSection(appUpdateRepository: AppUpdateRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkingUpdates by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<GithubRelease?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.about)) },
        supportingContent = { Text("${stringResource(R.string.app_name)} · ${BuildConfig.VERSION_NAME}") },
        leadingContent = {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(42.dp)
            )
        },
        trailingContent = {
            TextButton(
                enabled = !checkingUpdates,
                onClick = {
                    scope.launch {
                        checkingUpdates = true
                        val result = appUpdateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
                        checkingUpdates = false
                        val update = result.getOrNull()
                        if (update != null) {
                            availableUpdate = update
                            showUpdateDialog = true
                        } else {
                            val message = if (result.isSuccess) R.string.no_updates_available else R.string.check_updates_failed
                            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Text(stringResource(if (checkingUpdates) R.string.loading else R.string.check_updates))
            }
        }
    )

    if (showUpdateDialog) {
        availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text(stringResource(R.string.update_available, update.tagName)) },
                text = {
                    SelectionContainer {
                        Text(update.body.ifBlank { update.name })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUpdateDialog = false
                        openExternalUrl(context, update.htmlUrl)
                    }) {
                        Text(stringResource(R.string.open_release_page))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.close)) }
                }
            )
        }
    }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        ListItem(
            headlineContent = { Text(stringResource(page.titleRes)) },
            leadingContent = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            modifier = Modifier.clickable(onClick = onBack)
        )
        content()
    }
}

@OptIn(ExperimentalCoilApi::class)
private suspend fun getCacheSize(context: android.content.Context): CacheSizeUi = withContext(Dispatchers.IO) {
    val diskCache = context.imageLoader.diskCache
    val imageBytes = diskCache?.size ?: 0L
    val readerBytes = ReaderPageCache.size(context)
    val cachedBooks = BookDownloadIndex(context.cacheDir).list()
    val cachedBookBytes = ReaderPageCache.cachedBooksSize(context, cachedBooks)
    CacheSizeUi(
        imageBytes = imageBytes,
        readerBytes = readerBytes,
        cachedBooks = cachedBooks,
        cachedBookBytes = cachedBookBytes
    )
}

private data class CacheSizeUi(
    val imageBytes: Long,
    val readerBytes: Long,
    val cachedBooks: List<CachedBookEntry>,
    val cachedBookBytes: Long
) {
    @Composable
    fun displayText(): String =
        stringResource(
            R.string.settings_cache_size,
            formatCacheSize(imageBytes + readerBytes),
            formatCacheSize(imageBytes),
            formatCacheSize(readerBytes)
        )

    val cachedBookCount: Int
        get() = cachedBooks.size

    val cachedBookPages: Int
        get() = cachedBooks.sumOf { it.cachedPages }

    val cachedBookTotalPages: Int
        get() = cachedBooks.sumOf { it.pageCount }

    companion object {
        fun loading(): CacheSizeUi = CacheSizeUi(
            imageBytes = -1L,
            readerBytes = -1L,
            cachedBooks = emptyList(),
            cachedBookBytes = -1L
        )
    }
}

@Composable
private fun CacheHealthBreakdown(cacheSize: CacheSizeUi) {
    SettingsSectionHeader(stringResource(R.string.settings_cache_health))
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_cache_health_covers)) },
        supportingContent = { Text(formatCacheSize(cacheSize.imageBytes)) }
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_cache_health_reader_pages)) },
        supportingContent = { Text(formatCacheSize(cacheSize.readerBytes)) }
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_cache_health_cached_books)) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_cache_health_cached_books_desc,
                    cacheSize.cachedBookCount,
                    cacheSize.cachedBookPages,
                    cacheSize.cachedBookTotalPages,
                    formatCacheSize(cacheSize.cachedBookBytes)
                )
            )
        }
    )
}

private fun formatCacheSize(bytes: Long): String {
    if (bytes < 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}

private fun deviceTierLabelRes(tier: AiLocalModelTier): Int =
    when (tier) {
        AiLocalModelTier.LOW -> R.string.settings_ai_model_device_tier_low
        AiLocalModelTier.BALANCED -> R.string.settings_ai_model_device_tier_balanced
        AiLocalModelTier.HIGH -> R.string.settings_ai_model_device_tier_high
    }

@Composable
private fun TextSettingDialog(
    title: String,
    initialValue: String,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(value)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun CacheClearOption(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

@Composable
private fun CacheSizeDialog(
    title: String,
    selectedSizeMb: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                CacheSizeOption.values.forEach { option ->
                    RadioOption(
                        value = option.sizeMb.toString(),
                        label = formatCacheSize(option.bytes),
                        selected = CacheSizeOption.fromMb(selectedSizeMb).sizeMb.toString(),
                        onSelect = { onSelect(option.sizeMb) }
                    )
                }
            }
        },
        confirmButton = {}
    )
}
