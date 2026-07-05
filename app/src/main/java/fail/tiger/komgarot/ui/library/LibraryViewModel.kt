package fail.tiger.komgarot.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.repository.AuthRepository
import fail.tiger.komgarot.data.repository.LibraryHomeSource
import fail.tiger.komgarot.ui.i18n.UiTextProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class LibraryViewModel(
    private val repo: LibraryHomeSource,
    private val authRepo: AuthRepository,
    private val connectFailedMessage: String
) : ViewModel() {
    private val _libraries = MutableStateFlow<List<LibraryDto>>(emptyList())
    val libraries = _libraries.asStateFlow()
    private val _continueReadingBooks = MutableStateFlow<List<BookDto>>(emptyList())
    val continueReadingBooks = _continueReadingBooks.asStateFlow()
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

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = loadLibraryHome(repo, connectFailedMessage)
            _libraries.value = result.libraries
            _continueReadingBooks.value = result.continueReadingBooks
            _latestBooks.value = result.latestBooks
            _updatedSeries.value = result.updatedSeries
            _newSeries.value = result.newSeries
            _error.value = result.failures.firstOrNull()
            _loading.value = false
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch { authRepo.logout(); onDone() }
    }

    class Factory(
        repo: LibraryHomeSource,
        authRepo: AuthRepository,
        textProvider: UiTextProvider
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer { LibraryViewModel(repo, authRepo, textProvider.get(R.string.error_connect_komga_failed)) }
    })
}

internal data class LibraryHomeLoadResult(
    val libraries: List<LibraryDto> = emptyList(),
    val continueReadingBooks: List<BookDto> = emptyList(),
    val latestBooks: List<BookDto> = emptyList(),
    val updatedSeries: List<SeriesDto> = emptyList(),
    val newSeries: List<SeriesDto> = emptyList(),
    val failures: List<String> = emptyList()
)

internal suspend fun loadLibraryHome(
    repo: LibraryHomeSource,
    fallbackMessage: String = "Komga connection failed"
): LibraryHomeLoadResult = supervisorScope {
    val libraries = async { runCatching { repo.getLibraries() } }
    val continueReadingBooks = async { runCatching { repo.getContinueReadingBooks() } }
    val latestBooks = async { runCatching { repo.getLatestBooks() } }
    val updatedSeries = async { runCatching { repo.getUpdatedSeries() } }
    val newSeries = async { runCatching { repo.getNewSeries() } }

    val failures = mutableListOf<String>()
    fun <T> Result<T>.valueOrDefault(defaultValue: T): T =
        onFailure { failures += it.readableMessage(fallbackMessage) }.getOrDefault(defaultValue)

    LibraryHomeLoadResult(
        libraries = libraries.await().valueOrDefault(emptyList()),
        continueReadingBooks = continueReadingBooks.await().valueOrDefault(emptyList()),
        latestBooks = latestBooks.await().valueOrDefault(emptyList()),
        updatedSeries = updatedSeries.await().valueOrDefault(emptyList()),
        newSeries = newSeries.await().valueOrDefault(emptyList()),
        failures = failures
    )
}

private fun Throwable.readableMessage(fallbackMessage: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallbackMessage
