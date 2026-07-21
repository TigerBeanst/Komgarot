package fail.tiger.komgarot.ui.metadata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.ImageCacheInvalidator
import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.ui.i18n.UiTextProvider
import kotlinx.coroutines.launch

class MetadataViewModel(
    private val repo: BookRepository,
    private val imageCacheInvalidator: ImageCacheInvalidator,
    private val saveFailedMessage: String,
    private val metadataAdminRequiredMessage: String
) : ViewModel() {
    var seriesMeta by mutableStateOf<SeriesMetadataDto?>(null)
    var bookMeta by mutableStateOf<BookMetadataDto?>(null)
    var seriesLanguages by mutableStateOf<List<String>>(emptyList())
        private set
    var saving by mutableStateOf(false)
    var saved by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)
    var coverSaving by mutableStateOf(false)

    fun loadSeries(id: String) {
        viewModelScope.launch { runCatching { seriesMeta = repo.getSeriesMetadata(id) } }
        viewModelScope.launch {
            seriesLanguages = loadMetadataLanguages { repo.getLanguages() }
        }
    }

    fun loadBook(id: String) {
        viewModelScope.launch { runCatching { bookMeta = repo.getBookMetadata(id) } }
    }

    fun saveSeriesMeta(id: String, meta: SeriesMetadataDto, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            saving = true
            saved = false
            saveError = null
            val result = saveSeriesMetadata(
                meta = meta,
                fallbackErrorMessage = saveFailedMessage,
                forbiddenErrorMessage = metadataAdminRequiredMessage,
                update = { body -> repo.updateSeriesMetadata(id, body) }
            )
            val ok = when (result) {
                is MetadataSaveResult.Success -> {
                    seriesMeta = result.metadata
                    saved = true
                    true
                }
                is MetadataSaveResult.Failure -> {
                    saveError = result.message
                    false
                }
            }
            saving = false
            onDone(ok)
        }
    }

    fun saveBookMeta(id: String, meta: BookMetadataDto, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            saving = true
            saved = false
            saveError = null
            val result = saveBookMetadata(
                meta = meta,
                fallbackErrorMessage = saveFailedMessage,
                forbiddenErrorMessage = metadataAdminRequiredMessage,
                update = { body -> repo.updateBookMetadata(id, body) }
            )
            val ok = when (result) {
                is MetadataSaveResult.Success -> {
                    bookMeta = result.metadata
                    saved = true
                    true
                }
                is MetadataSaveResult.Failure -> {
                    saveError = result.message
                    false
                }
            }
            saving = false
            onDone(ok)
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

    fun uploadBookCover(id: String, imageBytes: ByteArray, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            coverSaving = true
            val seriesId = repo.getBookById(id).getOrNull()?.seriesId
            val ok = runCatching { repo.uploadBookThumbnail(id, imageBytes, "image/jpeg") }
                .onSuccess {
                    imageCacheInvalidator.invalidateBookThumbnail(id)
                    seriesId?.takeIf { it.isNotBlank() }?.let { imageCacheInvalidator.invalidateSeriesThumbnail(it) }
                }
                .isSuccess
            coverSaving = false
            onDone(ok)
        }
    }

    fun uploadSeriesCover(id: String, imageBytes: ByteArray, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            coverSaving = true
            val ok = runCatching { repo.uploadSeriesThumbnail(id, imageBytes, "image/jpeg") }
                .onSuccess { imageCacheInvalidator.invalidateSeriesThumbnail(id) }
                .isSuccess
            coverSaving = false
            onDone(ok)
        }
    }

    class Factory(
        repo: BookRepository,
        imageCacheInvalidator: ImageCacheInvalidator,
        textProvider: UiTextProvider
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer {
            MetadataViewModel(
                repo,
                imageCacheInvalidator,
                textProvider.get(R.string.save_failed),
                textProvider.get(R.string.metadata_admin_required)
            )
        }
    })
}
