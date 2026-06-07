package fail.tiger.komgarot.ui.cached

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.data.local.BookDownloadIndex
import fail.tiger.komgarot.data.local.CachedBookEntry
import java.io.File

class CachedBooksViewModel(private val source: CachedBooksSource) : ViewModel() {
    var books by mutableStateOf<List<CachedBookEntry>>(emptyList())
        private set

    fun load() {
        books = source.load()
    }

    class Factory(cacheDir: File) : ViewModelProvider.Factory by viewModelFactory({
        initializer { CachedBooksViewModel(CachedBooksSource(BookDownloadIndex(cacheDir))) }
    })
}
