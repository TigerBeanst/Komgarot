package fail.tiger.komgarot.data.remote.dto

data class BookSearchDto(
    val condition: Map<String, @JvmSuppressWildcards Any?>? = null,
    val fullTextSearch: String? = null
)

data class SeriesSearchDto(
    val condition: Map<String, @JvmSuppressWildcards Any?>? = null,
    val fullTextSearch: String? = null
)

object SearchCondition {
    fun isValue(value: Any): Map<String, Any> = mapOf("operator" to "IS", "value" to value)
    fun isNot(value: Any): Map<String, Any> = mapOf("operator" to "IS_NOT", "value" to value)
    fun contains(value: String): Map<String, Any> = mapOf("operator" to "CONTAINS", "value" to value)
    fun beginsWith(value: String): Map<String, Any> = mapOf("operator" to "BEGINS_WITH", "value" to value)
    fun isTrue(): Map<String, Any> = mapOf("operator" to "IS_TRUE")
    fun isFalse(): Map<String, Any> = mapOf("operator" to "IS_FALSE")
    fun after(dateTime: String): Map<String, Any> = mapOf("operator" to "AFTER", "dateTime" to dateTime)
    fun before(dateTime: String): Map<String, Any> = mapOf("operator" to "BEFORE", "dateTime" to dateTime)
    fun inTheLast(duration: String): Map<String, Any> = mapOf("operator" to "IS_IN_THE_LAST", "duration" to duration)

    fun series(field: String, condition: Map<String, Any>): Map<String, Any> =
        mapOf("operator" to "SERIES", field to condition)

    fun book(field: String, condition: Map<String, Any>): Map<String, Any> =
        mapOf("operator" to "BOOK", field to condition)

    fun allOfSeries(conditions: List<Map<String, Any>>): Map<String, Any>? =
        combine("SERIES", "allOf", conditions)

    fun anyOfSeries(conditions: List<Map<String, Any>>): Map<String, Any>? =
        combine("SERIES", "anyOf", conditions)

    fun allOfBook(conditions: List<Map<String, Any>>): Map<String, Any>? =
        combine("BOOK", "allOf", conditions)

    fun anyOfBook(conditions: List<Map<String, Any>>): Map<String, Any>? =
        combine("BOOK", "anyOf", conditions)

    private fun combine(operator: String, key: String, conditions: List<Map<String, Any>>): Map<String, Any>? {
        val present = conditions.filter { it.isNotEmpty() }
        return when (present.size) {
            0 -> null
            1 -> present.first()
            else -> mapOf("operator" to operator, key to present)
        }
    }
}

fun isCondition(value: Any): Map<String, Any> = SearchCondition.isValue(value)
