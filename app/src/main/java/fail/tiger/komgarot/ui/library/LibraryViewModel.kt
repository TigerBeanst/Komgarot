package fail.tiger.komgarot.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import fail.tiger.komgarot.data.repository.AuthRepository
import fail.tiger.komgarot.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repo: LibraryRepository,
    private val authRepo: AuthRepository,
    val prefs: AuthPreferences
) : ViewModel() {
    private val _libraries = MutableStateFlow<List<LibraryDto>>(emptyList())
    val libraries = _libraries.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch { runCatching { _libraries.value = repo.getLibraries() } }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch { authRepo.logout(); onDone() }
    }

    class Factory(
        private val repo: LibraryRepository,
        private val authRepo: AuthRepository,
        private val prefs: AuthPreferences
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(repo, authRepo, prefs) as T
    }
}
