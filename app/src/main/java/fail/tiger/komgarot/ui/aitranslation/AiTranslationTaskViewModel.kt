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
import fail.tiger.komgarot.data.local.AiTranslationTaskState
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
        state = state.copy(paused = true)
        store.saveTaskState(state)
    }

    fun resumeAll() {
        state = state.copy(paused = false)
        store.saveTaskState(state)
    }

    fun retryIncompletePages(bookId: String) {
        repository?.retryIncompleteBookTranslation(bookId, serverUrl)
        refresh()
    }

    fun clearBookTranslation(bookId: String) {
        repository?.clearBook(bookId) ?: store.clearBook(bookId)
        viewModelScope.launch {
            val current = store.readTaskState()
            store.saveTaskState(current.copy(tasks = current.tasks.filterNot { it.bookId == bookId }))
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
