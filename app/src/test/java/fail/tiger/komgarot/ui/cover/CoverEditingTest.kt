package fail.tiger.komgarot.ui.cover

import org.junit.Assert.assertEquals
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
}
