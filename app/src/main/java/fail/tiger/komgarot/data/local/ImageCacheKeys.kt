package fail.tiger.komgarot.data.local

sealed interface ThumbnailCacheTarget {
    val id: String

    data class Book(override val id: String) : ThumbnailCacheTarget
    data class Series(override val id: String) : ThumbnailCacheTarget
    data class Collection(override val id: String) : ThumbnailCacheTarget
    data class ReadList(override val id: String) : ThumbnailCacheTarget
}

fun thumbnailCacheKey(target: ThumbnailCacheTarget): String =
    when (target) {
        is ThumbnailCacheTarget.Book -> "book-thumb:${target.id}"
        is ThumbnailCacheTarget.Series -> "series-thumb:${target.id}"
        is ThumbnailCacheTarget.Collection -> "collection-thumb:${target.id}"
        is ThumbnailCacheTarget.ReadList -> "readlist-thumb:${target.id}"
    }
