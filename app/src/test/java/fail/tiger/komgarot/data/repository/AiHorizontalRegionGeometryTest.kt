package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.local.AiTranslationMode
import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHorizontalRegionGeometryTest {
    @Test
    fun horizontalSourceLinesProduceSeparatePageContextMasks() {
        val region = regionWithLines(
            AiTranslationRect(0.10f, 0.20f, 0.30f, 0.04f),
            AiTranslationRect(0.12f, 0.27f, 0.25f, 0.04f)
        )

        val masks = pageContextTextMaskRectsForAi(1000, 1000, listOf(region))

        assertEquals(2, masks.size)
        assertTrue(masks[0].bottom <= masks[1].top)
    }

    @Test
    fun legacyRegionStillUsesItsExistingSourceMask() {
        val region = AiTranslationLocalTextRegion(
            id = "legacy",
            rect = AiTranslationRect(0.2f, 0.3f, 0.1f, 0.1f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.8f,
            estimatedFontScale = 1f
        )

        val masks = pageContextTextMaskRectsForAi(1000, 1000, listOf(region))

        assertEquals(1, masks.size)
        assertTrue(masks.single().left < 200)
        assertTrue(masks.single().top < 300)
    }

    @Test
    fun translatedHorizontalBlockCarriesTargetLocaleAndLayoutVersion() {
        val context = AiTranslationLocalPageContext(
            pageIndex = 4,
            imageWidth = 1000,
            imageHeight = 1000,
            regions = listOf(regionWithLines(AiTranslationRect(0.1f, 0.2f, 0.2f, 0.04f)))
        )
        val translations = listOf(
            AiLocalRegionTranslation(
                localRegionId = "",
                sourceText = "hello",
                translatedLines = listOf("你好"),
                regionOrdinal = 0
            )
        )

        val page = buildTranslatedPageFromLocalContext(
            localContext = context,
            translations = translations,
            mode = AiTranslationMode.HIGH_ACCURACY,
            targetLocale = "zh-CN"
        )

        assertEquals("zh-CN", page!!.blocks.single().horizontalTargetLocale)
        assertEquals(1, page.blocks.single().horizontalLayoutVersion)
    }

    private fun regionWithLines(vararg lines: AiTranslationRect) =
        AiTranslationLocalTextRegion(
            id = "horizontal",
            rect = AiTranslationRect(0.1f, 0.2f, 0.3f, 0.11f),
            textDirection = AiTranslationTextDirection.HORIZONTAL,
            textColor = "#111111",
            backgroundColor = "#FFFFFF",
            confidence = 0.8f,
            estimatedFontScale = 1f,
            sourceLines = lines.toList(),
            horizontalLayoutVersion = 1
        )
}
