package fail.tiger.komgarot.data.repository

import java.io.IOException
import retrofit2.HttpException

enum class AiTranslationPurgeAbortReason {
    AUTHENTICATION,
    RATE_LIMIT,
    SERVER,
    NETWORK,
    UNKNOWN
}

sealed interface AiTranslationPurgeScanResult {
    val checkedCount: Int

    data class Ready(
        override val checkedCount: Int,
        val candidateBookIds: List<String>
    ) : AiTranslationPurgeScanResult

    data class Aborted(
        override val checkedCount: Int,
        val reason: AiTranslationPurgeAbortReason,
        val detail: String
    ) : AiTranslationPurgeScanResult
}

sealed interface AiTranslationPurgeResult {
    data class Completed(
        val checkedCount: Int,
        val removedCount: Int
    ) : AiTranslationPurgeResult

    data class Aborted(
        val checkedCount: Int,
        val reason: AiTranslationPurgeAbortReason,
        val detail: String
    ) : AiTranslationPurgeResult
}

internal suspend fun scanAiTranslationPurgeCandidates(
    bookIds: List<String>,
    lookup: suspend (String) -> Result<*>
): AiTranslationPurgeScanResult {
    val candidates = mutableListOf<String>()
    bookIds.distinct().forEachIndexed { index, bookId ->
        val result = lookup(bookId)
        val failure = result.exceptionOrNull()
        when {
            result.isSuccess -> Unit
            failure is HttpException && failure.code() in setOf(404, 410) -> candidates += bookId
            else -> return AiTranslationPurgeScanResult.Aborted(
                checkedCount = index + 1,
                reason = failure.toAiTranslationPurgeAbortReason(),
                detail = failure?.message.orEmpty()
            )
        }
    }
    return AiTranslationPurgeScanResult.Ready(
        checkedCount = bookIds.distinct().size,
        candidateBookIds = candidates
    )
}

private fun Throwable?.toAiTranslationPurgeAbortReason(): AiTranslationPurgeAbortReason = when (this) {
    is HttpException -> when (code()) {
        401, 403 -> AiTranslationPurgeAbortReason.AUTHENTICATION
        429 -> AiTranslationPurgeAbortReason.RATE_LIMIT
        in 500..599 -> AiTranslationPurgeAbortReason.SERVER
        else -> AiTranslationPurgeAbortReason.UNKNOWN
    }
    is IOException -> AiTranslationPurgeAbortReason.NETWORK
    else -> AiTranslationPurgeAbortReason.UNKNOWN
}
