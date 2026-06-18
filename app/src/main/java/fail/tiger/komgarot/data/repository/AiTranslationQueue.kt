package fail.tiger.komgarot.data.repository

data class AiTranslationQueuedPage(
    val bookId: String,
    val pageIndex: Int
)

data class AiTranslationBatch(
    val pages: List<AiTranslationQueuedPage>
)

data class AiTranslationQueueState(
    val paused: Boolean = false,
    private val highPriorityPages: List<AiTranslationQueuedPage> = emptyList(),
    private val normalPages: List<AiTranslationQueuedPage> = emptyList()
) {
    fun enqueueBook(bookId: String, pages: List<Int>, pagesPerRequest: Int): AiTranslationQueueState {
        val queued = pages.map { AiTranslationQueuedPage(bookId, it) }
        return copy(normalPages = normalPages + queued)
    }

    fun enqueueSinglePage(bookId: String, pageIndex: Int, highPriority: Boolean): AiTranslationQueueState {
        val page = AiTranslationQueuedPage(bookId, pageIndex)
        return if (highPriority) {
            copy(highPriorityPages = listOf(page) + highPriorityPages.filterNot { it == page })
        } else {
            copy(normalPages = normalPages + page)
        }
    }

    fun nextBatch(maxPages: Int): AiTranslationBatch {
        if (paused) return AiTranslationBatch(emptyList())
        val pages = (highPriorityPages + normalPages)
            .distinct()
            .take(maxPages.coerceAtLeast(1))
        return AiTranslationBatch(pages)
    }
}
