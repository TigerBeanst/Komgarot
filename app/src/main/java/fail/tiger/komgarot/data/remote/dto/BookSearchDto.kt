package fail.tiger.komgarot.data.remote.dto

data class BookSearchDto(
    val condition: BookCondition? = null
)

data class BookCondition(
    val operator: String = "BOOK",
    val seriesId: SeriesIdCondition
)

data class SeriesIdCondition(
    val operator: String = "IS",
    val value: String
)
