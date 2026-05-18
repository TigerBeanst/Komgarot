package fail.tiger.komgarot.data.remote.dto

data class BookSearchDto(
    val condition: Map<String, @JvmSuppressWildcards Any?>? = null,
    val fullTextSearch: String? = null
)

data class SeriesSearchDto(
    val condition: Map<String, @JvmSuppressWildcards Any?>? = null,
    val fullTextSearch: String? = null
)

fun isCondition(value: Any): Map<String, Any> = mapOf("operator" to "IS", "value" to value)
