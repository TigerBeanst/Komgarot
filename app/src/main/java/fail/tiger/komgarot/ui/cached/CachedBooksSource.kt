package fail.tiger.komgarot.ui.cached

import fail.tiger.komgarot.data.local.BookDownloadIndex
import fail.tiger.komgarot.data.local.CachedBookEntry
import fail.tiger.komgarot.data.local.ReaderPageCache
import java.io.File

class CachedBooksSource(
    private val index: BookDownloadIndex,
    private val cacheDir: File
) {
    fun load(): List<CachedBookEntry> = index.list()

    fun clearAll() {
        ReaderPageCache.clear(cacheDir)
        index.clear()
    }
}
