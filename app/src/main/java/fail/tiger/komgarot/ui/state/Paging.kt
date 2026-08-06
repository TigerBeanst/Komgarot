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
    private var generation = 0L

    fun reset() {
        generation++
        items.clear()
        page = 0
        hasMore = true
        loading = false
        error = null
        hasLoadedOnce = false
    }

    suspend fun loadMore(loadPage: suspend (Int) -> PagedDto<T>) {
        if (!hasMore || loading) return
        val requestGeneration = generation
        val requestPage = page
        loading = true
        error = null
        try {
            runCatching { loadPage(requestPage) }
                .onSuccess { result ->
                    if (requestGeneration == generation) {
                        items.addAllUniqueBy(result.content, keySelector)
                        hasMore = requestPage < result.totalPages - 1
                        page = requestPage + 1
                    }
                }
                .onFailure { throwable ->
                    if (requestGeneration == generation) {
                        error = throwable.message?.takeIf { it.isNotBlank() } ?: fallbackErrorMessage
                    }
                }
        } finally {
            if (requestGeneration == generation) {
                loading = false
                hasLoadedOnce = true
            }
        }
    }

    suspend fun refresh(loadPage: suspend (Int) -> PagedDto<T>) {
        if (loading) return
        val requestGeneration = generation
        loading = true
        error = null
        try {
            runCatching { loadPage(0) }
                .onSuccess { result ->
                    if (requestGeneration == generation) {
                        items.clear()
                        items.addAllUniqueBy(result.content, keySelector)
                        hasMore = result.totalPages > 1
                        page = 1
                    }
                }
                .onFailure { throwable ->
                    if (requestGeneration == generation) {
                        error = throwable.message?.takeIf { it.isNotBlank() } ?: fallbackErrorMessage
                    }
                }
        } finally {
            if (requestGeneration == generation) {
                loading = false
                hasLoadedOnce = true
            }
        }
    }
}
