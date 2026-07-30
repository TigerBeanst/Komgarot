package fail.tiger.komgarot.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationBlockDeduplicationTest {
    @Test
    fun overlappingEquivalentTranslationsRenderOnceAndKeepBothMasks() {
        val first = translatedBlock(
            id = "p2-r1",
            source = "source text",
            translation = listOf("翻译内容"),
            rect = AiTranslationRect(0.10f, 0.20f, 0.24f, 0.10f)
        )
        val second = translatedBlock(
            id = "p2-r2",
            source = "source  text",
            translation = listOf("翻译", "内容"),
            rect = AiTranslationRect(0.16f, 0.22f, 0.24f, 0.10f)
        )

        val result = listOf(first, second).suppressDuplicateRenderedTranslations()

        assertEquals(2, result.size)
        assertEquals(1, result.count { it.translatedLines.any(String::isNotBlank) })
        assertEquals(2, result.first().sourceColumns.size)
        assertEquals(0.30f, result.first().rect.width, 0.0001f)
        assertEquals(AiTranslationRegionStatus.DONE, result.last().regionStatus)
    }

    @Test
    fun equivalentTranslationsAtSeparateLocationsRemainVisible() {
        val first = translatedBlock(
            id = "p2-r1",
            source = "first",
            translation = listOf("相同内容"),
            rect = AiTranslationRect(0.08f, 0.10f, 0.18f, 0.08f)
        )
        val second = translatedBlock(
            id = "p2-r2",
            source = "second",
            translation = listOf("相同内容"),
            rect = AiTranslationRect(0.68f, 0.72f, 0.18f, 0.08f)
        )

        val result = listOf(first, second).suppressDuplicateRenderedTranslations()

        assertEquals(2, result.count { it.translatedLines.any(String::isNotBlank) })
    }

    @Test
    fun overlappingDifferentTranslationsRemainVisible() {
        val first = translatedBlock(
            id = "p2-r1",
            source = "first source",
            translation = listOf("第一段"),
            rect = AiTranslationRect(0.10f, 0.20f, 0.24f, 0.10f)
        )
        val second = translatedBlock(
            id = "p2-r2",
            source = "second source",
            translation = listOf("第二段"),
            rect = AiTranslationRect(0.16f, 0.22f, 0.24f, 0.10f)
        )

        val result = listOf(first, second).suppressDuplicateRenderedTranslations()

        assertEquals(2, result.count { it.translatedLines.any(String::isNotBlank) })
    }

    private fun translatedBlock(
        id: String,
        source: String,
        translation: List<String>,
        rect: AiTranslationRect
    ) = AiTranslationBlock(
        localRegionId = id,
        regionStatus = AiTranslationRegionStatus.DONE,
        kind = AiTranslationBlockKind.DIALOGUE,
        sourceText = source,
        translatedLines = translation,
        rect = rect,
        translationRect = rect,
        textDirection = AiTranslationTextDirection.HORIZONTAL
    )
}
