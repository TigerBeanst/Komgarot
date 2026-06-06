package fail.tiger.komgarot.ui.cached

import fail.tiger.komgarot.data.local.BookDownloadIndex
import fail.tiger.komgarot.data.local.CachedBookEntry

class CachedBooksSource(private val index: BookDownloadIndex) {
    fun load(): List<CachedBookEntry> = index.list()
}
