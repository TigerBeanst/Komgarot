package fail.tiger.komgarot.ui.metadata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.BookMetadataUpdateDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataUpdateDto
import fail.tiger.komgarot.data.repository.BookRepository
import kotlinx.coroutines.launch

class MetadataViewModel(private val repo: BookRepository) : ViewModel() {
    var seriesMeta by mutableStateOf<SeriesMetadataDto?>(null)
    var bookMeta by mutableStateOf<BookMetadataDto?>(null)
    var saving by mutableStateOf(false)
    var saved by mutableStateOf(false)

    fun loadSeries(id: String) {
        viewModelScope.launch { runCatching { seriesMeta = repo.getSeriesMetadata(id) } }
    }

    fun loadBook(id: String) {
        viewModelScope.launch { runCatching { bookMeta = repo.getBookMetadata(id) } }
    }

    fun saveSeriesMeta(id: String, meta: SeriesMetadataDto) {
        viewModelScope.launch {
            saving = true
            val body = SeriesMetadataUpdateDto(
                title = meta.title,
                titleSort = meta.titleSort,
                status = meta.status,
                summary = meta.summary,
                publisher = meta.publisher,
                ageRating = meta.ageRating,
                language = meta.language,
                readingDirection = meta.readingDirection,
                alternateTitles = meta.alternateTitles,
                genres = meta.genres,
                tags = meta.tags,
                sharingLabels = meta.sharingLabels,
                links = meta.links,
                totalBookCount = meta.totalBookCount,
                titleLock = meta.titleLock,
                titleSortLock = meta.titleSortLock,
                statusLock = meta.statusLock,
                summaryLock = meta.summaryLock,
                publisherLock = meta.publisherLock,
                ageRatingLock = meta.ageRatingLock,
                languageLock = meta.languageLock,
                readingDirectionLock = meta.readingDirectionLock,
                alternateTitlesLock = meta.alternateTitlesLock,
                genresLock = meta.genresLock,
                tagsLock = meta.tagsLock,
                sharingLabelsLock = meta.sharingLabelsLock,
                linksLock = meta.linksLock,
                totalBookCountLock = meta.totalBookCountLock
            )
            runCatching { repo.updateSeriesMetadata(id, body) }.onSuccess { saved = true }
            saving = false
        }
    }

    fun saveBookMeta(id: String, meta: BookMetadataDto) {
        viewModelScope.launch {
            saving = true
            val body = BookMetadataUpdateDto(
                title = meta.title,
                summary = meta.summary,
                number = meta.number,
                numberSort = meta.numberSort,
                releaseDate = meta.releaseDate,
                isbn = meta.isbn,
                authors = meta.authors,
                tags = meta.tags,
                links = meta.links,
                titleLock = meta.titleLock,
                summaryLock = meta.summaryLock,
                numberLock = meta.numberLock,
                numberSortLock = meta.numberSortLock,
                releaseDateLock = meta.releaseDateLock,
                isbnLock = meta.isbnLock,
                authorsLock = meta.authorsLock,
                tagsLock = meta.tagsLock,
                linksLock = meta.linksLock
            )
            runCatching { repo.updateBookMetadata(id, body) }.onSuccess { saved = true }
            saving = false
        }
    }

    fun refreshMetadata(type: String, id: String) {
        viewModelScope.launch {
            saving = true
            runCatching {
                if (type == "series") repo.refreshSeriesMetadata(id) else repo.refreshBookMetadata(id)
            }.onSuccess {
                if (type == "series") loadSeries(id) else loadBook(id)
            }
            saving = false
        }
    }

    class Factory(private val repo: BookRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MetadataViewModel(repo) as T
    }
}
