package fail.tiger.komgarot.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class KomgaUrlsTest {
    @Test
    fun pageThumbnailUsesKomgaThumbnailEndpoint() {
        assertEquals(
            "https://komga.test/api/v1/books/book-1/pages/7/thumbnail",
            KomgaUrls.pageThumbnail("https://komga.test/", "book-1", 7)
        )
    }
}
