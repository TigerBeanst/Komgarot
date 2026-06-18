package fail.tiger.komgarot.ui.bookdetail

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationBookDetailStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailScreen.kt").readText()
    private val viewModelSource = File("src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailViewModel.kt").readText()

    @Test
    fun bookDetailIncludesAiTranslationAction() {
        assertTrue(source.contains("AiTranslationBookAction"))
        assertTrue(source.contains("R.string.ai_translate_book"))
        assertTrue(source.contains("R.string.ai_translate_config_required"))
        assertTrue(source.contains("R.string.ai_translation_mode_local_detection"))
        assertTrue(!source.contains("AiTranslationModeQuickSelector"))
        assertTrue(!source.contains("R.string.ai_translation_mode_detect"))
        assertTrue(!source.contains("R.string.ai_translation_mode_vision"))
    }

    @Test
    fun deleteAiTranslationUsesTwoDistinctDialogs() {
        assertTrue(source.contains("R.string.ai_translate_delete_message_first"))
        assertTrue(source.contains("R.string.ai_translate_delete_message_final"))
        assertTrue(source.contains("R.string.ai_translate_delete_continue"))
        assertTrue(source.contains("R.string.ai_translate_delete_confirm"))
    }

    @Test
    fun startingBookTranslationShowsImmediateRunningFeedbackAndEnablesReaderDisplay() {
        assertTrue(source.contains("state.running"))
        assertTrue(source.indexOf("state.running") < source.indexOf("state.hasAnyResult"))
        assertTrue(source.contains("vm.aiTranslationState.running -> vm.refreshAiTranslationState()"))
        assertTrue(viewModelSource.contains("setTranslationDisplayEnabled(true)"))
        assertTrue(viewModelSource.contains("running = true"))
    }
}
