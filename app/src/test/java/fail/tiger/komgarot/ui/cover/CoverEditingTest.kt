package fail.tiger.komgarot.ui.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverEditingTest {
    @Test
    fun fullCropUsesEntireImage() {
        assertEquals(CoverCropRect(0, 0, 101, 200), coverCropRect(101, 200, CoverCrop.Full))
    }

    @Test
    fun leftCropUsesLeftHalfForOddWidth() {
        assertEquals(CoverCropRect(0, 0, 50, 200), coverCropRect(101, 200, CoverCrop.LeftHalf))
    }

    @Test
    fun rightCropKeepsRemainderForOddWidth() {
        assertEquals(CoverCropRect(50, 0, 51, 200), coverCropRect(101, 200, CoverCrop.RightHalf))
    }

    @Test
    fun narrowImageStillProducesPositiveCropWidth() {
        assertEquals(CoverCropRect(0, 0, 1, 200), coverCropRect(1, 200, CoverCrop.LeftHalf))
        assertEquals(CoverCropRect(0, 0, 1, 200), coverCropRect(1, 200, CoverCrop.RightHalf))
    }

    @Test
    fun largeCoverUploadIsScaledToMaxEdge() {
        assertEquals(CoverSize(1440, 2048), scaledCoverSize(2880, 4096))
    }

    @Test
    fun smallCoverUploadKeepsOriginalSize() {
        assertEquals(CoverSize(900, 1200), scaledCoverSize(900, 1200))
    }

    @Test
    fun encodedCoverStopsAtFirstCandidateWithinByteLimit() {
        val calls = mutableListOf<Pair<CoverSize, Int>>()

        val result = encodeCoverWithinByteLimit(
            initialSize = CoverSize(2048, 1200),
            maxBytes = 1_000,
            qualitySteps = intArrayOf(90, 80),
            edgeSteps = intArrayOf(2048),
            encoder = { size, quality ->
                calls += size to quality
                ByteArray(if (quality == 90) 1_001 else 900)
            }
        )

        assertEquals(CoverSize(2048, 1200), result.size)
        assertEquals(80, result.quality)
        assertEquals(900, result.bytes.size)
        assertEquals(listOf(CoverSize(2048, 1200) to 90, CoverSize(2048, 1200) to 80), calls)
    }

    @Test
    fun encodedCoverReducesEdgeWhenQualityIsStillTooLarge() {
        val result = encodeCoverWithinByteLimit(
            initialSize = CoverSize(2048, 1200),
            maxBytes = 1_000,
            qualitySteps = intArrayOf(90),
            edgeSteps = intArrayOf(2048, 1024),
            encoder = { size, _ -> ByteArray(if (size.width == 2048) 1_001 else 900) }
        )

        assertEquals(CoverSize(1024, 600), result.size)
        assertEquals(900, result.bytes.size)
    }

    @Test
    fun encodedCoverReturnsLastCandidateWhenEveryAttemptExceedsLimit() {
        val result = encodeCoverWithinByteLimit(
            initialSize = CoverSize(2048, 1200),
            maxBytes = 1_000,
            qualitySteps = intArrayOf(90),
            edgeSteps = intArrayOf(2048, 1024),
            encoder = { _, _ -> ByteArray(1_001) }
        )

        assertTrue(result.bytes.size > 1_000)
        assertEquals(CoverSize(1024, 600), result.size)
    }
}
