package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.ThumbnailVersion

object KomgaUrls {
    fun bookThumbnail(serverUrl: String, bookId: String): String =
        bookThumbnail(serverUrl, bookId, ThumbnailVersion.get(bookId))

    fun bookThumbnail(serverUrl: String, bookId: String, version: Int): String =
        "${serverUrl.trimEnd('/')}/api/v1/books/$bookId/thumbnail?v=$version"

    fun seriesThumbnail(serverUrl: String, seriesId: String): String =
        seriesThumbnail(serverUrl, seriesId, ThumbnailVersion.get(seriesId))

    fun seriesThumbnail(serverUrl: String, seriesId: String, version: Int): String =
        "${serverUrl.trimEnd('/')}/api/v1/series/$seriesId/thumbnail?v=$version"

    fun collectionThumbnail(serverUrl: String, collectionId: String): String =
        collectionThumbnail(serverUrl, collectionId, ThumbnailVersion.get(collectionId))

    fun collectionThumbnail(serverUrl: String, collectionId: String, version: Int): String =
        "${serverUrl.trimEnd('/')}/api/v1/collections/$collectionId/thumbnail?v=$version"

    fun readListThumbnail(serverUrl: String, readListId: String): String =
        readListThumbnail(serverUrl, readListId, ThumbnailVersion.get(readListId))

    fun readListThumbnail(serverUrl: String, readListId: String, version: Int): String =
        "${serverUrl.trimEnd('/')}/api/v1/readlists/$readListId/thumbnail?v=$version"

    fun page(serverUrl: String, bookId: String, pageNumber: Int): String =
        "${serverUrl.trimEnd('/')}/api/v1/books/$bookId/pages/$pageNumber"
}
