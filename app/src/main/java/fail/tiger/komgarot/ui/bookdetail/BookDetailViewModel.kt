package fail.tiger.komgarot.ui.bookdetail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.BookDownloadCache
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesDto
import fail.tiger.komgarot.data.local.ImageCacheInvalidator
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.SeriesRepository
import fail.tiger.komgarot.ui.i18n.UiTextProvider
import kotlinx.coroutines.launch

sealed interface BookDownloadState {
    val isRunning: Boolean get() = false

    data object Idle : BookDownloadState

    data class Partial(
        val completedPages: Int,
        val totalPages: Int
    ) : BookDownloadState

    data class Downloading(
        val completedPages: Int,
        val totalPages: Int
    ) : BookDownloadState {
        override val isRunning: Boolean get() = true
    }

    data class Cached(val totalPages: Int) : BookDownloadState

    data class Failed(val message: String) : BookDownloadState
}

internal fun bookDownloadStateForCachedPages(completedPages: Int, totalPages: Int): BookDownloadState {
    if (totalPages <= 0 || completedPages <= 0) return BookDownloadState.Idle
    return if (completedPages >= totalPages) {
        BookDownloadState.Cached(totalPages)
    } else {
        BookDownloadState.Partial(completedPages, totalPages)
    }
}

class BookDetailViewModel(
    private val bookRepo: BookRepository,
    private val seriesRepo: SeriesRepository,
    private val imageCacheInvalidator: ImageCacheInvalidator,
    private val downloadCache: BookDownloadCache,
    private val loadBookDetailFailed: String
) : ViewModel() {
    var book by mutableStateOf<BookDto?>(null)
    var series by mutableStateOf<SeriesDto?>(null)
    var metadata by mutableStateOf<BookMetadataDto?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var downloadState by mutableStateOf<BookDownloadState>(BookDownloadState.Idle)
    private var currentBookId = ""
    private var currentServerUrl = ""

    fun load(bookId: String, serverUrl: String) {
        currentBookId = bookId
        currentServerUrl = serverUrl
        viewModelScope.launch {
            loading = true
            error = null
            bookRepo.getBookById(bookId)
                .onSuccess {
                    book = it
                    metadata = it.metadata
                    updateDownloadState(serverUrl, it)
                }
                .onFailure {
                    error = it.message?.takeIf { message -> message.isNotBlank() } ?: loadBookDetailFailed
                }
            loading = false
        }
    }

    fun refresh() {
        imageCacheInvalidator.invalidateBook(currentBookId, book?.seriesId)
        book = null
        metadata = null
        load(currentBookId, currentServerUrl)
    }

    fun markRead() {
        val loaded = book ?: return
        viewModelScope.launch {
            runCatching { bookRepo.updateReadProgress(loaded.id, loaded.media.pagesCount.coerceAtLeast(1), completed = true) }
                .onSuccess { load(loaded.id, currentServerUrl) }
        }
    }

    fun markUnread() {
        val loaded = book ?: return
        viewModelScope.launch {
            runCatching { bookRepo.deleteBookReadProgress(loaded.id) }
                .onSuccess { load(loaded.id, currentServerUrl) }
        }
    }

    fun downloadForOffline(serverUrl: String) {
        if (downloadState.isRunning || currentBookId.isBlank()) return
        viewModelScope.launch {
            downloadState = BookDownloadState.Downloading(0, 0)
            runCatching {
                downloadCache.cacheBook(serverUrl, currentBookId, book) { progress ->
                    downloadState = BookDownloadState.Downloading(progress.completedPages, progress.totalPages)
                }
            }.onSuccess { totalPages ->
                downloadState = BookDownloadState.Cached(totalPages)
            }.onFailure { throwable ->
                downloadState = BookDownloadState.Failed(
                    throwable.message?.takeIf { it.isNotBlank() } ?: loadBookDetailFailed
                )
            }
        }
    }

    fun clearOfflineCache() {
        if (downloadState.isRunning || currentBookId.isBlank()) return
        downloadCache.clearBook(currentBookId)
        downloadState = BookDownloadState.Idle
    }

    class Factory(
        context: Context,
        bookRepo: BookRepository,
        seriesRepo: SeriesRepository,
        imageCacheInvalidator: ImageCacheInvalidator,
        textProvider: UiTextProvider
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer {
            BookDetailViewModel(
                bookRepo,
                seriesRepo,
                imageCacheInvalidator,
                BookDownloadCache(context.applicationContext, bookRepo),
                textProvider.get(R.string.error_load_book_detail_failed)
            )
        }
    })

    private suspend fun updateDownloadState(serverUrl: String, loaded: BookDto) {
        if (downloadState.isRunning || serverUrl.isBlank()) return
        runCatching { downloadCache.getProgress(serverUrl, loaded) }
            .onSuccess { progress ->
                downloadState = bookDownloadStateForCachedPages(progress.completedPages, progress.totalPages)
            }
    }
}
