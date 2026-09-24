package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.*
import fail.tiger.komgarot.data.remote.*
import org.junit.Assert.*
import org.junit.Test

class AiHorizontalRegionResumeTest {
    private val region = AiTranslationLocalTextRegion(
        id = "p0-r1", rect = AiTranslationRect(0.1f, 0.2f, 0.3f, 0.1f),
        textDirection = AiTranslationTextDirection.HORIZONTAL, textColor = "#111111",
        backgroundColor = "#FFFFFF", confidence = 0.9f, estimatedFontScale = 1f,
        horizontalLayoutVersion = 1
    )
    private val context = AiTranslationLocalPageContext(0, 1000, 1000, listOf(region))
    private val completed = localDetectionPlaceholderPage(context, AiTranslationMode.LOCAL_DETECTION, "zh-CN")
        .let { page -> page.copy(blocks = page.blocks.map {
            it.copy(regionStatus = AiTranslationRegionStatus.DONE, translatedLines = listOf("saved"))
        }) }

    @Test fun matchingHorizontalRegionKeepsCompletedTranslation() {
        assertEquals(listOf("saved"), resume(completed).translatedLines)
        assertEquals(AiTranslationRegionStatus.DONE, resume(completed).regionStatus)
    }

    @Test fun layoutUpgradeRequiresTranslationAgain() {
        assertPending(completed.copy(blocks = completed.blocks.map { it.copy(horizontalLayoutVersion = 0) }))
    }

    @Test fun reusedIdWithChangedGeometryRequiresTranslationAgain() {
        assertPending(completed.copy(blocks = completed.blocks.map {
            it.copy(rect = AiTranslationRect(0.6f, 0.7f, 0.2f, 0.1f))
        }))
        assertPending(completed.copy(blocks = completed.blocks.map { it.copy(sourceLines = emptyList()) }))
    }

    @Test fun changedImageOrTargetRequiresTranslationAgain() {
        assertPending(completed.copy(imageWidth = 2000))
        assertPending(completed.copy(blocks = completed.blocks.map { it.copy(horizontalTargetLocale = "en") }))
    }

    @Test fun legacyResumeKeepsExistingBehavior() {
        val legacyContext = context.copy(regions = listOf(region.copy(horizontalLayoutVersion = 0)))
        val legacyPage = completed.copy(blocks = completed.blocks.map { it.copy(horizontalLayoutVersion = 0) })
        val result = mergeLocalDetectionPageForRegionResume(legacyContext, legacyPage, AiTranslationMode.LOCAL_DETECTION)
        assertEquals(listOf("saved"), result.blocks.single().translatedLines)
    }

    private fun resume(page: AiTranslatedPage) = mergeLocalDetectionPageForRegionResume(
        context, page, AiTranslationMode.LOCAL_DETECTION, "zh-CN"
    ).blocks.single()

    private fun assertPending(page: AiTranslatedPage) {
        val block = resume(page)
        assertEquals(AiTranslationRegionStatus.PENDING, block.regionStatus)
        assertTrue(block.translatedLines.isEmpty())
    }
}
