package fail.tiger.komgarot.ui.aitranslation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.data.local.AiTranslationStore
import fail.tiger.komgarot.data.local.AiTranslationPageStatus
import fail.tiger.komgarot.data.local.AiTranslationTaskSummary
import fail.tiger.komgarot.data.local.AiTranslationTaskState
import fail.tiger.komgarot.data.local.AiTranslationTaskStatus
import fail.tiger.komgarot.data.repository.AiTranslationRepository
import kotlinx.coroutines.launch

class AiTranslationTaskViewModel(
    private val store: AiTranslationStore,
    private val repository: AiTranslationRepository?,
    private val serverUrl: String
) : ViewModel() {
    var state by mutableStateOf(store.readTaskState())
        private set

    fun refresh() {
        state = store.readTaskState()
    }

    fun pauseAll() {
        state = state.copy(
            paused = true,
            tasks = state.tasks.map { task ->
                if (task.status == AiTranslationTaskStatus.QUEUED || task.status == AiTranslationTaskStatus.RUNNING) {
                    task.copy(status = AiTranslationTaskStatus.PAUSED)
                } else {
                    task
                }
            }
        )
        store.saveTaskState(state)
    }

    fun resumeAll() {
        val recoveredTasks = state.tasks.filter { task ->
            task.status == AiTranslationTaskStatus.PAUSED && task.recoveryRequired
        }
        state = state.copy(
            paused = false,
            tasks = state.tasks.map { task ->
                if (task.status == AiTranslationTaskStatus.PAUSED) {
                    task.copy(
                        status = if (task.recoveryRequired) {
                            AiTranslationTaskStatus.QUEUED
                        } else {
                            AiTranslationTaskStatus.RUNNING
                        },
                        recoveryRequired = false
                    )
                } else {
                    task
                }
            }
        )
        store.saveTaskState(state)
        recoveredTasks.forEach { task ->
            repository?.resumeTaskTranslation(task.bookId, serverUrl, task.targetPageIndexes)
        }
    }

    fun retryIncompletePages(task: AiTranslationTaskSummary) {
        state = state.copy(
            tasks = state.tasks.map { current ->
                if (current.bookId == task.bookId) {
                    current.copy(
                        status = if (state.paused) AiTranslationTaskStatus.PAUSED else AiTranslationTaskStatus.QUEUED,
                        recoveryRequired = false
                    )
                } else {
                    current
                }
            }
        )
        store.saveTaskState(state)
        repository?.retryTaskTranslation(task.bookId, serverUrl, task.targetPageIndexes)
    }

    fun navigationPageFor(task: AiTranslationTaskSummary): Int {
        val targetSet = task.targetPageIndexes.toSet()
        val storedPages = store.readBook(task.bookId)?.pages.orEmpty()
        val failedPage = storedPages.firstOrNull { page ->
            page.status == AiTranslationPageStatus.FAILED &&
                (targetSet.isEmpty() || page.pageIndex in targetSet)
        }
        return ((failedPage?.pageIndex ?: task.targetPageIndexes.minOrNull() ?: 0) + 1).coerceAtLeast(1)
    }

    fun clearBookTranslation(bookId: String) {
        viewModelScope.launch {
            repository?.clearBook(bookId) ?: store.clearBook(bookId)
            val current = store.readTaskState()
            store.saveTaskState(current.copy(tasks = current.tasks.filterNot { it.bookId == bookId }))
            refresh()
        }
    }

    fun clearAllTranslations() {
        viewModelScope.launch {
            repository?.clearAllTranslations() ?: store.clearAll()
            refresh()
        }
    }

    class Factory(
        store: AiTranslationStore,
        repository: AiTranslationRepository? = null,
        serverUrl: String = ""
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer { AiTranslationTaskViewModel(store, repository, serverUrl) }
    })
}
