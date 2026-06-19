package fail.tiger.komgarot.ui.bookdetail

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationBookDetailStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailScreen.kt").readText()

    @Test
    fun bookDetailDoesNotExposeAiTranslationAction() {
        assertTrue(!source.contains("AiTranslationBookAction("))
        assertTrue(!source.contains("showAiActionMenu"))
        assertTrue(!source.contains("AiTranslationModeQuickSelector"))
        assertTrue(!source.contains("R.string.ai_translation_mode_detect"))
        assertTrue(!source.contains("R.string.ai_translation_mode_vision"))
    }

    @Test
    fun bookDetailDoesNotContainAiTranslationDeleteDialogs() {
        assertTrue(!source.contains("R.string.ai_translate_delete_message_first"))
        assertTrue(!source.contains("R.string.ai_translate_delete_message_final"))
        assertTrue(!source.contains("R.string.ai_translate_delete_continue"))
        assertTrue(!source.contains("R.string.ai_translate_delete_confirm"))
    }

    @Test
    fun bookDetailScreenHasNoAiTranslationProgressLoop() {
        assertTrue(!source.contains("while (vm.aiTranslationState.running)"))
        assertTrue(!source.contains("AiTranslationBookActionMenu"))
    }
}
