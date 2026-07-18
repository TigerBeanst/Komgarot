package fail.tiger.komgarot.data.repository

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AiTranslationPurgeTest {
    @Test
    fun scanCollectsOnlyBooksConfirmedMissingByServer() = runBlocking {
        val result = scanAiTranslationPurgeCandidates(listOf("present", "missing", "gone")) { bookId ->
            when (bookId) {
                "present" -> Result.success(Unit)
                "missing" -> Result.failure<Unit>(httpFailure(404))
                else -> Result.failure<Unit>(httpFailure(410))
            }
        }

        assertEquals(
            AiTranslationPurgeScanResult.Ready(
                checkedCount = 3,
                candidateBookIds = listOf("missing", "gone")
            ),
            result
        )
    }

    @Test
    fun scanAbortsBeforeDeletionCandidateListCanBeUsedAfterTransientFailure() = runBlocking {
        val result = scanAiTranslationPurgeCandidates(listOf("missing", "unstable", "unchecked")) { bookId ->
            when (bookId) {
                "missing" -> Result.failure<Unit>(httpFailure(404))
                "unstable" -> Result.failure<Unit>(IOException("offline"))
                else -> Result.success(Unit)
            }
        }

        assertTrue(result is AiTranslationPurgeScanResult.Aborted)
        val aborted = result as AiTranslationPurgeScanResult.Aborted
        assertEquals(2, aborted.checkedCount)
        assertEquals(AiTranslationPurgeAbortReason.NETWORK, aborted.reason)
    }

    @Test
    fun scanClassifiesAuthenticationRateLimitAndServerFailures() = runBlocking {
        val expected = mapOf(
            401 to AiTranslationPurgeAbortReason.AUTHENTICATION,
            403 to AiTranslationPurgeAbortReason.AUTHENTICATION,
            429 to AiTranslationPurgeAbortReason.RATE_LIMIT,
            500 to AiTranslationPurgeAbortReason.SERVER
        )

        expected.forEach { (status, reason) ->
            val result = scanAiTranslationPurgeCandidates(listOf("book")) {
                Result.failure<Unit>(httpFailure(status))
            }
            assertEquals(reason, (result as AiTranslationPurgeScanResult.Aborted).reason)
        }
    }

    private fun httpFailure(status: Int): HttpException =
        HttpException(Response.error<Unit>(status, "".toResponseBody()))
}
