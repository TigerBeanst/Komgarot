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
        assertTrue(source.contains("SettingsPageContent("))
        assertTrue(source.contains("enum class SettingsPage"))
        assertTrue(source.contains("var selectedSettingsPage by remember { mutableStateOf<SettingsPage?>(null) }"))
        assertTrue(source.contains("when (page)"))
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_cache))"))
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_reading))"))
        assertTrue(source.contains("SettingsSectionHeader(stringResource(R.string.settings_section_ai_models))"))
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
        assertTrue(source.contains("private val aiOnlySettingsPages"))
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
    fun settingsContentIncludesAiTranslationOptions() {
        assertTrue(source.contains("R.string.settings_section_ai_translation"))
        assertTrue(source.contains("R.string.settings_ai_translation_enabled"))
        assertTrue(source.contains("R.string.settings_ai_api_key"))
        assertTrue(source.contains("R.string.settings_ai_target_language"))
        assertTrue(source.contains("AiTargetLanguageOption"))
        assertTrue(source.contains("showAiTargetLanguageMenu"))
        assertTrue(source.contains("R.string.settings_ai_image_transport"))
        assertTrue(source.contains("R.string.settings_ai_image_url_extra_query"))
        assertTrue(source.contains("R.string.settings_ai_model_requires_vision"))
        assertTrue(source.contains("R.string.settings_ai_base_url_placeholder"))
        assertTrue(source.contains("R.string.settings_ai_pages_per_request"))
        assertTrue(source.contains("R.string.settings_ai_max_images_per_request"))
        assertTrue(source.contains("R.string.settings_ai_concurrent_requests"))
        assertTrue(source.contains("R.string.settings_ai_timeout"))
        assertTrue(source.contains("R.string.settings_ai_vertical_glyph_spacing"))
        assertTrue(source.contains("showAiVerticalGlyphSpacingDialog"))
        assertTrue(source.contains("showAiTimeoutDialog"))
        assertTrue(source.contains("showAiMaxImagesPerRequestDialog"))
        assertTrue(source.contains("prefs.setAiTimeoutSeconds"))
        assertTrue(source.contains("prefs.setAiVerticalGlyphSpacingPercent"))
        assertTrue(source.contains("prefs.setAiMaxImagesPerRequest"))
        assertTrue(source.contains("R.string.settings_ai_test_mode"))
        assertTrue(source.contains("R.string.ai_translation_mode_local_detection"))
        assertTrue(!source.contains("R.string.settings_ai_default_mode"))
        assertTrue(!source.contains("showAiModeDialog"))
        assertTrue(!source.contains("AiTranslationMode.entries"))
    }

    @Test
    fun settingsContentIncludesAiModelManagementOptions() {
        assertTrue(source.contains("R.string.settings_section_ai_models"))
        assertTrue(source.contains("R.string.settings_ai_model_source"))
        assertTrue(source.contains("R.string.settings_ai_model_collection"))
        assertTrue(source.contains("R.string.settings_ai_model_revision"))
        assertTrue(source.contains("R.string.settings_ai_model_download_policy"))
        assertTrue(source.contains("R.string.settings_ai_model_download_now"))
        assertTrue(source.contains("R.string.settings_ai_model_delete_now"))
        assertTrue(source.contains("showDeleteLocalModelsDialog"))
        assertTrue(source.contains("aiLocalModelRepository?.deletePlan"))
        assertTrue(source.contains("R.string.settings_ai_model_device_tier"))
        assertTrue(source.contains("R.string.settings_ai_model_auto_select"))
        assertTrue(source.contains("R.string.settings_ai_model_download_latest"))
        assertTrue(source.contains("R.string.settings_ai_model_huggingface_collection_placeholder"))
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
        assertTrue(source.contains("R.string.settings_webdav_backup_excludes_credentials"))
        assertTrue(source.contains("R.string.settings_webdav_backup_now"))
        assertTrue(source.contains("webDavBackupRepository?.backupNow()"))
    }
}
