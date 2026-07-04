package fail.tiger.komgarot.ui.aitranslation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationTaskScreenStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/ui/aitranslation/AiTranslationTaskScreen.kt").readText()
    private val viewModelSource = File("src/main/java/fail/tiger/komgarot/ui/aitranslation/AiTranslationTaskViewModel.kt").readText()

    @Test
    fun taskItemsOpenActionMenu() {
        assertTrue(source.contains("showTaskActionMenu"))
        assertTrue(source.contains("AiTranslationTaskActionMenu"))
        assertTrue(source.contains("modifier = Modifier.clickable"))
        assertTrue(source.contains("R.string.ai_translate_retry_incomplete_pages"))
        assertTrue(source.contains("R.string.ai_translate_delete_book_translation"))
    }

    @Test
    fun taskListRefreshesWhileWorkIsActiveAndShowsProgressState() {
        assertTrue(source.contains("while (vm.state.tasks.any { it.isActiveAiTranslationTask() })"))
        assertTrue(source.contains("delay(1000)"))
        assertTrue(source.contains("LinearProgressIndicator("))
        assertTrue(source.contains("taskStatusLabelRes(task.status)"))
        assertTrue(source.contains("aiTranslationTaskProgress(task)"))
    }

    @Test
    fun taskListShowsOverviewAndSortsActionableTasksFirst() {
        assertTrue(source.contains("AiTranslationTaskOverview(vm.state.tasks)"))
        assertTrue(source.contains("val sortedTasks = remember(vm.state.tasks)"))
        assertTrue(source.contains("aiTranslationTaskPriorityComparator"))
        assertTrue(source.contains("R.string.ai_translation_task_overview"))
        assertTrue(source.contains("activeAiTranslationTaskCount"))
        assertTrue(source.contains("failedAiTranslationPageCount"))
    }

    @Test
    fun taskListShowsFailureCategoryDiagnostics() {
        assertTrue(source.contains("aiTranslationFailureCategorySummary("))
        assertTrue(source.contains("task.failureCategories"))
        assertTrue(source.contains("R.string.ai_translation_failure_categories"))
    }

    @Test
    fun taskViewModelSupportsRetryIncompleteAndClearBook() {
        assertTrue(viewModelSource.contains("retryIncompletePages"))
        assertTrue(viewModelSource.contains("clearBookTranslation"))
        assertTrue(viewModelSource.contains("clearAllTranslations"))
        assertTrue(viewModelSource.contains("AiTranslationRepository"))
    }

    @Test
    fun taskScreenHasClearAllTopBarActionWithTwoConfirmations() {
        assertTrue(source.contains("clearAllFirstConfirmation"))
        assertTrue(source.contains("clearAllFinalConfirmation"))
        assertTrue(source.contains("actions = {"))
        assertTrue(source.contains("Icon(Icons.Default.Delete"))
        assertTrue(source.contains("R.string.ai_translate_clear_all_title"))
        assertTrue(source.contains("R.string.ai_translate_clear_all_message_first"))
        assertTrue(source.contains("R.string.ai_translate_clear_all_title_final"))
        assertTrue(source.contains("R.string.ai_translate_clear_all_message_final"))
        assertTrue(source.contains("vm.clearAllTranslations()"))
    }
}
