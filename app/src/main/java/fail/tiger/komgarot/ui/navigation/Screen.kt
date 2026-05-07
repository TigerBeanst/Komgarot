package fail.tiger.komgarot.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Library : Screen("library")
    object Series : Screen("series/{libraryId}?search={search}") {
        fun go(id: String?, search: String? = null) = if (search != null) {
            "series/${id ?: "all"}?search=${java.net.URLEncoder.encode(search, "UTF-8")}"
        } else {
            "series/${id ?: "all"}"
        }
    }
    object Books : Screen("books/{seriesId}") {
        fun go(id: String) = "books/$id"
    }
    object BookDetail : Screen("bookdetail/{bookId}/{bookName}/{seriesName}/{pageCount}/{isOneShot}") {
        fun go(id: String, name: String, seriesName: String, pageCount: Int, isOneShot: Boolean = false) =
            "bookdetail/$id/${java.net.URLEncoder.encode(name, "UTF-8")}/${java.net.URLEncoder.encode(seriesName, "UTF-8")}/$pageCount/$isOneShot"
    }
    object Reader : Screen("reader/{bookId}/{page}?trackProgress={trackProgress}") {
        fun go(id: String, page: Int = 1, trackProgress: Boolean = true) = "reader/$id/$page?trackProgress=$trackProgress"
    }
    object Metadata : Screen("metadata/{type}/{id}") {
        fun go(type: String, id: String) = "metadata/$type/$id"
    }
    object Settings : Screen("settings")
}
