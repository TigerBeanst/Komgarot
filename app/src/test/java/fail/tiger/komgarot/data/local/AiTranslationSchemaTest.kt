package fail.tiger.komgarot.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationSchemaTest {
    @Test
    fun validBookPassesValidation() {
        val book = sampleBook()

        val result = validateAiTranslatedBook(book)

        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun invalidBlockReportsPreciseErrors() {
        val book = sampleBook(
            block = sampleBlock().copy(
                rect = AiTranslationRect(x = -0.1f, y = 0.1f, width = 1.2f, height = 0.2f),
                textColor = "black",
                maskAlpha = 1.0f
            )
        )

        val result = validateAiTranslatedBook(book)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("rect") })
        assertTrue(result.errors.any { it.contains("textColor") })
        assertTrue(result.errors.any { it.contains("maskAlpha") })
    }

    @Test
    fun alphaAndGeometryAreSanitizedForRendering() {
        val block = sampleBlock().copy(
            rect = AiTranslationRect(x = 0.96f, y = -0.2f, width = 0.8f, height = 0.01f),
            translationRect = AiTranslationRect(x = 0.94f, y = 0.9f, width = 0.4f, height = 0.3f),
            maskAlpha = 0.2f,
            rotationDegrees = 45f,
            cornerRadius = 0.5f,
            fontScale = 1.8f,
            textDirection = AiTranslationTextDirection.VERTICAL
        )

        val render = block.renderSafe()

        assertEquals(AiTranslationTextDirection.VERTICAL, render.textDirection)
        assertTrue(render.rect.x + render.rect.width <= 1.0001f)
        assertTrue(render.rect.y >= 0f)
        assertTrue(render.rect.height >= 0.004f)
        assertTrue(render.translationRect.x + render.translationRect.width <= 1.0001f)
        assertTrue(render.translationRect.y + render.translationRect.height <= 1.0001f)
        assertEquals(0.78f, render.maskAlpha)
        assertEquals(0f, render.rotationDegrees)
        assertEquals(0.12f, render.cornerRadius)
        assertEquals(1.4f, render.fontScale)
    }

    @Test
    fun renderSafeKeepsValidRectCoordinatesUnshifted() {
        val block = sampleBlock().copy(
            rect = AiTranslationRect(x = 0.10f, y = 0.20f, width = 0.18f, height = 0.22f),
            translationRect = AiTranslationRect(x = 0.11f, y = 0.21f, width = 0.16f, height = 0.20f)
        )

        val render = block.renderSafe()

        assertEquals(block.rect, render.rect)
        assertEquals(block.translationRect, render.translationRect)
    }

    @Test
    fun renderSafeKeepsSmallOcrTextBoxesSmall() {
        val block = sampleBlock().copy(
            rect = AiTranslationRect(x = 0.30f, y = 0.40f, width = 0.008f, height = 0.012f),
            translationRect = AiTranslationRect(x = 0.30f, y = 0.40f, width = 0.008f, height = 0.012f)
        )

        val render = block.renderSafe()

        assertEquals(0.008f, render.rect.width, 0.0001f)
        assertEquals(0.012f, render.rect.height, 0.0001f)
        assertEquals(0.008f, render.translationRect.width, 0.0001f)
        assertEquals(0.012f, render.translationRect.height, 0.0001f)
    }

    private fun sampleBook(block: AiTranslationBlock = sampleBlock()) = AiTranslatedBook(
        bookId = "book-1",
        seriesId = "series-1",
        title = "Book",
        seriesTitle = "Series",
        pageCount = 1,
        fileFingerprint = AiBookFileFingerprint(mediaType = "image/jpeg", sizeBytes = 123),
        translation = AiBookTranslationMetadata(
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            provider = "openai-compatible",
            model = "vision-model"
        ),
        glossary = listOf(AiGlossaryEntry(source = "Sample term", target = "示例术语", note = "备注")),
        pages = listOf(
            AiTranslatedPage(
                pageIndex = 0,
                status = AiTranslationPageStatus.DONE,
                retryCount = 0,
                updatedAt = 100,
                imageWidth = 1600,
                imageHeight = 2400,
                blocks = listOf(block),
                errorSummary = ""
            )
        )
    )

    private fun sampleBlock() = AiTranslationBlock(
        kind = AiTranslationBlockKind.DIALOGUE,
        sourceText = "Hello",
        translatedLines = listOf("你好"),
        rect = AiTranslationRect(x = 0.1f, y = 0.1f, width = 0.2f, height = 0.1f),
        translationRect = AiTranslationRect(x = 0.11f, y = 0.12f, width = 0.18f, height = 0.09f),
        textColor = "#111111",
        maskColor = "#FFFFFF",
        maskAlpha = 0.72f,
        cornerRadius = 0.04f,
        rotationDegrees = 0f,
        fontScale = 1.0f,
        confidence = 0.92f,
        textDirection = AiTranslationTextDirection.HORIZONTAL
    )
}
