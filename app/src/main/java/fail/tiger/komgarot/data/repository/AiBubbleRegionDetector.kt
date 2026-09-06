package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationPoint

data class AiBubbleRegion(
    val rect: AiTranslationRect,
    val safeTextRect: AiTranslationRect = rect,
    val outline: List<AiTranslationPoint> = emptyList(),
    val backgroundColor: String = "#FFFFFF",
    val solidFill: Boolean = false
)

interface AiBubbleRegionDetector : AutoCloseable {
    fun detect(source: IntArray, width: Int, height: Int): List<AiBubbleRegion>
    fun cacheVersion(): String
    override fun close() = Unit
}
