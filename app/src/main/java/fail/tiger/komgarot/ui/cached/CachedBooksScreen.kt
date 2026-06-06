package fail.tiger.komgarot.ui.cached

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.CachedBookEntry
import fail.tiger.komgarot.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachedBooksScreen(
    vm: CachedBooksViewModel,
    onBookClick: (CachedBookEntry) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(vm) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cached_books)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (vm.books.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.empty_cached_books),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize()
            ) {
                items(vm.books, key = { it.bookId }) { book ->
                    CachedBookRow(book = book, onClick = { onBookClick(book) })
                }
            }
        }
    }
}

@Composable
private fun CachedBookRow(book: CachedBookEntry, onClick: () -> Unit) {
    val seriesTitle = book.seriesTitle.ifBlank { stringResource(R.string.unknown) }
    ListItem(
        headlineContent = { Text(book.title.ifBlank { book.bookId }) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.cached_book_progress,
                    seriesTitle,
                    book.cachedPages,
                    book.pageCount
                )
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
