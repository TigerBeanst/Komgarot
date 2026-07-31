package fail.tiger.komgarot.ui.metadata

import fail.tiger.komgarot.data.remote.dto.BookMetadataDto
import fail.tiger.komgarot.data.remote.dto.SeriesMetadataDto
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class MetadataSaveCoordinatorTest {
    @Test
    fun bookSaveSuccessReturnsUpdatedMetadata() = runBlocking {
        var savedTitle = ""

        val result = saveBookMetadata(
            meta = BookMetadataDto(title = "Updated"),
            fallbackErrorMessage = "Save failed",
            forbiddenErrorMessage = "Komga admin permission is required",
            update = { body -> savedTitle = body.title }
        )

        assertEquals("Updated", savedTitle)
        assertEquals(BookMetadataDto(title = "Updated"), (result as MetadataSaveResult.Success).metadata)
    }

    @Test
    fun bookSaveFailureReturnsFallbackMessage() = runBlocking {
        val result = saveBookMetadata(
            meta = BookMetadataDto(title = "Updated"),
            fallbackErrorMessage = "Save failed",
            forbiddenErrorMessage = "Komga admin permission is required",
            update = { throw IOException() }
        )

        assertTrue(result is MetadataSaveResult.Failure)
        assertEquals("Save failed", (result as MetadataSaveResult.Failure).message)
    }

    @Test
    fun bookSaveForbiddenReturnsPermissionMessage() = runBlocking {
        val result = saveBookMetadata(
            meta = BookMetadataDto(title = "Updated"),
            fallbackErrorMessage = "Save failed",
            forbiddenErrorMessage = "Komga admin permission is required",
            update = { throw HttpException(Response.error<Unit>(403, "".toResponseBody())) }
        )

        assertTrue(result is MetadataSaveResult.Failure)
        assertEquals("Komga admin permission is required", (result as MetadataSaveResult.Failure).message)
    }

    @Test
    fun seriesSaveSuccessReturnsUpdatedMetadata() = runBlocking {
        var savedTitle = ""
        var savedLanguage = ""
        var savedLanguageLock = false

        val result = saveSeriesMetadata(
            meta = SeriesMetadataDto(title = "Updated", language = "ko", languageLock = true),
            fallbackErrorMessage = "Save failed",
            forbiddenErrorMessage = "Komga admin permission is required",
            update = { body ->
                savedTitle = body.title
                savedLanguage = body.language
                savedLanguageLock = body.languageLock
            }
        )

        assertEquals("Updated", savedTitle)
        assertEquals("ko", savedLanguage)
        assertTrue(savedLanguageLock)
        assertEquals(
            SeriesMetadataDto(title = "Updated", language = "ko", languageLock = true),
            (result as MetadataSaveResult.Success).metadata
        )
    }

    @Test
    fun seriesSaveCanClearLanguage() = runBlocking {
        var savedLanguage = "unchanged"

        saveSeriesMetadata(
            meta = SeriesMetadataDto(title = "Updated", language = ""),
            fallbackErrorMessage = "Save failed",
            forbiddenErrorMessage = "Komga admin permission is required",
            update = { body -> savedLanguage = body.language }
        )

        assertEquals("", savedLanguage)
    }

    @Test
    fun seriesLanguageSaveUsesFocusedPatchAndUpdatesLocalMetadata() = runBlocking {
        var savedLanguage = ""
        var savedLanguageLock = false

        val result = saveSeriesLanguageMetadata(
            current = SeriesMetadataDto(title = "Series", language = "ja"),
            language = "ko",
            languageLock = true,
            fallbackErrorMessage = "Save failed",
            forbiddenErrorMessage = "Komga admin permission is required",
            update = { body ->
                savedLanguage = body.language
                savedLanguageLock = body.languageLock
            }
        )

        assertEquals("ko", savedLanguage)
        assertTrue(savedLanguageLock)
        assertEquals(
            SeriesMetadataDto(title = "Series", language = "ko", languageLock = true),
            (result as MetadataSaveResult.Success).metadata
        )
    }

    @Test
    fun seriesLanguageSaveFailureKeepsCurrentMetadata() = runBlocking {
        val current = SeriesMetadataDto(title = "Series", language = "ja")

        val result = saveSeriesLanguageMetadata(
            current = current,
            language = "ko",
            languageLock = true,
            fallbackErrorMessage = "Save failed",
            forbiddenErrorMessage = "Komga admin permission is required",
            update = { throw IOException() }
        )

        assertTrue(result is MetadataSaveResult.Failure)
        assertEquals("Save failed", (result as MetadataSaveResult.Failure).message)
        assertEquals("ja", current.language)
    }
}
