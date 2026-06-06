package fail.tiger.komgarot.ui.navigation

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Library : Screen("library")
    object Browse : Screen("browse")
    object Collections : Screen("collections")
    object CollectionDetail : Screen("collections/{collectionId}") {
        fun go(id: String) = "collections/${encodeArg(id)}"
    }
    object ReadLists : Screen("readlists")
    object ReadListDetail : Screen("readlists/{readListId}") {
        fun go(id: String) = "readlists/${encodeArg(id)}"
    }
    object Admin : Screen("admin")
    object Me : Screen("me")
    object CachedBooks : Screen("cached-books")
    object Settings : Screen("settings")
    object Series : Screen("series/{libraryId}?search={search}&tag={tag}") {
        fun go(id: String?, search: String? = null, tag: String? = null): String {
            val base = "series/${encodeArg(id ?: "all")}"
            val params = listOfNotNull(
                search?.let { "search=${encodeArg(it)}" },
                tag?.let { "tag=${encodeArg(it)}" }
            )
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }
    }
    object Books : Screen("books/{seriesId}") {
        fun go(id: String) = "books/${encodeArg(id)}"
    }
    object BookDetail : Screen("bookdetail/{bookId}/{bookName}/{seriesName}/{pageCount}/{isOneShot}") {
        fun go(id: String, name: String, seriesName: String, pageCount: Int, isOneShot: Boolean = false) =
            "bookdetail/${encodeArg(id)}/${encodeArg(name)}/${encodeArg(seriesName)}/$pageCount/$isOneShot"
    }
    object Reader : Screen("reader/{bookId}/{page}?trackProgress={trackProgress}") {
        fun go(id: String, page: Int = 1, trackProgress: Boolean = true) =
            "reader/${encodeArg(id)}/$page?trackProgress=$trackProgress"
    }
    object Metadata : Screen("metadata/{type}/{id}?coverUri={coverUri}&coverFocus={coverFocus}") {
        fun go(type: String, id: String) = "metadata/${encodeArg(type)}/${encodeArg(id)}"

        fun goBookCover(bookId: String, coverUri: String): String =
            goCover(type = "book", id = bookId, coverUri = coverUri)

        fun goSeriesCover(seriesId: String, coverUri: String): String =
            goCover(type = "series", id = seriesId, coverUri = coverUri)

        private fun goCover(type: String, id: String, coverUri: String): String =
            "metadata/${encodeArg(type)}/${encodeArg(id)}?coverUri=${encodeArg(coverUri)}&coverFocus=true"
    }

    companion object {
        fun decodeArg(value: String): String =
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }
}

private fun encodeArg(value: String): String =
    buildString {
        value.encodeToByteArray().forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val char = unsigned.toChar()
            if (char.isRouteSafe()) {
                append(char)
            } else {
                append('%')
                append(unsigned.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

private fun Char.isRouteSafe(): Boolean =
    this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9' ||
        this == '-' ||
        this == '.' ||
        this == '_' ||
        this == '~'
