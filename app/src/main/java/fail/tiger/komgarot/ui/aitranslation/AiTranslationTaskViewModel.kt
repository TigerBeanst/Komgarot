package fail.tiger.komgarot.ui.aitranslation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.data.local.AiTranslationStore
import fail.tiger.komgarot.data.local.AiTranslationTaskState

class AiTranslationTaskViewModel(
    private val store: AiTranslationStore
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

    class Factory(
        store: AiTranslationStore
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer { AiTranslationTaskViewModel(store) }
    })
}
