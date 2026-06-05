package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.remote.dto.PageDto

private val directImageMediaTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
    "image/jxl",
    "image/heif",
    "image/avif"
)

fun readerPageUrl(serverUrl: String, bookId: String, page: PageDto): String {
    val url = KomgaUrls.page(serverUrl, bookId, page.number)
    return if (page.mediaType.lowercase() in directImageMediaTypes) url else "$url?convert=png"
}
