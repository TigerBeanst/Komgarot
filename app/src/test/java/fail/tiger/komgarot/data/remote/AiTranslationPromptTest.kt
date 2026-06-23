package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiTranslationRect
import fail.tiger.komgarot.data.local.AiTranslationTextDirection
import fail.tiger.komgarot.data.local.AiTranslationMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationPromptTest {
    private val clientSource = File("src/main/java/fail/tiger/komgarot/data/remote/AiTranslationClient.kt").readText()

    @Test
    fun systemPromptDescribesLocalRegionCropTranslationOnly() {
        val prompt = aiTranslationSystemPrompt()

        assertTrue(prompt.contains("page context images"))
        assertTrue(prompt.contains("text-region crop images"))
        assertTrue(prompt.contains("strict JSON"))
        assertTrue(prompt.contains("translatedLines"))
        assertTrue(prompt.contains("manga"))
        assertTrue(prompt.contains("local text regions"))
        assertTrue(prompt.contains("localRegionId"))
        assertTrue(prompt.contains("sourceText"))
        assertTrue(prompt.contains("translations"))
        assertTrue(prompt.contains("rect"))
        assertTrue(prompt.contains("coordinates"))
        assertTrue(prompt.contains("The app owns placement"))
        assertTrue(prompt.contains("Image ordering"))
        assertTrue(prompt.contains("Preserve Japanese corner quotes"))
        assertTrue(prompt.contains("Quote style is part of the translation contract"))
        assertTrue(prompt.contains("translatedLines must use the same outer quote marks"))
        assertTrue(prompt.contains("Standard curly quotes “ ” are used only when the source crop itself uses “ ”"))
        assertTrue(prompt.contains("Line breaks must preserve word and phrase cohesion"))
        assertTrue(prompt.contains("Punctuation in translatedLines follows visible source punctuation"))
        assertTrue(prompt.contains("Short fragments with no visible sentence-final mark should end bare"))
        assertTrue(prompt.contains("Punctuation attaches to the preceding word or phrase"))
        assertTrue(prompt.contains("merged text box or balloon crop"))
        assertTrue(prompt.contains("pure number"))
        assertTrue(prompt.contains("translatedLines as an empty array"))
        assertTrue(!prompt.contains("ocrText"))
        assertTrue(!prompt.contains("textColor"))
        assertTrue(!prompt.contains("maskColor"))
    }

    @Test
    fun userPromptContainsLocaleLanguageAndCustomInstructions() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-1",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = emptyList(),
            customInstructions = "保留敬语"
        )

        assertTrue(prompt.contains("book-1"))
        assertTrue(prompt.contains("zh-CN"))
        assertTrue(prompt.contains("简体中文"))
        assertTrue(prompt.contains("sourceMode: local_detection"))
        assertTrue(prompt.contains("保留敬语"))
    }

    @Test
    fun userPromptIncludesLocalTextRegionsWhenLocalDetectionRuns() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-1",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = listOf(
                AiTranslationLocalPageContext(
                    pageIndex = 4,
                    imageWidth = 1200,
                    imageHeight = 1800,
                    regions = listOf(
                        AiTranslationLocalTextRegion(
                            id = "p4-r1",
                            rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.08f, height = 0.24f),
                            textDirection = AiTranslationTextDirection.VERTICAL,
                            textColor = "#111111",
                            backgroundColor = "#FFFFFF",
                            confidence = 0.74f,
                            estimatedFontScale = 1.05f
                        )
                    )
                )
            ),
            customInstructions = ""
        )

        assertTrue(prompt.contains("localTextRegions"))
        assertTrue(prompt.contains("\"pageIndex\":4"))
        assertTrue(prompt.contains("\"id\":\"p4-r1\""))
        assertTrue(prompt.contains("\"imageRef\":\"text-region:p4-r1\""))
        assertTrue(prompt.contains("\"textDirection\":\"vertical\""))
        assertTrue(prompt.contains("\"rect\":{\"x\":0.1"))
        assertTrue(prompt.contains("\"width\":0.08"))
        assertTrue(prompt.contains("\"layoutHints\""))
        assertTrue(prompt.contains("\"suggestedColumns\":1"))
        assertTrue(prompt.contains("\"maxCharsPerColumn\":7"))
        assertTrue(prompt.contains("\"estimatedFontPx\":57"))
        assertTrue(prompt.contains("Translate the supplied local text regions"))
        assertTrue(prompt.contains("Return localRegionId with corrected sourceText"))
        assertTrue(prompt.contains("Return localRegionId"))
        assertTrue(prompt.contains("The attached images are ordered"))
        assertTrue(prompt.contains("Read the full crop before translating"))
        assertTrue(prompt.contains("keeping connected words together"))
        assertTrue(prompt.contains("source with no visible sentence-final mark returns no added period/full stop"))
        assertTrue(prompt.contains("no translatedLines entry should contain only punctuation"))
        assertTrue(prompt.contains("source 「」/『』 stays as translated 「」/『』"))
        assertTrue(prompt.contains("pure number"))
        assertTrue(prompt.contains("Return translations only"))
        assertTrue(!prompt.contains("ocrText"))
        assertTrue(!prompt.contains("textColor"))
        assertTrue(!prompt.contains("maskColor"))
        assertTrue(!prompt.contains("fontScale"))
    }

    @Test
    fun verticalLayoutHintsAreComputedLocallyFromRegionSizeAndEstimatedFont() {
        val hints = aiTranslationRegionLayoutHints(
            pageImageWidth = 1200,
            pageImageHeight = 1800,
            region = AiTranslationLocalTextRegion(
                id = "p4-r1",
                rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.08f, height = 0.24f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.74f,
                estimatedFontScale = 1.05f
            )
        )

        assertEquals(1, hints.suggestedColumns)
        assertEquals(7, hints.maxCharsPerColumn)
        assertEquals(57, hints.estimatedFontPx)
    }

    @Test
    fun localPromptJsonUsesStableReadableKeysInReleaseBuilds() {
        val source = File("src/main/java/fail/tiger/komgarot/data/remote/AiTranslationPrompt.kt").readText()
        val prompt = aiTranslationUserPrompt(
            bookId = "book-1",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = listOf(
                AiTranslationLocalPageContext(
                    pageIndex = 4,
                    imageWidth = 1200,
                    imageHeight = 1800,
                    regions = listOf(
                        AiTranslationLocalTextRegion(
                            id = "p4-r1",
                            rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.08f, height = 0.24f),
                            textDirection = AiTranslationTextDirection.VERTICAL,
                            textColor = "#111111",
                            backgroundColor = "#FFFFFF",
                            confidence = 0.74f,
                            estimatedFontScale = 1.05f
                        )
                    )
                )
            ),
            customInstructions = ""
        )

        assertTrue(source.contains("JsonObject().apply"))
        assertTrue(source.contains("addProperty(\"imageRef\""))
        assertTrue(!source.contains("private data class PromptLocalRegion"))
        assertTrue(prompt.contains("\"pageIndex\":4"))
        assertTrue(prompt.contains("\"regions\""))
        assertTrue(prompt.contains("\"imageRef\":\"text-region:p4-r1\""))
        assertTrue(!prompt.contains("\"ocrText\""))
        assertTrue(!prompt.contains("\"a\":4"))
        assertTrue(!prompt.contains("\"b\":"))
        assertTrue(!prompt.contains("\"c\":\"sample\""))
    }

    @Test
    fun localPromptKeepsDetectedRegionsWithoutLocalTextHints() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-1",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = listOf(
                AiTranslationLocalPageContext(
                    pageIndex = 0,
                    imageWidth = 1000,
                    imageHeight = 1600,
                    regions = listOf(
                        AiTranslationLocalTextRegion(
                            id = "p0-r1",
                            rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.08f, height = 0.24f),
                            textDirection = AiTranslationTextDirection.VERTICAL,
                            textColor = "#111111",
                            backgroundColor = "#FFFFFF",
                            confidence = 0.74f,
                            estimatedFontScale = 1.05f
                        ),
                        AiTranslationLocalTextRegion(
                            id = "p0-r2",
                            rect = AiTranslationRect(x = 0.2f, y = 0.3f, width = 0.08f, height = 0.24f),
                            textDirection = AiTranslationTextDirection.VERTICAL,
                            textColor = "#111111",
                            backgroundColor = "#FFFFFF",
                            confidence = 0.74f,
                            estimatedFontScale = 1.05f
                        ),
                        AiTranslationLocalTextRegion(
                            id = "p0-r3",
                            rect = AiTranslationRect(x = 0.3f, y = 0.4f, width = 0.08f, height = 0.24f),
                            textDirection = AiTranslationTextDirection.VERTICAL,
                            textColor = "#111111",
                            backgroundColor = "#FFFFFF",
                            confidence = 0.74f,
                            estimatedFontScale = 1.05f
                        )
                    )
                )
            ),
            customInstructions = ""
        )

        assertTrue(prompt.contains("p0-r1"))
        assertTrue(prompt.contains("p0-r2"))
        assertTrue(prompt.contains("p0-r3"))
        assertTrue(!prompt.contains("\"ocrText\""))
    }

    @Test
    fun localPromptKeepsRegionsForCropReading() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-1",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = listOf(
                AiTranslationLocalPageContext(
                    pageIndex = 0,
                    imageWidth = 1000,
                    imageHeight = 1600,
                    regions = listOf(
                        AiTranslationLocalTextRegion(
                            id = "p0-r1",
                            rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.08f, height = 0.24f),
                            textDirection = AiTranslationTextDirection.VERTICAL,
                            textColor = "#111111",
                            backgroundColor = "#FFFFFF",
                            confidence = 0.74f,
                            estimatedFontScale = 1.05f
                        )
                    )
                )
            ),
            customInstructions = ""
        )

        assertTrue(prompt.contains("localTextRegions"))
        assertTrue(prompt.contains("p0-r1"))
        assertTrue(prompt.contains("\"imageRef\":\"text-region:p0-r1\""))
        assertTrue(!prompt.contains("\"ocrText\""))
    }

    @Test
    fun imageUrlExtraQueryIsAppendedSafely() {
        val appended = appendImageUrlExtraQuery(
            url = "https://komga.test/api/v1/books/b/pages/1?convert=png",
            extraQuery = "token=abc"
        )

        assertTrue(appended.endsWith("?convert=png&token=abc"))
    }

    @Test
    fun base64PayloadUsesDataUrlImageContent() {
        val request = AiTranslationImageInput(
            pageIndex = 0,
            transport = AiImageTransport.BASE64,
            mimeType = "image/jpeg",
            base64 = "abc",
            imageUrl = "",
            localRegionId = "p0-r1"
        )

        assertTrue(request.toOpenAiImageUrl().contains("data:image/jpeg;base64,abc"))
    }

    @Test
    fun chatRequestJsonUsesOpenAiVisionMessageShape() {
        val json = buildAiTranslationChatRequestJson(
            model = "vision-model",
            systemPrompt = "system",
            userPrompt = "user",
            images = listOf(
                AiTranslationImageInput(
                    pageIndex = 0,
                    transport = AiImageTransport.IMAGE_URL,
                    mimeType = "image/png",
                    base64 = "",
                    imageUrl = "https://komga.test/page.png?token=abc"
                )
            )
        )

        assertTrue(json.contains("\"model\":\"vision-model\""))
        assertTrue(json.contains("\"type\":\"image_url\""))
        assertTrue(json.contains("https://komga.test/page.png?token=abc"))
        assertTrue(json.contains("\"response_format\":{\"type\":\"json_object\"}"))
    }

    @Test
    fun chatRequestBuilderKeepsImagePayloadAsUrlReferenceUntilFinalJsonWrite() {
        assertTrue(clientSource.contains("val imageUrl = image.toOpenAiImageUrl()"))
        assertTrue(clientSource.contains("addProperty(\"url\", imageUrl)"))
    }

    @Test
    fun chatRequestJsonAddsStableImageMetadataTextBeforeEachImage() {
        val json = buildAiTranslationChatRequestJson(
            model = "vision-model",
            systemPrompt = "system",
            userPrompt = "user",
            images = listOf(
                AiTranslationImageInput(
                    pageIndex = 0,
                    transport = AiImageTransport.BASE64,
                    mimeType = "image/jpeg",
                    base64 = "page",
                    imageUrl = ""
                ),
                AiTranslationImageInput(
                    pageIndex = 0,
                    transport = AiImageTransport.BASE64,
                    mimeType = "image/jpeg",
                    base64 = "crop",
                    imageUrl = "",
                    localRegionId = "p0-r1"
                )
            )
        )

        assertTrue(json.contains("imageRole=page_context; pageIndex=0"))
        assertTrue(json.contains("imageRole=text_region; pageIndex=0; localRegionId=p0-r1"))
        assertTrue(json.indexOf("imageRole=page_context") < json.indexOf("data:image/jpeg;base64,page"))
        assertTrue(json.indexOf("localRegionId=p0-r1") < json.indexOf("data:image/jpeg;base64,crop"))
    }

    @Test
    fun chatResponseContentIsExtractedAndCodeFenceIsRemoved() {
        val response = """
            {"choices":[{"message":{"content":"```json\n{\"bookId\":\"book-1\",\"pages\":[]}\n```"}}]}
        """.trimIndent()

        assertEquals(
            "{\"bookId\":\"book-1\",\"pages\":[]}",
            extractAiTranslationJsonContent(response)
        )
    }

    @Test
    fun httpFailureSummaryIncludesStatusWhenBodyIsEmpty() {
        val summary = buildAiHttpFailureSummary(
            statusCode = 502,
            statusMessage = "Bad Gateway",
            responseBody = ""
        )

        assertEquals("HTTP 502 Bad Gateway", summary)
    }

    @Test
    fun aiClientAppliesConfiguredTimeoutToHttpCalls() {
        assertTrue(clientSource.contains("timeoutSeconds: Int"))
        assertTrue(clientSource.contains("aiResponseTimeoutSeconds(timeoutSeconds)"))
        assertTrue(!clientSource.contains("callTimeout("))
        assertTrue(clientSource.contains("connectTimeout(AI_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)"))
        assertTrue(clientSource.contains("readTimeout(responseTimeout.toLong(), TimeUnit.SECONDS)"))
        assertTrue(clientSource.contains("writeTimeout(writeTimeout.toLong(), TimeUnit.SECONDS)"))
        assertTrue(clientSource.contains("AI request timed out after"))
    }

    @Test
    fun aiClientCancelsHttpCallWhenCoroutineStops() {
        assertTrue(clientSource.contains("suspend fun translate("))
        assertTrue(clientSource.contains("suspendCancellableCoroutine"))
        assertTrue(clientSource.contains("continuation.invokeOnCancellation"))
        assertTrue(clientSource.contains("call.cancel()"))
    }

    @Test
    fun aiClientAllowsDisablingResponseTimeout() {
        assertEquals(0, aiResponseTimeoutSeconds(0))
        assertEquals(30, aiResponseTimeoutSeconds(30))
    }
}
