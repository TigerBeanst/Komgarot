package fail.tiger.komgarot.ui.cached

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var showClearAllFirstConfirmation by remember { mutableStateOf(false) }
    var showClearAllFinalConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cached_books)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearAllFirstConfirmation = true },
                        enabled = vm.books.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cached_books_clear_all))
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

    if (showClearAllFirstConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllFirstConfirmation = false },
            title = { Text(stringResource(R.string.cached_books_clear_all_title)) },
            text = { Text(stringResource(R.string.cached_books_clear_all_message_first)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllFirstConfirmation = false
                    showClearAllFinalConfirmation = true
                }) {
                    Text(stringResource(R.string.cached_books_clear_all_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllFirstConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showClearAllFinalConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllFinalConfirmation = false },
            title = { Text(stringResource(R.string.cached_books_clear_all_title_final)) },
            text = { Text(stringResource(R.string.cached_books_clear_all_message_final)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    showClearAllFinalConfirmation = false
                }) {
                    Text(stringResource(R.string.cached_books_clear_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllFinalConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
