package fail.tiger.komgarot.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fail.tiger.komgarot.data.remote.dto.BookDto
import java.io.File

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

    fun list(): List<CachedBookEntry> =
        readEntries().sortedByDescending { it.updatedAt }

    fun record(entry: CachedBookEntry) {
        if (entry.bookId.isBlank()) return
        val next = readEntries()
            .filterNot { it.bookId == entry.bookId }
            .plus(entry)
        writeEntries(next)
    }

    fun remove(bookId: String) {
        if (bookId.isBlank()) return
        writeEntries(readEntries().filterNot { it.bookId == bookId })
    }

    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun readEntries(): List<CachedBookEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            indexGson.fromJson<List<CachedBookEntry>>(file.readText(), cachedBookEntryListType)
        }.getOrDefault(emptyList())
    }

    private fun writeEntries(entries: List<CachedBookEntry>) {
        file.parentFile?.mkdirs()
        file.writeText(indexGson.toJson(entries))
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
