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
import retrofit2.HttpException

sealed interface CoverUploadResult {
    data object Success : CoverUploadResult
    data class Failure(val message: String) : CoverUploadResult
}

class MetadataViewModel(
    private val repo: BookRepository,
    private val imageCacheInvalidator: ImageCacheInvalidator,
    private val saveFailedMessage: String,
    private val metadataAdminRequiredMessage: String,
    private val coverSaveFailedMessage: String,
    private val coverPayloadTooLargeMessage: String
) : ViewModel() {
    var seriesMeta by mutableStateOf<SeriesMetadataDto?>(null)
    var bookMeta by mutableStateOf<BookMetadataDto?>(null)
    var bookSeriesId by mutableStateOf("")
        private set
    var bookSeriesMeta by mutableStateOf<SeriesMetadataDto?>(null)
        private set
    var metadataLanguages by mutableStateOf<List<String>>(emptyList())
        private set
    var saving by mutableStateOf(false)
    var saved by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)
    var coverSaving by mutableStateOf(false)

    fun loadSeries(id: String) {
        viewModelScope.launch { runCatching { seriesMeta = repo.getSeriesMetadata(id) } }
        loadLanguages()
    }

    fun loadBook(id: String) {
        viewModelScope.launch {
            runCatching { repo.getBookById(id).getOrThrow() }
                .onSuccess { book ->
                    bookMeta = book.metadata
                    bookSeriesId = book.seriesId
                    bookSeriesMeta = runCatching { repo.getSeriesMetadata(book.seriesId) }.getOrNull()
                }
        }
        loadLanguages()
    }

    private fun loadLanguages() {
        viewModelScope.launch {
            metadataLanguages = loadMetadataLanguages { repo.getLanguages() }
        }
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

    fun saveBookMeta(
        id: String,
        meta: BookMetadataDto,
        seriesLanguage: String,
        seriesLanguageLock: Boolean,
        onDone: (Boolean) -> Unit = {}
    ) {
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
            val bookSaved = when (result) {
                is MetadataSaveResult.Success -> {
                    bookMeta = result.metadata
                    true
                }
                is MetadataSaveResult.Failure -> {
                    saveError = result.message
                    false
                }
            }
            val currentSeriesMeta = bookSeriesMeta
            val seriesResult = if (
                bookSaved &&
                currentSeriesMeta != null &&
                bookSeriesId.isNotBlank() &&
                (currentSeriesMeta.language != seriesLanguage || currentSeriesMeta.languageLock != seriesLanguageLock)
            ) {
                saveSeriesLanguageMetadata(
                    current = currentSeriesMeta,
                    language = seriesLanguage,
                    languageLock = seriesLanguageLock,
                    fallbackErrorMessage = saveFailedMessage,
                    forbiddenErrorMessage = metadataAdminRequiredMessage,
                    update = { body -> repo.updateSeriesLanguage(bookSeriesId, body) }
                )
            } else {
                null
            }
            val ok = when (seriesResult) {
                is MetadataSaveResult.Success -> {
                    bookSeriesMeta = seriesResult.metadata
                    true
                }
                is MetadataSaveResult.Failure -> {
                    saveError = seriesResult.message
                    false
                }
                null -> bookSaved
            }
            saved = ok
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

    fun uploadBookCover(id: String, imageBytes: ByteArray, onDone: (CoverUploadResult) -> Unit = {}) {
        viewModelScope.launch {
            coverSaving = true
            val seriesId = repo.getBookById(id).getOrNull()?.seriesId
            val result = runCatching { repo.uploadBookThumbnail(id, imageBytes, "image/jpeg") }
                .onSuccess {
                    imageCacheInvalidator.invalidateBookThumbnail(id)
                    seriesId?.takeIf { it.isNotBlank() }?.let { imageCacheInvalidator.invalidateSeriesThumbnail(it) }
                }
                .fold(
                    onSuccess = { CoverUploadResult.Success },
                    onFailure = {
                        CoverUploadResult.Failure(
                            coverUploadFailureMessage(
                                error = it,
                                fallbackMessage = coverSaveFailedMessage,
                                forbiddenMessage = metadataAdminRequiredMessage,
                                payloadTooLargeMessage = coverPayloadTooLargeMessage
                            )
                        )
                    }
                )
            coverSaving = false
            onDone(result)
        }
    }

    fun uploadSeriesCover(id: String, imageBytes: ByteArray, onDone: (CoverUploadResult) -> Unit = {}) {
        viewModelScope.launch {
            coverSaving = true
            val result = runCatching { repo.uploadSeriesThumbnail(id, imageBytes, "image/jpeg") }
                .onSuccess { imageCacheInvalidator.invalidateSeriesThumbnail(id) }
                .fold(
                    onSuccess = { CoverUploadResult.Success },
                    onFailure = {
                        CoverUploadResult.Failure(
                            coverUploadFailureMessage(
                                error = it,
                                fallbackMessage = coverSaveFailedMessage,
                                forbiddenMessage = metadataAdminRequiredMessage,
                                payloadTooLargeMessage = coverPayloadTooLargeMessage
                            )
                        )
                    }
                )
            coverSaving = false
            onDone(result)
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
                textProvider.get(R.string.metadata_admin_required),
                textProvider.get(R.string.metadata_cover_save_failed),
                textProvider.get(R.string.metadata_cover_payload_too_large)
            )
        }
    })
}

internal fun coverUploadFailureMessage(
    error: Throwable,
    fallbackMessage: String,
    forbiddenMessage: String,
    payloadTooLargeMessage: String = fallbackMessage
): String {
    if (error is HttpException) {
        val status = buildString {
            append("HTTP ").append(error.code())
            error.response()?.message()
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "Response.error()" }
                ?.let {
                    append(" ").append(it)
                }
        }
        val body = runCatching { error.response()?.errorBody()?.string().orEmpty() }
            .getOrDefault("")
            .compactCoverFailureDetail()
        val detail = listOf(status, body)
            .filter(String::isNotBlank)
            .joinToString(": ")
        return when (error.code()) {
            403 -> "$forbiddenMessage ($detail)"
            413 -> "$payloadTooLargeMessage ($detail)"
            else -> "$fallbackMessage: $detail"
        }
    }

    val detail = error.message
        ?.compactCoverFailureDetail()
        ?.takeIf(String::isNotBlank)
        ?: error.javaClass.simpleName
    return "$fallbackMessage: $detail"
}

private fun String.compactCoverFailureDetail(): String =
    trim().replace(Regex("\\s+"), " ").take(240)
