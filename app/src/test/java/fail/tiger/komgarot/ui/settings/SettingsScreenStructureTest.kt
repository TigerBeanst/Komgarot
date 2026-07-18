package fail.tiger.komgarot.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/settings/SettingsScreen.kt").readText()

    @Test
    fun settingsContentUsesSectionHeadersAndDividers() {
        assertTrue(source.contains("SettingsCategoryList("))
        assertTrue(source.contains("SettingsCategoryItem("))
        assertTrue(!source.contains("SettingsPageContent("))
        assertTrue(source.contains("enum class SettingsPage"))
        assertTrue(source.contains("var selectedSettingsPage by remember { mutableStateOf<SettingsPage?>(null) }"))
        assertTrue(source.contains("when (page)"))
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_cache))"))
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_reading))"))
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_security))"))
        assertTrue(!source.contains("SettingsPageTabs("))
        assertTrue(!source.contains("ScrollableTabRow("))
        assertTrue(!source.contains("HorizontalDivider(Modifier.padding(vertical = 8.dp))"))
    }

    @Test
    fun settingsHidesAiPagesWhenFeatureIsUnavailable() {
        assertTrue(source.contains("aiTranslationAvailable: Boolean"))
        assertTrue(source.contains("SettingsCategoryList(aiTranslationAvailable = aiTranslationAvailable"))
        assertTrue(source.contains("SettingsPage.entries.filter { page ->"))
        assertTrue(source.contains("aiTranslationAvailable || page !in aiOnlySettingsPages"))
        assertTrue(source.contains("private val aiOnlySettingsPages = setOf(SettingsPage.AI)"))
    }

    @Test
    fun settingsContentIncludesEinkReaderOptions() {
        assertTrue(source.contains("R.string.settings_eink_mode"))
        assertTrue(source.contains("R.string.settings_tap_page_turn"))
        assertTrue(source.contains("if (!einkMode)"))
        assertTrue(source.contains("prefs.setEinkMode"))
        assertTrue(source.contains("prefs.setTapPageTurn"))
    }

    @Test
    fun settingsContentIncludesCacheHealthBreakdown() {
        assertTrue(source.contains("CacheHealthBreakdown("))
        assertTrue(source.contains("R.string.settings_cache_health"))
        assertTrue(source.contains("R.string.settings_cache_health_covers"))
        assertTrue(source.contains("R.string.settings_cache_health_reader_pages"))
        assertTrue(source.contains("R.string.settings_cache_health_cached_books"))
        assertTrue(source.contains("BookDownloadIndex(context.cacheDir).list()"))
        assertTrue(source.contains("ReaderPageCache.cachedBooksSize(context, cachedBooks)"))
    }

    @Test
    fun settingsContentIncludesAiTranslationOptions() {
        assertTrue(source.contains("R.string.settings_section_ai_translation"))
        assertTrue(source.contains("R.string.settings_ai_translation_enabled"))
        assertTrue(source.contains("R.string.settings_ai_api_key"))
        assertTrue(source.contains("R.string.settings_ai_target_language"))
        assertTrue(source.contains("AiTargetLanguageOption"))
        assertTrue(source.contains("showAiTargetLanguageMenu"))
        assertTrue(source.contains("R.string.settings_ai_source_text_profile"))
        assertTrue(source.contains("AiSourceTextProfileOption"))
        assertTrue(source.contains("showAiSourceTextProfileDialog"))
        assertTrue(source.contains("prefs.setAiSourceTextProfile"))
        assertTrue(source.contains("R.string.settings_ai_image_transport"))
        assertTrue(source.contains("R.string.settings_ai_s3_endpoint"))
        assertTrue(source.contains("R.string.settings_ai_s3_bucket"))
        assertTrue(source.contains("R.string.settings_ai_s3_access_key"))
        assertTrue(source.contains("R.string.settings_ai_s3_secret_key"))
        assertTrue(source.contains("R.string.settings_ai_s3_ttl_seconds"))
        assertTrue(source.contains("R.string.settings_ai_s3_test_upload"))
        assertTrue(source.contains("s3ImageUrlConfigOrNull()"))
        assertTrue(source.contains("testAiS3ImageUrlUpload(config)"))
        assertTrue(source.contains("var aiS3Testing by remember"))
        assertTrue(source.contains("saveS3Settings"))
        assertTrue(source.contains("R.string.settings_ai_model_requires_vision"))
        assertTrue(source.contains("R.string.settings_ai_base_url_placeholder"))
        assertTrue(source.contains("R.string.settings_ai_pages_per_request"))
        assertTrue(source.contains("R.string.settings_ai_max_images_per_request"))
        assertTrue(source.contains("R.string.settings_ai_request_mode"))
        assertTrue(source.contains("R.string.settings_ai_request_mode_serial"))
        assertTrue(source.contains("R.string.settings_ai_request_mode_parallel"))
        assertTrue(source.contains("prefs.setAiTranslationRequestMode"))
        assertTrue(source.contains("if (aiTranslationRequestMode == AiTranslationRequestMode.PARALLEL)"))
        assertTrue(source.contains("R.string.settings_ai_concurrent_requests"))
        assertTrue(source.contains("R.string.settings_ai_timeout"))
        assertTrue(source.contains("R.string.settings_ai_vertical_glyph_spacing"))
        assertTrue(source.contains("showAiVerticalGlyphSpacingDialog"))
        assertTrue(source.contains("showAiTimeoutDialog"))
        assertTrue(source.contains("showAiMaxImagesPerRequestDialog"))
        assertTrue(source.contains("prefs.setAiTimeoutSeconds"))
        assertTrue(source.contains("prefs.setAiVerticalGlyphSpacingPercent"))
        assertTrue(source.contains("prefs.setAiMaxImagesPerRequest"))
        assertTrue(!source.contains("R.string.settings_ai_test_mode"))
        assertTrue(!source.contains("setAiTestModeEnabled"))
        assertTrue(source.contains("R.string.settings_ai_section_basic"))
        assertTrue(source.contains("R.string.settings_ai_section_request"))
        assertTrue(source.contains("R.string.settings_ai_section_image_transport"))
        assertTrue(source.contains("R.string.settings_ai_section_local_model"))
        assertTrue(source.contains("R.string.settings_ai_section_data"))
        assertTrue(source.contains("R.string.settings_ai_purge_translation_data"))
        assertTrue(source.contains("scanMissingBookTranslations()"))
        assertTrue(source.contains("purgeMissingBookTranslations(candidates)"))
        assertTrue(source.contains("aiTranslationPurgeCandidates"))
        assertTrue(source.contains("settings_ai_purge_translation_data_confirm_message"))
        assertTrue(!source.contains("R.string.ai_translation_mode_local_detection"))
        assertTrue(!source.contains("settings_ai_local_detection_pipeline_desc"))
        assertTrue(!source.contains("R.string.settings_ai_default_mode"))
        assertTrue(!source.contains("showAiModeDialog"))
        assertTrue(!source.contains("AiTranslationMode.entries"))
    }

    @Test
    fun settingsContentIncludesAiModelManagementOptions() {
        assertTrue(!source.contains("SettingsPage.MODELS"))
        assertTrue(!source.contains("R.string.settings_section_ai_models"))
        assertTrue(!source.contains("R.string.settings_ai_model_source"))
        assertTrue(!source.contains("R.string.settings_ai_model_collection"))
        assertTrue(!source.contains("R.string.settings_ai_model_revision"))
        assertTrue(!source.contains("R.string.settings_ai_model_download_policy"))
        assertTrue(source.contains("R.string.settings_ai_model_download_now"))
        assertTrue(source.contains("R.string.settings_ai_model_delete_now"))
        assertTrue(source.contains("showDeleteLocalModelsDialog"))
        assertTrue(source.contains("aiLocalModelRepository?.deletePlan"))
        assertTrue(source.contains("R.string.settings_ai_model_device_tier"))
        assertTrue(!source.contains("R.string.settings_ai_model_auto_select"))
        assertTrue(!source.contains("R.string.settings_ai_model_download_latest"))
        assertTrue(!source.contains("R.string.settings_ai_model_huggingface_collection_placeholder"))
    }

    @Test
    fun settingsSubpagesUseTopBarBackHandler() {
        assertTrue(source.contains("BackHandler(enabled = selectedSettingsPage != null)"))
        assertTrue(source.contains("TopAppBar("))
        assertTrue(source.contains("selectedSettingsPage?.titleRes ?: R.string.settings"))
        assertTrue(source.contains("AnimatedContent("))
        assertTrue(source.contains("slideInHorizontally"))
        assertTrue(source.contains("slideOutHorizontally"))
        assertTrue(!source.contains("SettingsPageContent("))
    }

    @Test
    fun settingsAnimatedContentUsesSingleRootColumn() {
        val animatedContentStart = source.indexOf("AnimatedContent(")
        val contentLambdaStart = source.indexOf(") { page ->", animatedContentStart)
        val rootColumnStart = source.indexOf("Column(Modifier.fillMaxWidth())", contentLambdaStart)
        val categoryListStart = source.indexOf("SettingsCategoryList(aiTranslationAvailable = aiTranslationAvailable", contentLambdaStart)

        assertTrue(animatedContentStart >= 0)
        assertTrue(contentLambdaStart >= 0)
        assertTrue(rootColumnStart in contentLambdaStart until categoryListStart)
    }

    @Test
    fun aiApiKeyDialogUsesDynamicMultilineInput() {
        assertTrue(source.contains("singleLine = false"))
        assertTrue(source.contains("minLines = 1"))
        assertTrue(source.contains("maxLines = 6"))
        assertTrue(source.contains("TextSettingDialog("))
    }

    @Test
    fun settingsContentIncludesWebDavBackupOptions() {
        assertTrue(source.contains("R.string.settings_section_webdav_backup"))
        assertTrue(source.contains("R.string.settings_webdav_url"))
        assertTrue(source.contains("R.string.settings_webdav_username"))
        assertTrue(source.contains("R.string.settings_webdav_password"))
        assertTrue(source.contains("R.string.settings_webdav_backup_hint"))
        assertTrue(source.contains("webDavPasswordDisplayText(secureWebDavSettings.password)"))
        assertTrue(source.contains("secureWebDavSettings.username.ifBlank { stringResource(R.string.settings_not_configured) }"))
        assertTrue(source.contains("passwordInput = true"))
        assertTrue(!source.contains("R.string.settings_webdav_backup_scope"))
        assertTrue(source.contains("R.string.settings_webdav_backup_now"))
        assertTrue(source.contains("webDavBackupRepository?.backupNow()"))
        assertTrue(source.contains("R.string.settings_webdav_restore_now"))
        assertTrue(source.contains("webDavBackupRepository?.listBackups()"))
        assertTrue(source.contains("webDavBackupRepository?.restoreBackup("))
        val restoreStart = source.indexOf("val result = app?.webDavBackupRepository?.restoreBackup(fileName)")
        assertTrue(restoreStart >= 0)
        val restoreEnd = source.indexOf("android.widget.Toast.makeText(context, message", restoreStart)
        assertTrue(restoreEnd > restoreStart)
        val restoreSource = source.substring(restoreStart, restoreEnd)
        assertTrue(restoreSource.contains("secureAiSettings = app?.secureAiSettingsStore?.read() ?: secureAiSettings"))
        assertTrue(restoreSource.contains("context.getString(R.string.settings_webdav_restore_success)"))
        assertTrue(source.contains("showWebDavBackupPicker"))
        assertTrue(source.contains("webDavBackupFiles"))
        assertTrue(source.contains("R.string.settings_webdav_restore_select_backup"))
        assertTrue(source.contains("R.string.settings_webdav_restore_no_backups"))
    }
}
