package fail.tiger.komgarot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fail.tiger.komgarot.KomgarotApp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiSourceTextProfile
import fail.tiger.komgarot.data.local.AiTranslationRequestMode
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.BookDownloadIndex
import fail.tiger.komgarot.data.local.CacheClearTarget
import fail.tiger.komgarot.data.local.CacheMaintenance
import fail.tiger.komgarot.data.local.CacheSizeOption
import fail.tiger.komgarot.data.local.CachedBookEntry
import fail.tiger.komgarot.data.local.LandscapePageSplitOrder
import fail.tiger.komgarot.data.local.ReaderPageCache
import fail.tiger.komgarot.data.local.SecureAiSettings
import fail.tiger.komgarot.data.local.SecureWebDavSettings
import fail.tiger.komgarot.data.local.normalizeWebDavUrl
import fail.tiger.komgarot.data.remote.AiServiceTestResult
import fail.tiger.komgarot.data.remote.AiTranslationErrorCategory
import fail.tiger.komgarot.data.repository.AiLocalModelTier
import fail.tiger.komgarot.data.repository.AiTranslationPurgeAbortReason
import fail.tiger.komgarot.data.repository.AiTranslationPurgeResult
import fail.tiger.komgarot.data.repository.AiTranslationPurgeScanResult
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
fun SettingsScreen(
    onBack: () -> Unit,
    prefs: AuthPreferences,
    aiTranslationAvailable: Boolean = BuildConfig.AI_TRANSLATION_AVAILABLE
) {
    SettingsContent(
        prefs = prefs,
        aiTranslationAvailable = aiTranslationAvailable,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun MeScreen(
    userEmail: String?,
    serverUrl: String,
    isAdmin: Boolean,
    sessionSyncing: Boolean,
    sessionRetryable: Boolean,
    onSessionRetry: () -> Unit,
    onUpdateServerUrl: suspend (String) -> Result<Unit>,
    onServerChanged: () -> Unit,
    aiTranslationAvailable: Boolean = BuildConfig.AI_TRANSLATION_AVAILABLE,
    onCachedBooksClick: () -> Unit,
    onAiTranslationTasksClick: () -> Unit,
    appUpdateRepository: AppUpdateRepository = AppUpdateRepository(),
    onAdminClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showServerUrlDialog by remember { mutableStateOf(false) }
    var serverUrlSaving by remember { mutableStateOf(false) }
    var serverUrlError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.me)) })
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = {
                    Text(userEmail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.session_syncing))
                },
                supportingContent = {
                    Column {
                        Text(
                            stringResource(
                                when {
                                    sessionRetryable -> R.string.session_sync_failed
                                    sessionSyncing -> R.string.session_syncing_desc
                                    isAdmin -> R.string.admin_admin_role
                                    else -> R.string.user_role
                                }
                            )
                        )
                        Text(
                            text = serverUrl.ifBlank { stringResource(R.string.settings_not_configured) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                trailingContent = {
                    when {
                        sessionRetryable -> TextButton(onClick = onSessionRetry) {
                            Text(stringResource(R.string.session_retry))
                        }
                        sessionSyncing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else -> Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                },
                modifier = Modifier.clickable {
                    serverUrlError = null
                    showServerUrlDialog = true
                }
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
            if (userEmail != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.logout)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onLogout)
                )
            }
            Spacer(Modifier.weight(1f, fill = true))
            HorizontalDivider()
            AboutSection(appUpdateRepository = appUpdateRepository)
        }
    }

    if (showServerUrlDialog) {
        ServerUrlSettingDialog(
            initialValue = serverUrl,
            saving = serverUrlSaving,
            error = serverUrlError,
            onSave = { value ->
                if (value.isBlank()) {
                    serverUrlError = context.getString(R.string.settings_server_url_required)
                } else {
                    serverUrlSaving = true
                    serverUrlError = null
                    scope.launch {
                        val result = onUpdateServerUrl(value)
                        serverUrlSaving = false
                        result
                            .onSuccess {
                                showServerUrlDialog = false
                                onServerChanged()
                            }
                            .onFailure { failure ->
                                serverUrlError = failure.message
                                    ?.takeIf(String::isNotBlank)
                                    ?: context.getString(R.string.settings_server_url_update_failed)
                            }
                    }
                }
            },
            onDismiss = {
                if (!serverUrlSaving) showServerUrlDialog = false
            }
        )
    }
}

private enum class SettingsPage(val titleRes: Int, val icon: ImageVector) {
    CACHE(R.string.settings_section_cache, Icons.Default.Cached),
    READING(R.string.settings_section_reading, Icons.AutoMirrored.Filled.MenuBook),
    AI(R.string.settings_section_ai_translation, Icons.Default.AutoAwesome),
    WEBDAV(R.string.settings_section_webdav_backup, Icons.Default.CloudUpload),
    SECURITY(R.string.settings_section_security, Icons.Default.Security)
}

private val aiOnlySettingsPages = setOf(SettingsPage.AI)

private fun AiTranslationPurgeAbortReason.purgeMessageRes(): Int = when (this) {
    AiTranslationPurgeAbortReason.AUTHENTICATION -> R.string.settings_ai_purge_translation_data_aborted_authentication
    AiTranslationPurgeAbortReason.RATE_LIMIT -> R.string.settings_ai_purge_translation_data_aborted_rate_limit
    AiTranslationPurgeAbortReason.SERVER -> R.string.settings_ai_purge_translation_data_aborted_server
    AiTranslationPurgeAbortReason.NETWORK -> R.string.settings_ai_purge_translation_data_aborted_network
    AiTranslationPurgeAbortReason.UNKNOWN -> R.string.settings_ai_purge_translation_data_aborted_unknown
}

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
    onBack: () -> Unit,
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
    val splitLandscapePages by prefs.splitLandscapePages.collectAsStateWithLifecycle(initialValue = false)
    val landscapePageSplitOrder by prefs.landscapePageSplitOrder.collectAsStateWithLifecycle(
        initialValue = LandscapePageSplitOrder.RIGHT_FIRST
    )
    val keepScreenOn by prefs.keepScreenOn.collectAsStateWithLifecycle(initialValue = true)
    val einkMode by prefs.einkMode.collectAsStateWithLifecycle(initialValue = false)
    val tapPageTurn by prefs.tapPageTurn.collectAsStateWithLifecycle(initialValue = false)
    val showBookThumbnails by prefs.showBookThumbnails.collectAsStateWithLifecycle(initialValue = true)
    val coverCacheSizeMb by prefs.coverCacheSizeMb.collectAsStateWithLifecycle(initialValue = CacheSizeOption.default.sizeMb)
    val readerCacheSizeMb by prefs.readerCacheSizeMb.collectAsStateWithLifecycle(initialValue = CacheSizeOption.default.sizeMb)
    val clearCacheOnStartup by prefs.clearCacheOnStartup.collectAsStateWithLifecycle(initialValue = false)
    val aiTranslationEnabled by prefs.aiTranslationEnabled.collectAsStateWithLifecycle(initialValue = false)
    val aiBaseUrl by prefs.aiBaseUrl.collectAsStateWithLifecycle(initialValue = "")
    val aiModelName by prefs.aiModelName.collectAsStateWithLifecycle(initialValue = "")
    val aiTargetLocale by prefs.aiTargetLocale.collectAsStateWithLifecycle(initialValue = "")
    val aiTargetLanguageName by prefs.aiTargetLanguageName.collectAsStateWithLifecycle(initialValue = "")
    val aiSourceTextProfile by prefs.aiSourceTextProfile.collectAsStateWithLifecycle(initialValue = AiSourceTextProfile.AUTO)
    val aiModelCollectionId by prefs.aiModelCollectionId.collectAsStateWithLifecycle(initialValue = "PaddlePaddle/pp-ocrv6")
    val aiModelRevision by prefs.aiModelRevision.collectAsStateWithLifecycle(initialValue = "main")
    val aiAutoSelectDeviceTier by prefs.aiAutoSelectDeviceTier.collectAsStateWithLifecycle(initialValue = true)
    val aiImageTransport by prefs.aiImageTransport.collectAsStateWithLifecycle(initialValue = AiImageTransport.BASE64)
    val aiTranslationRequestMode by prefs.aiTranslationRequestMode.collectAsStateWithLifecycle(initialValue = AiSettings.defaults().requestMode)
    val aiPagesPerRequest by prefs.aiPagesPerRequest.collectAsStateWithLifecycle(initialValue = 10)
    val aiConcurrentRequests by prefs.aiConcurrentRequests.collectAsStateWithLifecycle(initialValue = AiSettings.defaults().concurrentRequests)
    val aiMaxImagesPerRequest by prefs.aiMaxImagesPerRequest.collectAsStateWithLifecycle(initialValue = 20)
    val aiTimeoutSeconds by prefs.aiTimeoutSeconds.collectAsStateWithLifecycle(initialValue = 30)
    val aiSkipSoundEffects by prefs.aiSkipSoundEffects.collectAsStateWithLifecycle(initialValue = false)
    val aiReasoningEffort by prefs.aiReasoningEffort.collectAsStateWithLifecycle(initialValue = "")
    val aiAdditionalPrompt by prefs.aiCustomInstructions.collectAsStateWithLifecycle(initialValue = "")
    val aiVerticalGlyphSpacingPercent by prefs.aiVerticalGlyphSpacingPercent.collectAsStateWithLifecycle(initialValue = 86)
    val app = context.applicationContext as? KomgarotApp
    var secureAiSettings by remember { mutableStateOf(app?.secureAiSettingsStore?.read() ?: SecureAiSettings()) }
    var secureWebDavSettings by remember { mutableStateOf(app?.secureWebDavSettingsStore?.read() ?: SecureWebDavSettings()) }
    var showPreloadDialog by remember { mutableStateOf(false) }
    var showReadingDialog by remember { mutableStateOf(false) }
    var showFitDialog by remember { mutableStateOf(false) }
    var showLandscapePageSplitOrderDialog by remember { mutableStateOf(false) }
    var showAiBaseUrlDialog by remember { mutableStateOf(false) }
    var showAiApiKeyDialog by remember { mutableStateOf(false) }
    var showAiModelDialog by remember { mutableStateOf(false) }
    var showAiTargetLanguageMenu by remember { mutableStateOf(false) }
    var showAiSourceTextProfileDialog by remember { mutableStateOf(false) }
    var showAiImageTransportDialog by remember { mutableStateOf(false) }
    var showAiRequestModeDialog by remember { mutableStateOf(false) }
    var showAiS3EndpointDialog by remember { mutableStateOf(false) }
    var showAiS3RegionDialog by remember { mutableStateOf(false) }
    var showAiS3BucketDialog by remember { mutableStateOf(false) }
    var showAiS3AccessKeyDialog by remember { mutableStateOf(false) }
    var showAiS3SecretKeyDialog by remember { mutableStateOf(false) }
    var showAiS3PathPrefixDialog by remember { mutableStateOf(false) }
    var showAiS3TtlDialog by remember { mutableStateOf(false) }
    var aiS3Testing by remember { mutableStateOf(false) }
    var aiServiceTesting by remember { mutableStateOf(false) }
    var aiServiceTestResult by remember { mutableStateOf<AiServiceTestResult?>(null) }
    var showAiPagesPerRequestDialog by remember { mutableStateOf(false) }
    var showAiConcurrencyDialog by remember { mutableStateOf(false) }
    var showAiMaxImagesPerRequestDialog by remember { mutableStateOf(false) }
    var showAiTimeoutDialog by remember { mutableStateOf(false) }
    var showAiReasoningEffortDialog by remember { mutableStateOf(false) }
    var showAiAdditionalPromptDialog by remember { mutableStateOf(false) }
    var showAiVerticalGlyphSpacingDialog by remember { mutableStateOf(false) }
    var showDeleteLocalModelsDialog by remember { mutableStateOf(false) }
    var purgingAiTranslations by remember { mutableStateOf(false) }
    var aiTranslationPurgeCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var showWebDavUrlDialog by remember { mutableStateOf(false) }
    var showWebDavUsernameDialog by remember { mutableStateOf(false) }
    var showWebDavPasswordDialog by remember { mutableStateOf(false) }
    var showWebDavBackupPicker by remember { mutableStateOf(false) }
    var webDavBackupFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var webDavBackupLoading by remember { mutableStateOf(false) }
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
    BackHandler(enabled = selectedSettingsPage != null) {
        selectedSettingsPage = null
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(selectedSettingsPage?.titleRes ?: R.string.settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedSettingsPage != null) {
                                selectedSettingsPage = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            headerContent()
            AnimatedContent(
                targetState = selectedSettingsPage,
                label = "settings-page",
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val enteringSubpage = initialState == null && targetState != null
                    if (enteringSubpage) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                }
            ) { page ->
                Column(Modifier.fillMaxWidth()) {
                    if (page == null) {
                        SettingsCategoryList(aiTranslationAvailable = aiTranslationAvailable, onSelect = { selectedSettingsPage = it })
                    } else {
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
                headlineContent = { Text(stringResource(R.string.settings_show_book_thumbnails)) },
                supportingContent = { Text(stringResource(R.string.settings_show_book_thumbnails_desc)) },
                trailingContent = {
                    Switch(
                        checked = showBookThumbnails,
                        onCheckedChange = { scope.launch { prefs.setShowBookThumbnails(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setShowBookThumbnails(!showBookThumbnails) }
                }
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
                headlineContent = { Text(stringResource(R.string.settings_split_landscape_pages)) },
                supportingContent = { Text(stringResource(R.string.settings_split_landscape_pages_desc)) },
                trailingContent = {
                    Switch(
                        checked = splitLandscapePages,
                        onCheckedChange = { scope.launch { prefs.setSplitLandscapePages(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setSplitLandscapePages(!splitLandscapePages) }
                }
            )
            if (splitLandscapePages) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_landscape_page_split_order)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (landscapePageSplitOrder == LandscapePageSplitOrder.RIGHT_FIRST) {
                                    R.string.settings_landscape_page_split_right_first
                                } else {
                                    R.string.settings_landscape_page_split_left_first
                                }
                            )
                        )
                    },
                    modifier = Modifier.clickable { showLandscapePageSplitOrderDialog = true }
                )
            }
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
            SettingsSectionHeader(stringResource(R.string.settings_ai_section_basic))
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
            val missingAiSettings = buildList {
                if (aiBaseUrl.isBlank()) add(stringResource(R.string.settings_ai_base_url))
                if (secureAiSettings.apiKey.isBlank()) add(stringResource(R.string.settings_ai_api_key))
                if (aiModelName.isBlank()) add(stringResource(R.string.settings_ai_model_name))
            }
            val aiTestConfigRequiredMessage = stringResource(
                R.string.settings_ai_test_service_config_required,
                missingAiSettings.joinToString()
            )
            val aiTestLocalModelRequiredMessage = stringResource(R.string.settings_ai_test_service_local_model_required)
            val aiTestClientUnavailableMessage = stringResource(R.string.settings_ai_test_service_client_unavailable)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_test_service)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (aiServiceTesting) {
                                R.string.settings_ai_test_service_testing
                            } else {
                                R.string.settings_ai_test_service_desc
                            }
                        )
                    )
                },
                modifier = Modifier.clickable(enabled = !aiServiceTesting) {
                    if (missingAiSettings.isNotEmpty()) {
                        aiServiceTestResult = AiServiceTestResult.Failure(
                            detail = aiTestConfigRequiredMessage,
                            category = AiTranslationErrorCategory.MODEL_CONFIGURATION
                        )
                        return@clickable
                    }
                    if (!localModelsInstalled) {
                        aiServiceTestResult = AiServiceTestResult.Failure(
                            detail = aiTestLocalModelRequiredMessage,
                            category = AiTranslationErrorCategory.MODEL_CONFIGURATION
                        )
                        return@clickable
                    }
                    scope.launch {
                        aiServiceTesting = true
                        aiServiceTestResult = app?.aiTranslationClient?.testService(
                            baseUrl = aiBaseUrl,
                            apiKey = secureAiSettings.apiKey,
                            model = aiModelName,
                            timeoutSeconds = aiTimeoutSeconds
                        ) ?: AiServiceTestResult.Failure(
                            aiTestClientUnavailableMessage
                        )
                        aiServiceTesting = false
                    }
                }
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
            SettingsSectionHeader(stringResource(R.string.settings_ai_section_translation_behavior))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_skip_sound_effects)) },
                supportingContent = { Text(stringResource(R.string.settings_ai_skip_sound_effects_desc)) },
                trailingContent = {
                    Switch(
                        checked = aiSkipSoundEffects,
                        onCheckedChange = { scope.launch { prefs.setAiSkipSoundEffects(it) } }
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch { prefs.setAiSkipSoundEffects(!aiSkipSoundEffects) }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_reasoning_effort)) },
                supportingContent = {
                    Column {
                        Text(aiReasoningEffort.ifBlank { stringResource(R.string.settings_not_configured) })
                        Text(
                            text = stringResource(R.string.settings_ai_reasoning_effort_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.clickable { showAiReasoningEffortDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_additional_prompt)) },
                supportingContent = {
                    Column {
                        if (aiAdditionalPrompt.isNotBlank()) {
                            Text(
                                text = aiAdditionalPrompt,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_ai_additional_prompt_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.clickable { showAiAdditionalPromptDialog = true }
            )
            SettingsSectionHeader(stringResource(R.string.settings_ai_section_image_transport))
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
            SettingsSectionHeader(stringResource(R.string.settings_ai_section_request))
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
                headlineContent = { Text(stringResource(R.string.settings_ai_request_mode)) },
                supportingContent = { Text(aiTranslationRequestModeLabel(aiTranslationRequestMode)) },
                modifier = Modifier.clickable { showAiRequestModeDialog = true }
            )
            if (aiTranslationRequestMode == AiTranslationRequestMode.PARALLEL) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_concurrent_requests)) },
                    supportingContent = { Text(aiConcurrentRequests.toString()) },
                    modifier = Modifier.clickable { showAiConcurrencyDialog = true }
                )
            }
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
            SettingsSectionHeader(stringResource(R.string.settings_ai_section_local_model))
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
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            )
            SettingsSectionHeader(stringResource(R.string.settings_ai_section_data))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ai_purge_translation_data)) },
                supportingContent = {
                    Text(stringResource(if (purgingAiTranslations) R.string.loading else R.string.settings_ai_purge_translation_data_desc))
                },
                modifier = Modifier.clickable(enabled = !purgingAiTranslations) {
                    scope.launch {
                        purgingAiTranslations = true
                        val result = app?.aiTranslationRepositoryOrNull?.scanMissingBookTranslations()
                        purgingAiTranslations = false
                        when (result) {
                            is AiTranslationPurgeScanResult.Ready -> {
                                if (result.candidateBookIds.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_ai_purge_translation_data_done, 0),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    aiTranslationPurgeCandidates = result.candidateBookIds
                                }
                            }
                            is AiTranslationPurgeScanResult.Aborted -> Toast.makeText(
                                context,
                                context.getString(result.reason.purgeMessageRes()),
                                Toast.LENGTH_LONG
                            ).show()
                            null -> Unit
                        }
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
                    Text(secureWebDavSettings.username.ifBlank { stringResource(R.string.settings_not_configured) })
                },
                modifier = Modifier.clickable { showWebDavUsernameDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webdav_password)) },
                supportingContent = {
                    Text(webDavPasswordDisplayText(secureWebDavSettings.password).ifBlank { stringResource(R.string.settings_not_configured) })
                },
                modifier = Modifier.clickable { showWebDavPasswordDialog = true }
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webdav_restore_now)) },
                supportingContent = {
                    Text(stringResource(if (webDavBackupLoading) R.string.loading else R.string.settings_webdav_restore_now_desc))
                },
                modifier = Modifier.clickable(enabled = !webDavBackupLoading) {
                    scope.launch {
                        webDavBackupLoading = true
                        val result = app?.webDavBackupRepository?.listBackups()
                        webDavBackupLoading = false
                        val files = result?.getOrNull().orEmpty()
                        if (result?.isSuccess == true && files.isNotEmpty()) {
                            webDavBackupFiles = files
                            showWebDavBackupPicker = true
                        } else {
                            val message = if (result?.isSuccess == true) {
                                context.getString(R.string.settings_webdav_restore_no_backups)
                            } else {
                                context.getString(R.string.operation_failed)
                            }
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            Text(
                text = stringResource(R.string.settings_webdav_backup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
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

    if (aiTranslationPurgeCandidates.isNotEmpty()) {
        val candidateCount = aiTranslationPurgeCandidates.size
        AlertDialog(
            onDismissRequest = { aiTranslationPurgeCandidates = emptyList() },
            title = { Text(stringResource(R.string.settings_ai_purge_translation_data_confirm_title)) },
            text = { Text(stringResource(R.string.settings_ai_purge_translation_data_confirm_message, candidateCount)) },
            confirmButton = {
                TextButton(
                    enabled = !purgingAiTranslations,
                    onClick = {
                        val candidates = aiTranslationPurgeCandidates
                        scope.launch {
                            purgingAiTranslations = true
                            val result = app?.aiTranslationRepositoryOrNull?.purgeMissingBookTranslations(candidates)
                            purgingAiTranslations = false
                            aiTranslationPurgeCandidates = emptyList()
                            val message = when (result) {
                                is AiTranslationPurgeResult.Completed -> context.getString(
                                    R.string.settings_ai_purge_translation_data_done,
                                    result.removedCount
                                )
                                is AiTranslationPurgeResult.Aborted -> context.getString(result.reason.purgeMessageRes())
                                null -> context.getString(R.string.operation_failed)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !purgingAiTranslations,
                    onClick = { aiTranslationPurgeCandidates = emptyList() }
                ) {
                    Text(stringResource(R.string.cancel))
                }
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

    if (showLandscapePageSplitOrderDialog) {
        AlertDialog(
            onDismissRequest = { showLandscapePageSplitOrderDialog = false },
            title = { Text(stringResource(R.string.settings_landscape_page_split_order)) },
            text = {
                Column {
                    RadioOption(
                        LandscapePageSplitOrder.RIGHT_FIRST.storedValue,
                        stringResource(R.string.settings_landscape_page_split_right_first),
                        landscapePageSplitOrder.storedValue
                    ) {
                        scope.launch {
                            prefs.setLandscapePageSplitOrder(LandscapePageSplitOrder.RIGHT_FIRST)
                        }
                        showLandscapePageSplitOrderDialog = false
                    }
                    RadioOption(
                        LandscapePageSplitOrder.LEFT_FIRST.storedValue,
                        stringResource(R.string.settings_landscape_page_split_left_first),
                        landscapePageSplitOrder.storedValue
                    ) {
                        scope.launch {
                            prefs.setLandscapePageSplitOrder(LandscapePageSplitOrder.LEFT_FIRST)
                        }
                        showLandscapePageSplitOrderDialog = false
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

    if (showAiReasoningEffortDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_reasoning_effort),
            initialValue = aiReasoningEffort,
            placeholder = stringResource(R.string.settings_ai_reasoning_effort_placeholder),
            onSave = { scope.launch { prefs.setAiReasoningEffort(it) } },
            onDismiss = { showAiReasoningEffortDialog = false }
        )
    }

    if (showAiAdditionalPromptDialog) {
        TextSettingDialog(
            title = stringResource(R.string.settings_ai_additional_prompt),
            initialValue = aiAdditionalPrompt,
            placeholder = stringResource(R.string.settings_ai_additional_prompt_placeholder),
            singleLine = false,
            minLines = 4,
            maxLines = 12,
            onSave = { scope.launch { prefs.setAiCustomInstructions(it) } },
            onDismiss = { showAiAdditionalPromptDialog = false }
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

    if (showAiRequestModeDialog) {
        AlertDialog(
            onDismissRequest = { showAiRequestModeDialog = false },
            title = { Text(stringResource(R.string.settings_ai_request_mode)) },
            text = {
                Column {
                    RadioOption(
                        value = AiTranslationRequestMode.SERIAL.storedValue,
                        label = stringResource(R.string.settings_ai_request_mode_serial),
                        selected = aiTranslationRequestMode.storedValue,
                        onSelect = {
                            scope.launch { prefs.setAiTranslationRequestMode(AiTranslationRequestMode.SERIAL) }
                            showAiRequestModeDialog = false
                        }
                    )
                    RadioOption(
                        value = AiTranslationRequestMode.PARALLEL.storedValue,
                        label = stringResource(R.string.settings_ai_request_mode_parallel),
                        selected = aiTranslationRequestMode.storedValue,
                        onSelect = {
                            scope.launch { prefs.setAiTranslationRequestMode(AiTranslationRequestMode.PARALLEL) }
                            showAiRequestModeDialog = false
                        }
                    )
                }
            },
            confirmButton = {}
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
                val next = secureWebDavSettings.copy(url = normalizeWebDavUrl(it))
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
            passwordInput = true,
            onSave = {
                val next = secureWebDavSettings.copy(password = it)
                app?.secureWebDavSettingsStore?.save(next)
                secureWebDavSettings = app?.secureWebDavSettingsStore?.read() ?: next
            },
            onDismiss = { showWebDavPasswordDialog = false }
        )
    }

    if (showWebDavBackupPicker) {
        AlertDialog(
            onDismissRequest = { showWebDavBackupPicker = false },
            title = { Text(stringResource(R.string.settings_webdav_restore_select_backup)) },
            text = {
                Column {
                    webDavBackupFiles.forEach { fileName ->
                        ListItem(
                            headlineContent = { Text(webDavBackupDisplayName(fileName)) },
                            supportingContent = { Text(fileName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showWebDavBackupPicker = false
                                    scope.launch {
                                        val result = app?.webDavBackupRepository?.restoreBackup(fileName)
                                        val message = if (result?.isSuccess == true) {
                                            secureAiSettings = app?.secureAiSettingsStore?.read() ?: secureAiSettings
                                            context.getString(R.string.settings_webdav_restore_success)
                                        } else {
                                            context.getString(R.string.operation_failed)
                                        }
                                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWebDavBackupPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    aiServiceTestResult?.let { result ->
        AiServiceTestResultDialog(
            result = result,
            onDismiss = { aiServiceTestResult = null }
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
                modifier = Modifier.size(48.dp)
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
private fun AiServiceTestResultDialog(
    result: AiServiceTestResult,
    onDismiss: () -> Unit
) {
    val success = result as? AiServiceTestResult.Success
    val failure = result as? AiServiceTestResult.Failure
    val successDetail = success?.let {
        stringResource(
            R.string.settings_ai_test_service_success_detail,
            it.latencyMs,
            it.responseBody
        )
    }
    val failureCategory = failure?.let { stringResource(aiServiceTestFailureCategoryLabelRes(it.category)) }
    val detail = successDetail ?: listOfNotNull(failureCategory, failure?.detail)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (success != null) {
                        R.string.settings_ai_test_service_success
                    } else {
                        R.string.settings_ai_test_service_failed
                    }
                )
            )
        },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(detail)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

private fun aiServiceTestFailureCategoryLabelRes(category: AiTranslationErrorCategory): Int = when (category) {
    AiTranslationErrorCategory.NETWORK_OR_API,
    AiTranslationErrorCategory.RATE_LIMITED,
    AiTranslationErrorCategory.SERVER_TEMPORARY -> R.string.settings_ai_test_service_category_network
    AiTranslationErrorCategory.AUTHENTICATION -> R.string.settings_ai_test_service_category_authentication
    AiTranslationErrorCategory.MODEL_CONFIGURATION -> R.string.settings_ai_test_service_category_model
    AiTranslationErrorCategory.VISION_UNSUPPORTED -> R.string.settings_ai_test_service_category_vision
    AiTranslationErrorCategory.NON_JSON_RESPONSE,
    AiTranslationErrorCategory.JSON_VALIDATION_FAILED -> R.string.settings_ai_test_service_category_response
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

private const val WEB_DAV_PASSWORD_MASK = "********"

private fun webDavPasswordDisplayText(password: String): String =
    if (password.isBlank()) "" else WEB_DAV_PASSWORD_MASK

private fun webDavBackupDisplayName(fileName: String): String {
    val raw = fileName.removePrefix("Komgarot_backup_").removeSuffix(".zip")
    if (raw.length != 15 || raw[8] != '_') return fileName
    return "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)} " +
        "${raw.substring(9, 11)}:${raw.substring(11, 13)}:${raw.substring(13, 15)}"
}

private fun deviceTierLabelRes(tier: AiLocalModelTier): Int =
    when (tier) {
        AiLocalModelTier.LOW -> R.string.settings_ai_model_device_tier_low
        AiLocalModelTier.BALANCED -> R.string.settings_ai_model_device_tier_balanced
        AiLocalModelTier.HIGH -> R.string.settings_ai_model_device_tier_high
    }

@Composable
private fun aiTranslationRequestModeLabel(mode: AiTranslationRequestMode): String =
    stringResource(
        when (mode) {
            AiTranslationRequestMode.SERIAL -> R.string.settings_ai_request_mode_serial
            AiTranslationRequestMode.PARALLEL -> R.string.settings_ai_request_mode_parallel
        }
    )

@Composable
private fun TextSettingDialog(
    title: String,
    initialValue: String,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    passwordInput: Boolean = false,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var passwordVisible by remember { mutableStateOf(false) }
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
                visualTransformation = if (passwordInput && !passwordVisible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = if (passwordInput) {
                    KeyboardOptions(keyboardType = KeyboardType.Password)
                } else {
                    KeyboardOptions.Default
                },
                trailingIcon = if (passwordInput) {
                    {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) {
                                        R.string.password_hide
                                    } else {
                                        R.string.password_show
                                    }
                                )
                            )
                        }
                    }
                } else {
                    null
                },
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
private fun ServerUrlSettingDialog(
    initialValue: String,
    saving: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_server_url)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(stringResource(R.string.settings_server_url_placeholder)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (saving) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                enabled = !saving && value.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.cancel))
            }
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
