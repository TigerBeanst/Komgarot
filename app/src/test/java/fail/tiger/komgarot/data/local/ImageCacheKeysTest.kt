package fail.tiger.komgarot.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCacheKeysTest {
    @Test
    fun thumbnailCacheKeysAreStableAcrossVersionBumps() {
        assertEquals("book-thumb:book-1", thumbnailCacheKey(ThumbnailCacheTarget.Book("book-1")))
        assertEquals("series-thumb:series-1", thumbnailCacheKey(ThumbnailCacheTarget.Series("series-1")))
        assertEquals("collection-thumb:collection-1", thumbnailCacheKey(ThumbnailCacheTarget.Collection("collection-1")))
        assertEquals("readlist-thumb:readlist-1", thumbnailCacheKey(ThumbnailCacheTarget.ReadList("readlist-1")))
    }
}
