package fail.tiger.komgarot.ui.aitranslation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.AiTranslationTaskSummary
import fail.tiger.komgarot.data.local.AiTranslationTaskStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTranslationTaskScreen(
    vm: AiTranslationTaskViewModel,
    onBack: () -> Unit
) {
    var showTaskActionMenu by remember { mutableStateOf<AiTranslationTaskSummary?>(null) }
    var deleteFirstConfirmation by remember { mutableStateOf<AiTranslationTaskSummary?>(null) }
    var deleteFinalConfirmation by remember { mutableStateOf<AiTranslationTaskSummary?>(null) }

    LaunchedEffect(vm.state.tasks) {
        while (vm.state.tasks.any { it.isActiveAiTranslationTask() }) {
            delay(1000)
            vm.refresh()
        }
    }
    val sortedTasks = remember(vm.state.tasks) {
        vm.state.tasks.sortedWith(aiTranslationTaskPriorityComparator)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_translation_tasks)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = { if (vm.state.paused) vm.resumeAll() else vm.pauseAll() }) {
                    Text(stringResource(if (vm.state.paused) R.string.ai_translation_resume_all else R.string.ai_translation_pause_all))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.refresh() }) {
                    Text(stringResource(R.string.refresh))
                }
            }
            if (vm.state.tasks.isEmpty()) {
                Text(stringResource(R.string.ai_translation_no_tasks), modifier = Modifier.padding(16.dp))
            } else {
                AiTranslationTaskOverview(vm.state.tasks)
                sortedTasks.forEach { task ->
                    ListItem(
                        headlineContent = { Text(task.title.ifBlank { task.bookId }) },
                        supportingContent = {
                            Column {
                                Text(stringResource(taskStatusLabelRes(task.status)))
                                Text(stringResource(R.string.ai_translate_progress, task.completedPages, task.pageCount, task.failedPages))
                                LinearProgressIndicator(
                                    progress = { aiTranslationTaskProgress(task) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                )
                            }
                        },
                        modifier = Modifier.clickable { showTaskActionMenu = task }
                    )
                }
            }
        }
    }

    showTaskActionMenu?.let { task ->
        AiTranslationTaskActionMenu(
            onDismiss = { showTaskActionMenu = null },
            onRetryIncomplete = {
                vm.retryIncompletePages(task.bookId)
                showTaskActionMenu = null
            },
            onDelete = {
                showTaskActionMenu = null
                deleteFirstConfirmation = task
            }
        )
    }

    deleteFirstConfirmation?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteFirstConfirmation = null },
            title = { Text(stringResource(R.string.ai_translate_delete_title)) },
            text = { Text(stringResource(R.string.ai_translate_delete_message_first)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteFirstConfirmation = null
                    deleteFinalConfirmation = task
                }) { Text(stringResource(R.string.ai_translate_delete_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFirstConfirmation = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    deleteFinalConfirmation?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteFinalConfirmation = null },
            title = { Text(stringResource(R.string.ai_translate_delete_title_final)) },
            text = { Text(stringResource(R.string.ai_translate_delete_message_final)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearBookTranslation(task.bookId)
                    deleteFinalConfirmation = null
                }) { Text(stringResource(R.string.ai_translate_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFinalConfirmation = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private fun AiTranslationTaskSummary.isActiveAiTranslationTask(): Boolean =
    status == AiTranslationTaskStatus.QUEUED || status == AiTranslationTaskStatus.RUNNING

private val aiTranslationTaskPriorityComparator = compareBy<AiTranslationTaskSummary> { task ->
    when {
        task.status == AiTranslationTaskStatus.RUNNING -> 0
        task.status == AiTranslationTaskStatus.QUEUED -> 1
        task.status == AiTranslationTaskStatus.FAILED || task.failedPages > 0 -> 2
        task.status == AiTranslationTaskStatus.PAUSED -> 3
        task.completedPages + task.failedPages < task.pageCount -> 4
        task.status == AiTranslationTaskStatus.DONE -> 5
        else -> 6
    }
}.thenByDescending { task -> task.updatedAt }

private fun activeAiTranslationTaskCount(tasks: List<AiTranslationTaskSummary>): Int =
    tasks.count { it.isActiveAiTranslationTask() }

private fun failedAiTranslationPageCount(tasks: List<AiTranslationTaskSummary>): Int =
    tasks.sumOf { it.failedPages }

@Composable
private fun AiTranslationTaskOverview(tasks: List<AiTranslationTaskSummary>) {
    val totalPages = tasks.sumOf { it.pageCount }.coerceAtLeast(1)
    val completedPages = tasks.sumOf { it.completedPages }
    val failedPages = failedAiTranslationPageCount(tasks)
    ListItem(
        headlineContent = {
            Text(
                stringResource(
                    R.string.ai_translation_task_overview,
                    activeAiTranslationTaskCount(tasks),
                    failedPages,
                    completedPages,
                    totalPages
                )
            )
        },
        supportingContent = {
            LinearProgressIndicator(
                progress = { ((completedPages + failedPages).toFloat() / totalPages.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    )
}

private fun aiTranslationTaskProgress(task: AiTranslationTaskSummary): Float {
    val pageCount = task.pageCount.coerceAtLeast(1)
    return ((task.completedPages + task.failedPages).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
}

private fun taskStatusLabelRes(status: AiTranslationTaskStatus): Int = when (status) {
    AiTranslationTaskStatus.QUEUED -> R.string.ai_translation_task_status_queued
    AiTranslationTaskStatus.RUNNING -> R.string.ai_translation_task_status_running
    AiTranslationTaskStatus.PAUSED -> R.string.ai_translation_task_status_paused
    AiTranslationTaskStatus.DONE -> R.string.ai_translation_task_status_done
    AiTranslationTaskStatus.FAILED -> R.string.ai_translation_task_status_failed
    AiTranslationTaskStatus.IDLE -> R.string.ai_translation_task_status_idle
}

@Composable
private fun AiTranslationTaskActionMenu(
    onDismiss: () -> Unit,
    onRetryIncomplete: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_translation_tasks)) },
        text = {
            Column {
                TextButton(onClick = onRetryIncomplete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ai_translate_retry_incomplete_pages))
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ai_translate_delete_book_translation))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
