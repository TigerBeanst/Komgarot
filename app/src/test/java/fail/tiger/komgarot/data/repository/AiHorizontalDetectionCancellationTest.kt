package fail.tiger.komgarot.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiHorizontalDetectionCancellationTest {
    @Test
    fun cancelledJobStopsDetectionWithoutThreadInterruption() {
        val job = Job()
        job.cancel()
        assertFalse(Thread.currentThread().isInterrupted)
        try {
            ensureHorizontalDetectionActive(job)
            fail("Cancelled job must stop detection")
        } catch (_: CancellationException) {
            assertFalse(Thread.currentThread().isInterrupted)
        }
    }

    @Test
    fun activeJobAllowsDetection() {
        val job = Job()
        try {
            ensureHorizontalDetectionActive(job)
            assertTrue(job.isActive)
        } finally {
            job.cancel()
        }
    }
}
