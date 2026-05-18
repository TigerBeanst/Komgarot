package fail.tiger.komgarot.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
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
    private val _onDeckBooks = MutableStateFlow<List<BookDto>>(emptyList())
    val onDeckBooks = _onDeckBooks.asStateFlow()
    private val _latestBooks = MutableStateFlow<List<BookDto>>(emptyList())
    val latestBooks = _latestBooks.asStateFlow()
    private val _updatedSeries = MutableStateFlow<List<SeriesDto>>(emptyList())
    val updatedSeries = _updatedSeries.asStateFlow()
    private val _newSeries = MutableStateFlow<List<SeriesDto>>(emptyList())
    val newSeries = _newSeries.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val failures = mutableListOf<String>()

            runCatching { repo.getLibraries() }
                .onSuccess { _libraries.value = it }
                .onFailure { failures += it.readableMessage() }

            runCatching { repo.getBooksOnDeck() }
                .onSuccess { _onDeckBooks.value = it }
                .onFailure { failures += it.readableMessage() }

            runCatching { repo.getLatestBooks() }
                .onSuccess { _latestBooks.value = it }
                .onFailure { failures += it.readableMessage() }

            runCatching { repo.getUpdatedSeries() }
                .onSuccess { _updatedSeries.value = it }
                .onFailure { failures += it.readableMessage() }

            runCatching { repo.getNewSeries() }
                .onSuccess { _newSeries.value = it }
                .onFailure { failures += it.readableMessage() }

            _error.value = failures.firstOrNull()
            _loading.value = false
        }
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

private fun Throwable.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "连接 Komga 失败"
