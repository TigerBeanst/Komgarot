package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.local.AiImageTransport
import fail.tiger.komgarot.data.local.AiGlossaryEntry
import fail.tiger.komgarot.data.local.AiSeriesSourceLanguageState
import fail.tiger.komgarot.data.local.AiSourceLanguageOrigin
import fail.tiger.komgarot.data.local.AiSourceReadingDirection
import fail.tiger.komgarot.data.local.AiSourceTextProfile
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
        assertTrue(prompt.contains("current text-region crop images"))
        assertTrue(prompt.contains("Return only:"))
        assertTrue(prompt.contains("translatedLines"))
        assertTrue(prompt.contains("manga"))
        assertTrue(prompt.contains("sourceText"))
        assertTrue(prompt.contains("translations"))
        assertTrue(prompt.contains("\"kind\":\"dialogue\"|\"narration\"|\"sign\"|\"SFX\""))
        assertTrue(prompt.contains("The crop image is the only readable source text"))
        assertTrue(prompt.contains("Page context images provide scene, speaker, tone, setting, and action context"))
        assertTrue(prompt.contains("Use page context for meaning"))
        assertTrue(prompt.contains("Coordinates, placement, mask, color, and font data are owned by the app"))
        assertTrue(prompt.contains("Return regionOrdinal for every crop"))
        assertTrue(prompt.contains("Return one translation item for every crop"))
        assertTrue(prompt.contains("Classify the crop as dialogue, narration, sign, or SFX"))
        assertTrue(prompt.contains("If the crop is dominated by a sound effect"))
        assertTrue(prompt.contains("SFX translatedLines must stay very short"))
        assertTrue(prompt.contains("detectedSourceLanguage"))
        assertTrue(prompt.contains("Line breaks must preserve word and phrase cohesion"))
        assertTrue(prompt.contains("Punctuation in translatedLines follows visible source punctuation"))
        assertTrue(prompt.contains("A single visible source ellipsis character … maps to one translated ellipsis"))
        assertTrue(prompt.contains("Short fragments with no visible sentence-final mark should end bare"))
        assertTrue(prompt.contains("Punctuation attaches to the preceding word or phrase"))
        assertTrue(prompt.contains("translatedLines must be written in targetLanguageName"))
        assertTrue(prompt.contains("IETF BCP 47"))
        assertTrue(prompt.contains("pure number"))
        assertTrue(prompt.contains("translatedLines: []"))
        assertTrue(!prompt.contains("Return strict JSON only."))
        assertTrue(!prompt.contains("Return JSON with pageIndex and translations"))
        assertTrue(!prompt.contains("Exactly one translation object"))
        assertTrue(!prompt.contains("Each translation object includes"))
        assertTrue(!prompt.contains("For each page, return pageIndex and translations"))
        assertTrue(!prompt.contains("Each local region has normalized rect coordinates"))
        assertTrue(!prompt.contains("Image ordering:"))
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
        assertTrue(!prompt.contains("sourceTextProfile: auto"))
        assertTrue(prompt.contains("保留敬语"))
        assertTrue(prompt.trimEnd().endsWith("保留敬语"))
    }

    @Test
    fun userPromptIncludesGlossaryForConsistentTerminology() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-glossary",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = emptyList(),
            customInstructions = "",
            glossary = listOf(
                AiGlossaryEntry(source = "主人公", target = "主角", note = "角色称呼")
            )
        )

        assertTrue(prompt.contains("Glossary:"))
        assertTrue(prompt.contains("主人公 => 主角 (角色称呼)"))
        assertTrue(prompt.contains("Keep glossary source terms and targets consistent"))
    }

    @Test
    fun soundEffectSkippingReturnsEmptyTranslations() {
        val systemPrompt = aiTranslationSystemPrompt(skipSoundEffects = true)
        val userPrompt = aiTranslationUserPrompt(
            bookId = "book-sfx",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = emptyList(),
            customInstructions = "",
            skipSoundEffects = true
        )

        assertTrue(systemPrompt.contains("Short calls, interjections, greetings, and spoken fragments remain dialogue"))
        assertTrue(systemPrompt.contains("status: \"skipped_sfx\""))
        assertTrue(userPrompt.contains("skipSoundEffects: true"))
        assertTrue(userPrompt.contains("one skipped_sfx response item for each crop"))
    }

    @Test
    fun userPromptIncludesKoreanHorizontalSourceProfileInstructions() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-korean",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = listOf(
                AiTranslationLocalPageContext(
                    pageIndex = 2,
                    imageWidth = 1080,
                    imageHeight = 1920,
                    regions = listOf(
                        AiTranslationLocalTextRegion(
                            id = "p2-r1",
                            rect = AiTranslationRect(x = 0.12f, y = 0.20f, width = 0.48f, height = 0.10f),
                            textDirection = AiTranslationTextDirection.HORIZONTAL,
                            textColor = "#111111",
                            backgroundColor = "#FFFFFF",
                            confidence = 0.86f,
                            estimatedFontScale = 1.0f
                        )
                    )
                )
            ),
            customInstructions = "",
            sourceTextProfile = AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON
        )

        assertTrue(prompt.contains("sourceTextProfile: korean_horizontal_webtoon"))
        assertTrue(prompt.contains("Korean horizontal webtoon"))
        assertTrue(prompt.contains("left-to-right"))
        assertTrue(prompt.contains("top-to-bottom"))
        assertTrue(prompt.contains("preserve Korean word spaces"))
        assertTrue(prompt.contains("Hangul text"))
        assertTrue(prompt.contains("mixed Hanja"))
    }

    @Test
    fun koreanSourceLanguageActivatesKoreanProfileWhenSettingsProfileIsAuto() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-korean-source",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = emptyList(),
            customInstructions = "",
            sourceTextProfile = AiSourceTextProfile.AUTO,
            sourceLanguage = AiSeriesSourceLanguageState(normalizedCode = "ko")
        )

        assertTrue(prompt.contains("sourceLanguage: ko"))
        assertTrue(prompt.contains("sourceTextProfile: korean_horizontal_webtoon"))
        assertTrue(prompt.contains("Hangul syllable blocks"))
    }

    @Test
    fun userPromptUsesKnownKomgaSourceLanguageWithoutDetectionRequest() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-japanese",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = emptyList(),
            customInstructions = "",
            sourceLanguage = AiSeriesSourceLanguageState(
                seriesId = "series-1",
                normalizedCode = "ja-JP",
                rawKomgaValue = "ja-JP",
                origin = AiSourceLanguageOrigin.KOMGA,
                readingDirection = AiSourceReadingDirection.RIGHT_TO_LEFT
            )
        )

        assertTrue(prompt.contains("sourceLanguageOrigin: komga"))
        assertTrue(prompt.contains("sourceLanguage: ja-JP"))
        assertTrue(prompt.contains("sourceLanguageName: Japanese"))
        assertTrue(prompt.contains("detectSourceLanguage: false"))
        assertTrue(prompt.contains("Japanese source"))
        assertTrue(prompt.contains("Preserve Japanese corner quotes"))
        assertTrue(prompt.contains("sourceReadingDirection: right_to_left"))
        assertTrue(prompt.contains("sourceTextProfile: japanese_manga"))
        assertTrue(prompt.contains("vertical columns from right to left"))
    }

    @Test
    fun userPromptRequestsLanguageDetectionForPendingSeries() {
        val prompt = aiTranslationUserPrompt(
            bookId = "book-auto",
            targetLocale = "zh-CN",
            targetLanguageName = "简体中文",
            translationMode = AiTranslationMode.LOCAL_DETECTION,
            localPageContexts = emptyList(),
            customInstructions = "",
            sourceLanguage = AiSeriesSourceLanguageState(seriesId = "series-1")
        )

        assertTrue(prompt.contains("sourceLanguageOrigin: ai_pending"))
        assertTrue(prompt.contains("sourceLanguage: auto"))
        assertTrue(prompt.contains("detectSourceLanguage: true"))
        assertTrue(prompt.contains("IETF BCP 47 tag"))
    }

    @Test
    fun userPromptProvidesLanguageSpecificRulesForHorizontalComics() {
        val instructions = mapOf(
            "ko" to "Korean source",
            "en" to "English source",
            "zh-Hans" to "Chinese source",
            "th" to "Thai source"
        )

        instructions.forEach { (language, expectedInstruction) ->
            val prompt = aiTranslationUserPrompt(
                bookId = "book-horizontal",
                targetLocale = "zh-CN",
                targetLanguageName = "简体中文",
                translationMode = AiTranslationMode.LOCAL_DETECTION,
                localPageContexts = emptyList(),
                customInstructions = "",
                sourceLanguage = AiSeriesSourceLanguageState(
                    seriesId = "series-1",
                    normalizedCode = language,
                    origin = AiSourceLanguageOrigin.KOMGA
                )
            )

            assertTrue(prompt.contains(expectedInstruction))
            val expectedProfile = if (language == "ko") {
                "sourceTextProfile: korean_horizontal_webtoon"
            } else {
                "sourceTextProfile: horizontal_comic"
            }
            assertTrue(prompt.contains(expectedProfile))
            if (language == "en") {
                assertTrue(prompt.contains("whole English words"))
            }
        }
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

        assertTrue(prompt.contains("currentRegion"))
        assertTrue(prompt.contains("\"pageIndex\":4"))
        assertTrue(!prompt.contains("\"id\":\"p4-r1\""))
        assertTrue(!prompt.contains("\"imageRef\""))
        assertTrue(!prompt.contains("p4-r1"))
        assertTrue(prompt.contains("\"textDirection\":\"vertical\""))
        assertTrue(!prompt.contains("sourceTextProfile: auto"))
        assertTrue(!prompt.contains("\"sourceTextProfile\":\"auto\""))
        assertTrue(!prompt.contains("\"rect\""))
        assertTrue(!prompt.contains("\"imageWidth\""))
        assertTrue(!prompt.contains("\"imageHeight\""))
        assertTrue(!prompt.contains("\"sourceColumns\""))
        assertTrue(prompt.contains("\"layoutHints\""))
        assertTrue(prompt.contains("\"suggestedColumns\":1"))
        assertTrue(prompt.contains("\"maxCharsPerColumn\":7"))
        assertTrue(!prompt.contains("\"estimatedFontPx\""))
        assertTrue(prompt.contains("Translate the current crop."))
        assertTrue(!prompt.contains("Return localRegionId"))
        assertTrue(prompt.contains("Image 1: context"))
        assertTrue(prompt.contains("Images 2-2: crop images in regionOrdinal order"))
        assertTrue(!prompt.contains("Attached images: page context image first, current crop image last"))
        assertTrue(prompt.contains("Use page context for scene, speaker, tone, and action"))
        assertTrue(prompt.contains("Read sourceText from the crop image"))
        assertTrue(!prompt.contains("For a pure number region"))
        assertTrue(!prompt.contains("source with no visible sentence-final mark"))
        assertTrue(!prompt.contains("source 「」/『』"))
        assertTrue(!prompt.contains("Return no localRegionId or id"))
        assertTrue(!prompt.contains("The attached images are ordered: page context image first for each page"))
        assertTrue(!prompt.contains("Return placement, styling"))
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
    fun verticalLayoutHintsUseTallestSourceColumnHeight() {
        val hints = aiTranslationRegionLayoutHints(
            pageImageWidth = 1000,
            pageImageHeight = 2000,
            region = AiTranslationLocalTextRegion(
                id = "p4-r1",
                rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.08f, height = 0.22f),
                textDirection = AiTranslationTextDirection.VERTICAL,
                textColor = "#111111",
                backgroundColor = "#FFFFFF",
                confidence = 0.74f,
                estimatedFontScale = 1.0f,
                sourceColumns = listOf(
                    AiTranslationRect(x = 0.16f, y = 0.20f, width = 0.03f, height = 0.20f),
                    AiTranslationRect(x = 0.12f, y = 0.28f, width = 0.03f, height = 0.10f)
                )
            )
        )

        assertEquals(2, hints.suggestedColumns)
        assertEquals(12, hints.maxCharsPerColumn)
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
        assertTrue(!source.contains("addProperty(\"imageRef\""))
        assertTrue(!source.contains("private data class PromptLocalRegion"))
        assertTrue(prompt.contains("\"pageIndex\":4"))
        assertTrue(prompt.contains("currentRegion:"))
        assertTrue(prompt.contains("\"regions\""))
        assertTrue(prompt.contains("\"regionOrdinal\":0"))
        assertTrue(!prompt.contains("\"imageWidth\""))
        assertTrue(!prompt.contains("\"imageHeight\""))
        assertTrue(!prompt.contains("\"rect\""))
        assertTrue(!prompt.contains("\"sourceColumns\""))
        assertTrue(!prompt.contains("\"id\":\"p4-r1\""))
        assertTrue(!prompt.contains("\"imageRef\""))
        assertTrue(!prompt.contains("\"ocrText\""))
        assertTrue(!prompt.contains("\"a\":4"))
        assertTrue(!prompt.contains("\"b\":"))
        assertTrue(!prompt.contains("\"c\":\"sample\""))
    }

    @Test
    fun localPromptKeepsOnlyCompactCurrentRegionWithoutLocalTextHints() {
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

        assertTrue(!prompt.contains("p0-r1"))
        assertTrue(!prompt.contains("p0-r2"))
        assertTrue(!prompt.contains("p0-r3"))
        assertTrue(prompt.contains("currentRegion:"))
        assertTrue(prompt.contains("\"regions\""))
        assertTrue(prompt.contains("\"regionOrdinal\":0"))
        assertTrue(!prompt.contains("\"imageWidth\""))
        assertTrue(!prompt.contains("\"imageHeight\""))
        assertTrue(!prompt.contains("\"rect\""))
        assertTrue(!prompt.contains("\"sourceColumns\""))
        assertTrue(!prompt.contains("\"ocrText\""))
    }

    @Test
    fun localPromptKeepsCompactRegionForCropReading() {
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

        assertTrue(prompt.contains("currentRegion"))
        assertTrue(!prompt.contains("p0-r1"))
        assertTrue(!prompt.contains("\"imageRef\""))
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
    fun imageUrlPayloadCanFallbackToBase64AfterRemoteFetchTimeout() {
        val request = AiTranslationImageInput(
            pageIndex = 12,
            transport = AiImageTransport.IMAGE_URL,
            mimeType = "image/jpeg",
            base64 = "",
            imageUrl = "https://s3.test/page-12-p12-r2.jpg?X-Amz-Signature=abc",
            localRegionId = "p12-r2",
            fallbackBase64 = "fallback-bytes"
        )

        val fallback = request.asBase64Fallback()

        assertEquals("", request.base64)
        assertEquals(AiImageTransport.BASE64, fallback.transport)
        assertEquals("fallback-bytes", fallback.base64)
        assertEquals("", fallback.imageUrl)
        assertEquals("p12-r2", fallback.localRegionId)
        assertTrue(fallback.toOpenAiImageUrl().contains("data:image/jpeg;base64,fallback-bytes"))
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
    fun chatRequestIncludesReasoningEffortOnlyWhenConfigured() {
        val configured = buildAiTranslationChatRequestJson(
            model = "vision-model",
            systemPrompt = "system",
            userPrompt = "user",
            images = emptyList(),
            reasoningEffort = " high "
        )
        val empty = buildAiTranslationChatRequestJson(
            model = "vision-model",
            systemPrompt = "system",
            userPrompt = "user",
            images = emptyList(),
            reasoningEffort = " "
        )

        assertTrue(configured.contains("\"reasoning_effort\":\"high\""))
        assertTrue(!empty.contains("\"reasoning_effort\""))
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
                    localRegionId = "p0-r1",
                    regionOrdinal = 0
                ),
                AiTranslationImageInput(
                    pageIndex = 0,
                    transport = AiImageTransport.BASE64,
                    mimeType = "image/jpeg",
                    base64 = "crop2",
                    imageUrl = "",
                    localRegionId = "p0-r2",
                    regionOrdinal = 1
                )
            )
        )

        assertTrue(json.contains("Image 1: context; page=0"))
        assertTrue(json.contains("Image 2: crop; page=0"))
        assertTrue(json.contains("Image 2: crop; page=0; regionOrdinal=0"))
        assertTrue(json.contains("Image 3: crop; page=0; regionOrdinal=1"))
        assertTrue(!json.contains("imageRole=page_context"))
        assertTrue(!json.contains("sceneContextOnly=true"))
        assertTrue(!json.contains("imageRole=text_region"))
        assertTrue(!json.contains("textSourceOnlyForCurrentRegion=true"))
        assertTrue(!json.contains("localRegionId=p0-r1"))
        assertTrue(!json.contains("textSourceOnlyForLocalRegionId=p0-r1"))
        assertTrue(json.indexOf("Image 1: context") < json.indexOf("data:image/jpeg;base64,page"))
        assertTrue(json.indexOf("Image 2: crop") < json.indexOf("data:image/jpeg;base64,crop"))
        assertTrue(json.indexOf("Image 3: crop") < json.indexOf("data:image/jpeg;base64,crop2"))
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
