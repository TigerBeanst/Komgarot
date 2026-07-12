package fail.tiger.komgarot.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fail.tiger.komgarot.data.remote.dto.PagedDto

internal fun <T, K> MutableList<T>.addAllUniqueBy(items: List<T>, keySelector: (T) -> K) {
    val existingKeys = mapTo(mutableSetOf(), keySelector)
    addAll(items.filter { existingKeys.add(keySelector(it)) })
}

internal class PagedListState<T, K>(
    private val keySelector: (T) -> K,
    private val fallbackErrorMessage: String
) {
    val items = mutableStateListOf<T>()
    var hasMore by mutableStateOf(true)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasLoadedOnce by mutableStateOf(false)
        private set
    private var page = 0

    fun reset() {
        items.clear()
        page = 0
        hasMore = true
        loading = false
        error = null
        hasLoadedOnce = false
    }

    suspend fun loadMore(loadPage: suspend (Int) -> PagedDto<T>) {
        if (!hasMore || loading) return
        loading = true
        error = null
        try {
            runCatching { loadPage(page) }
                .onSuccess { result ->
                    items.addAllUniqueBy(result.content, keySelector)
                    hasMore = page < result.totalPages - 1
                    page++
                }
                .onFailure { throwable ->
                    error = throwable.message?.takeIf { it.isNotBlank() } ?: fallbackErrorMessage
                }
        } finally {
            loading = false
            hasLoadedOnce = true
        }
    }

    suspend fun refresh(loadPage: suspend (Int) -> PagedDto<T>) {
        if (loading) return
        loading = true
        error = null
        try {
            runCatching { loadPage(0) }
                .onSuccess { result ->
                    items.clear()
                    items.addAllUniqueBy(result.content, keySelector)
                    hasMore = result.totalPages > 1
                    page = 1
                }
                .onFailure { throwable ->
                    error = throwable.message?.takeIf { it.isNotBlank() } ?: fallbackErrorMessage
                }
        } finally {
            loading = false
            hasLoadedOnce = true
        }
    }
}
