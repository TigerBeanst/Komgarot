package fail.tiger.komgarot.ui.metadata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto
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
            val body = mapOf(
                "title" to meta.title, "titleSort" to meta.titleSort, "status" to meta.status,
                "summary" to meta.summary, "publisher" to meta.publisher, "ageRating" to meta.ageRating,
                "language" to meta.language, "genres" to meta.genres, "tags" to meta.tags
            )
            runCatching { repo.updateSeriesMetadata(id, body) }.onSuccess { saved = true }
            saving = false
        }
    }

    fun saveBookMeta(id: String, meta: BookMetadataDto) {
        viewModelScope.launch {
            saving = true
            val body = mapOf(
                "title" to meta.title, "summary" to meta.summary, "number" to meta.number,
                "releaseDate" to meta.releaseDate,
                "authors" to meta.authors.map { mapOf("name" to it.name, "role" to it.role) },
                "tags" to meta.tags
            )
            runCatching { repo.updateBookMetadata(id, body) }.onSuccess { saved = true }
            saving = false
        }
    }

    class Factory(private val repo: BookRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MetadataViewModel(repo) as T
    }
}
