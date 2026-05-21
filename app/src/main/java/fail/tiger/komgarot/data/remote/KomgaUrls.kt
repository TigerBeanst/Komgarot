package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.ThumbnailVersion

object KomgaUrls {
    fun bookThumbnail(serverUrl: String, bookId: String): String =
        "${serverUrl.trimEnd('/')}/api/v1/books/$bookId/thumbnail?v=${ThumbnailVersion.get(bookId)}"

    fun seriesThumbnail(serverUrl: String, seriesId: String): String =
        "${serverUrl.trimEnd('/')}/api/v1/series/$seriesId/thumbnail?v=${ThumbnailVersion.get(seriesId)}"

    fun collectionThumbnail(serverUrl: String, collectionId: String): String =
        "${serverUrl.trimEnd('/')}/api/v1/collections/$collectionId/thumbnail?v=${ThumbnailVersion.get(collectionId)}"

    fun readListThumbnail(serverUrl: String, readListId: String): String =
        "${serverUrl.trimEnd('/')}/api/v1/readlists/$readListId/thumbnail?v=${ThumbnailVersion.get(readListId)}"

    fun page(serverUrl: String, bookId: String, pageNumber: Int): String =
        "${serverUrl.trimEnd('/')}/api/v1/books/$bookId/pages/$pageNumber"
}
