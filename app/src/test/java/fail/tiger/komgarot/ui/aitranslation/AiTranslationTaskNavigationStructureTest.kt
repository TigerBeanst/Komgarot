package fail.tiger.komgarot.ui.aitranslation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationTaskNavigationStructureTest {
    @Test
    fun taskActionMenuCanOpenBookDetail() {
        val source = File("src/main/java/fail/tiger/komgarot/ui/aitranslation/AiTranslationTaskScreen.kt").readText()

        assertTrue(source.contains("onOpenBook: (AiTranslationTaskSummary) -> Unit"))
        assertTrue(source.contains("R.string.ai_translation_open_book_detail"))
        assertTrue(source.contains("onOpenBook(task)"))
    }
}
