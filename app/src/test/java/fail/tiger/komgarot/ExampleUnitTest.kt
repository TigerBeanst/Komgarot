package fail.tiger.komgarot

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun thumbnailVersionBumpIncrementsVersionAndIgnoresBlankIds() {
        val id = "thumbnail-test-${System.nanoTime()}"
        val initial = ThumbnailVersion.get(id)

        ThumbnailVersion.bump(id)
        ThumbnailVersion.bump("")

        assertEquals(initial + 1, ThumbnailVersion.get(id))
        assertEquals(0, ThumbnailVersion.get(""))
    }
}
