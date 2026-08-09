package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.AiTranslationErrorCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationRegionImageBuildTest {
    @Test
    fun imageUploadFailureBecomesRetryableRegionBuildFailure() = runBlocking {
        val result = buildAiTranslationRegionImagesSafely(
            pageIndex = 44,
            expectedRegionCount = 1
        ) {
            error("S3 image upload failed: HTTP 502 Bad Gateway")
        }

        assertTrue(result is AiTranslationRegionImageBuildResult.Failure)
        val failure = result as AiTranslationRegionImageBuildResult.Failure
        assertEquals(AiTranslationErrorCategory.NETWORK_OR_API, failure.category)
        assertTrue(failure.summary.contains("page=44"))
        assertTrue(failure.summary.contains("HTTP 502"))
    }
}
