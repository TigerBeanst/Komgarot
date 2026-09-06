package fail.tiger.komgarot.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fail.tiger.komgarot.data.remote.dto.BookDto
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class CachedBookEntry(
    val bookId: String = "",
    val title: String = "",
    val seriesTitle: String = "",
    val pageCount: Int = 0,
    val cachedPages: Int = 0,
    val isOneShot: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

class BookDownloadIndex(cacheDir: File) {
    private val file = File(cacheDir, "book_download_index.json")
    private val temporaryFile = File(cacheDir, "book_download_index.json.tmp")
    private val corruptFile = File(cacheDir, "book_download_index.json.corrupt")

    @Synchronized
    fun list(): List<CachedBookEntry> =
        readEntries().sortedByDescending { it.updatedAt }

    @Synchronized
    fun record(entry: CachedBookEntry) {
        if (entry.bookId.isBlank()) return
        val next = readEntries()
            .filterNot { it.bookId == entry.bookId }
            .plus(entry)
        writeEntries(next)
    }

    @Synchronized
    fun remove(bookId: String) {
        if (bookId.isBlank()) return
        writeEntries(readEntries().filterNot { it.bookId == bookId })
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
        if (temporaryFile.exists()) temporaryFile.delete()
        if (corruptFile.exists()) corruptFile.delete()
    }

    private fun readEntries(): List<CachedBookEntry> {
        if (!file.isFile) return emptyList()
        return try {
            indexGson.fromJson<List<CachedBookEntry>>(file.readText(), cachedBookEntryListType).orEmpty()
        } catch (_: Exception) {
            preserveCorruptIndex()
            emptyList()
        }
    }

    private fun writeEntries(entries: List<CachedBookEntry>) {
        file.parentFile?.mkdirs()
        val encoded = indexGson.toJson(entries).toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            moveIntoPlace(temporaryFile, file)
        } finally {
            temporaryFile.delete()
        }
    }

    private fun preserveCorruptIndex() {
        if (!file.isFile) return
        runCatching {
            Files.move(
                file.toPath(),
                corruptFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun moveIntoPlace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}

private val indexGson = Gson()
private val cachedBookEntryListType = TypeToken.getParameterized(
    List::class.java,
    CachedBookEntry::class.java
).type

internal fun cachedBookEntry(
    book: BookDto,
    progress: BookDownloadProgress,
    updatedAt: Long = System.currentTimeMillis()
): CachedBookEntry {
    val pageCount = progress.totalPages.takeIf { it > 0 } ?: book.media.pagesCount
    return CachedBookEntry(
        bookId = book.id,
        title = book.metadata.title.ifBlank { book.name },
        seriesTitle = book.seriesTitle.orEmpty(),
        pageCount = pageCount,
        cachedPages = progress.completedPages,
        isOneShot = book.oneshot,
        updatedAt = updatedAt
    )
}
