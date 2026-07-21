package fail.tiger.komgarot.data.local

import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion
import fail.tiger.komgarot.data.repository.AiTranslationQueueRunner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiTranslationStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun bookTranslationRoundTripsAsSingleJsonFile() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val book = sampleBook().copy(
            translation = sampleBook().translation.copy(
                mode = AiTranslationMode.LOCAL_DETECTION.storedValue,
                sourceLanguageCode = "ja",
                sourceLanguageOrigin = AiSourceLanguageOrigin.KOMGA.storedValue,
                sourceKomgaLanguage = "ja",
                sourceReadingDirection = AiSourceReadingDirection.RIGHT_TO_LEFT.storedValue,
                modePinned = true
            )
        )

        store.saveBookNow(book)

        assertTrue(store.bookFile("book-1").isFile)
        assertEquals(book.bookId, store.readBook("book-1")?.bookId)
        assertEquals(AiTranslationMode.LOCAL_DETECTION.storedValue, store.readBook("book-1")?.translation?.mode)
        assertEquals("ja", store.readBook("book-1")?.translation?.sourceLanguageCode)
        assertEquals(AiSourceLanguageOrigin.KOMGA.storedValue, store.readBook("book-1")?.translation?.sourceLanguageOrigin)
        assertEquals(AiSourceReadingDirection.RIGHT_TO_LEFT.storedValue, store.readBook("book-1")?.translation?.sourceReadingDirection)
        assertTrue(store.readBook("book-1")?.translation?.modePinned == true)
        assertEquals(AiTranslationPageStatus.DONE, store.readBook("book-1")?.pages?.single()?.status)
    }

    @Test
    fun seriesSourceLanguageInferenceRoundTripsIndependentlyFromBooks() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val state = AiSeriesSourceLanguageState(
            seriesId = "series-1",
            normalizedCode = "ko",
            origin = AiSourceLanguageOrigin.AI,
            readingDirection = AiSourceReadingDirection.LEFT_TO_RIGHT,
            evidence = listOf("ko", "en", "ko")
        )

        store.saveSeriesSourceLanguage(state)

        assertEquals(state, store.readSeriesSourceLanguage("series-1"))
        assertEquals(null, store.readSeriesSourceLanguage("series-2"))
    }

    @Test
    fun clearBookDeletesBookJson() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(sampleBook())

        store.clearBook("book-1")

        assertFalse(store.bookFile("book-1").exists())
        assertEquals(null, store.readBook("book-1"))
    }

    @Test
    fun localDetectionContextCacheRoundTripsByKeyAndIsClearedWithBook() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val context = AiTranslationLocalPageContext(
            pageIndex = 4,
            imageWidth = 1200,
            imageHeight = 1800,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p4-r1",
                    rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.3f, height = 0.4f),
                    textDirection = AiTranslationTextDirection.VERTICAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.91f,
                    estimatedFontScale = 1.05f
                )
            )
        )

        store.saveLocalPageContext("book-1", pageIndex = 4, cacheKey = "key-a", context = context)

        assertEquals(context, store.readLocalPageContext("book-1", pageIndex = 4, cacheKey = "key-a"))
        assertEquals(null, store.readLocalPageContext("book-1", pageIndex = 4, cacheKey = "key-b"))

        store.clearBook("book-1")

        assertEquals(null, store.readLocalPageContext("book-1", pageIndex = 4, cacheKey = "key-a"))
    }

    @Test
    fun regionCropCacheRoundTripsByRegionAndIsClearedWithBook() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val bytes = byteArrayOf(1, 2, 3, 4)

        store.saveRegionCrop("book-1", pageIndex = 2, regionId = "p2-r1", cacheKey = "crop-a", bytes = bytes)

        assertArrayEquals(bytes, store.readRegionCrop("book-1", pageIndex = 2, regionId = "p2-r1", cacheKey = "crop-a"))
        assertEquals(null, store.readRegionCrop("book-1", pageIndex = 2, regionId = "p2-r2", cacheKey = "crop-a"))
        assertEquals(null, store.readRegionCrop("book-1", pageIndex = 2, regionId = "p2-r1", cacheKey = "crop-b"))

        store.clearBook("book-1")

        assertEquals(null, store.readRegionCrop("book-1", pageIndex = 2, regionId = "p2-r1", cacheKey = "crop-a"))
    }

    @Test
    fun exportBooksReturnsOneJsonPerSavedBook() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(sampleBook())
        store.saveBookNow(sampleBook().copy(bookId = "book-2"))

        assertEquals(listOf("book-1", "book-2"), store.exportBooks().map { it.bookId }.sorted())
    }

    @Test
    fun exportBooksSkipsBooksWithoutCompletedTranslations() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(sampleBook())
        store.saveBookNow(
            sampleBook().copy(
                bookId = "pending-book",
                pages = listOf(AiTranslatedPage(pageIndex = 0, status = AiTranslationPageStatus.PENDING))
            )
        )
        store.saveBookNow(
            sampleBook().copy(
                bookId = "failed-book",
                pages = listOf(AiTranslatedPage(pageIndex = 0, status = AiTranslationPageStatus.FAILED))
            )
        )

        assertEquals(listOf("book-1"), store.exportBooks().map { it.bookId })
    }

    @Test
    fun importBooksWritesRestoredBooksAndSkipsBlankIds() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))

        store.importBooks(
            listOf(
                sampleBook().copy(bookId = "book-restored"),
                sampleBook().copy(bookId = "")
            )
        )

        assertEquals("book-restored", store.readBook("book-restored")?.bookId)
        assertEquals(null, store.readBook(""))
    }

    @Test
    fun storeCanListCachedTranslationBookIdsForMaintenance() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(sampleBook())
        store.saveBookNow(sampleBook().copy(bookId = "book-2"))

        assertEquals(listOf("book-1", "book-2"), store.listBookIds().sorted())
    }

    @Test
    fun readBookUsesExplicitPageParsingForReleaseBuilds() {
        val source = java.io.File("src/main/java/fail/tiger/komgarot/data/local/AiTranslationStore.kt").readText()

        assertTrue(source.contains("JsonParser.parseString"))
        assertTrue(source.contains("parseAiTranslatedPageElement"))
        assertTrue(source.contains("parseAiTranslationBlockElement"))
        assertTrue(source.contains("parseAiTranslationRect"))
    }

    @Test
    fun saveBookNowUsesExplicitBookJsonWriterForReleaseBuilds() {
        val source = java.io.File("src/main/java/fail/tiger/komgarot/data/local/AiTranslationStore.kt").readText()

        assertTrue(source.contains("toAiTranslatedBookJson(book)"))
        assertTrue(source.contains("JsonArray().apply"))
        assertTrue(source.contains("addProperty(\"pageIndex\", page.pageIndex)"))
        assertTrue(source.contains("addProperty(\"status\", page.status.name)"))
    }

    @Test
    fun storeSerializesFileAccessAndUsesUniqueTempFiles() {
        val source = java.io.File("src/main/java/fail/tiger/komgarot/data/local/AiTranslationStore.kt").readText()

        assertTrue(source.contains("@Synchronized\n    fun readBook("))
        assertTrue(source.contains("@Synchronized\n    fun saveBookNow("))
        assertTrue(source.contains("@Synchronized\n    fun upsertPages("))
        assertTrue(source.contains("File.createTempFile("))
        assertTrue(source.contains("tmp.delete()"))
    }

    @Test
    fun rawBookStateReportsUnreadableJsonDetails() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val file = store.bookFile("book-1")
        file.parentFile?.mkdirs()
        file.writeText("{broken")

        val state = store.rawBookState("book-1")

        assertTrue(state.contains("length=7"))
        assertTrue(state.contains("parseError="))
        assertTrue(state.contains("head={broken"))
    }

    @Test
    fun atomicWriteChecksRenameResultAndReplacesExistingFile() {
        val source = java.io.File("src/main/java/fail/tiger/komgarot/data/local/AiTranslationStore.kt").readText()

        assertTrue(source.contains("if (!tmp.renameTo(file))"))
        assertTrue(source.contains("file.delete()"))
        assertTrue(source.contains("error(\"failed to replace \${file.name}\")"))
    }

    @Test
    fun readTaskStateUsesExplicitTaskParsingForReleaseBuilds() {
        val source = java.io.File("src/main/java/fail/tiger/komgarot/data/local/AiTranslationStore.kt").readText()

        assertTrue(source.contains("parseAiTranslationTaskStateJson"))
        assertTrue(source.contains("private fun parseAiTranslationTaskSummary("))
        assertTrue(source.contains("failureCategories = failureCategories"))
        assertTrue(source.contains("targetPageIndexes = taskObject.getAsJsonArrayOrNull"))
    }

    @Test
    fun proguardKeepsAiTranslationGsonModels() {
        val rules = java.io.File("../app/proguard-rules.pro").readText()

        assertTrue(rules.contains("-keep class fail.tiger.komgarot.data.local.Ai** { *; }"))
        assertTrue(rules.contains("-keep class fail.tiger.komgarot.data.repository.WebDavBackup** { *; }"))
    }

    @Test
    fun readBookParsesStoredPagesAndBlocksAsTypedObjects() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val file = store.bookFile("book-1")
        file.parentFile?.mkdirs()
        file.writeText(
            """
            {
              "schemaVersion": 1,
              "bookId": "book-1",
              "seriesId": "series-1",
              "title": "Book",
              "pageCount": 1,
              "pages": [
                {
                  "pageIndex": 0,
                  "status": "DONE",
                  "imageWidth": 1200,
                  "imageHeight": 1800,
                  "blocks": [
                    {
                      "localRegionId": "p0-r1",
                      "kind": "DIALOGUE",
                      "sourceText": "hello",
                      "translatedLines": ["你好"],
                      "rect": { "x": 0.1, "y": 0.2, "width": 0.3, "height": 0.4 },
                      "translationRect": { "x": 0.12, "y": 0.24, "width": 0.12, "height": 0.22 },
                      "sourceColumns": [
                        { "x": 0.18, "y": 0.22, "width": 0.03, "height": 0.18 },
                        { "x": 0.14, "y": 0.22, "width": 0.03, "height": 0.18 }
                      ],
                      "textColor": "#111111",
                      "maskColor": "#FFFFFF",
                      "textDirection": "vertical",
                      "maskAlpha": 0.72,
                      "cornerRadius": 0.04,
                      "rotationDegrees": 0,
                      "fontScale": 1,
                      "confidence": 0.9
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val page = store.readBook("book-1")!!.pages.single()
        val block = page.blocks.single()

        assertEquals(0, page.pageIndex)
        assertEquals(AiTranslationPageStatus.DONE, page.status)
        assertEquals("hello", block.sourceText)
        assertEquals("p0-r1", block.localRegionId)
        assertEquals(listOf("你好"), block.translatedLines)
        assertEquals(AiTranslationTextDirection.VERTICAL, block.textDirection)
        assertEquals(0.12f, block.translationRect.x)
        assertEquals(0.22f, block.translationRect.height)
        assertEquals(2, block.sourceColumns.size)
        assertEquals(0.18f, block.sourceColumns.first().x, 0.0001f)
    }

    @Test
    fun readBookAcceptsModelBlockKindsInStoredJson() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val file = store.bookFile("book-1")
        file.parentFile?.mkdirs()
        file.writeText(
            """
            {
              "schemaVersion": 1,
              "bookId": "book-1",
              "pageCount": 12,
              "pages": [
                {
                  "pageIndex": 11,
                  "status": "DONE",
                  "blocks": [
                    {
                      "kind": "speech",
                      "translatedLines": ["示例文本……"],
                      "rect": [0.78, 0.53, 0.13, 0.09],
                      "textColor": "#000000",
                      "maskColor": "#FFFFFF"
                    },
                    {
                      "kind": "sfx",
                      "translatedLines": ["示例音效"],
                      "rect": [0.44, 0.44, 0.47, 0.18],
                      "textColor": "#FFFFFF",
                      "maskColor": "#000000"
                    },
                    {
                      "kind": "narration",
                      "translatedLines": ["!!"],
                      "rect": [0.78, 0.13, 0.08, 0.05],
                      "textColor": "#000000",
                      "maskColor": "#FFFFFF"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val page = store.readBook("book-1")!!.pages.single()

        assertEquals(11, page.pageIndex)
        assertEquals(AiTranslationPageStatus.DONE, page.status)
        assertEquals(AiTranslationBlockKind.DIALOGUE, page.blocks[0].kind)
        assertEquals(AiTranslationBlockKind.SFX, page.blocks[1].kind)
        assertEquals(AiTranslationBlockKind.NARRATION, page.blocks[2].kind)
    }

    @Test
    fun readBookAcceptsLegacyR8ObfuscatedJson() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val file = store.bookFile("0QPVECHMGS85H")
        file.parentFile?.mkdirs()
        file.writeText(
            """
            {
              "a": "0QPVECHMGS85H",
              "b": "0QPVECHMCS0NW",
              "c": "Example Series - Vol.01",
              "d": "",
              "e": 174,
              "f": { "a": "" },
              "g": { "a": "", "b": "", "c": "openai-compatible", "d": "" },
              "h": [],
              "i": [
                { "a": 11, "b": "FAILED", "c": 1781542037777, "d": [], "e": "timeout" }
              ]
            }
            """.trimIndent()
        )

        val book = store.readBook("0QPVECHMGS85H")!!
        val page = book.pages.single()

        assertEquals("0QPVECHMGS85H", book.bookId)
        assertEquals("0QPVECHMCS0NW", book.seriesId)
        assertEquals("Example Series - Vol.01", book.title)
        assertEquals(174, book.pageCount)
        assertEquals(11, page.pageIndex)
        assertEquals(AiTranslationPageStatus.FAILED, page.status)
        assertEquals("timeout", page.errorSummary)
    }

    @Test
    fun parseAiTranslatedPagesAcceptsSinglePageModelResponseShape() {
        val json = """
            {
              "pageIndex": 6,
              "imageWidth": 768,
              "imageHeight": 1117,
              "blocks": [
                {
                  "kind": "dialogue",
                  "sourceText": "Test line A.",
                  "translatedLines": ["测试文本 A。"],
                  "rect": [0.12, 0.13, 0.14, 0.12],
                  "textColor": "#FFFFFF",
                  "maskColor": "#232323",
                  "maskAlpha": 0.7,
                  "cornerRadius": 0.12,
                  "rotationDegrees": -16,
                  "fontScale": 1.02,
                  "confidence": 0.96
                }
              ],
              "glossaryUpdates": []
            }
        """.trimIndent()

        val page = parseAiTranslatedPagesJson(json).single()
        val block = page.blocks.single()

        assertEquals(6, page.pageIndex)
        assertEquals(AiTranslationPageStatus.DONE, page.status)
        assertEquals(AiTranslationBlockKind.DIALOGUE, block.kind)
        assertEquals(0.12f, block.rect.x)
        assertEquals(0.13f, block.rect.y)
        assertEquals(0.14f, block.rect.width)
        assertEquals(0.12f, block.rect.height)
        assertTrue(block.rect.x + block.rect.width <= 1.0001f)
        assertEquals(listOf("测试文本 A。"), block.translatedLines)
    }

    @Test
    fun parseAiTranslatedPagesAcceptsSpeechAndSfxKindsFromModel() {
        val json = """
            {
              "pageIndex": 6,
              "imageWidth": 768,
              "imageHeight": 1117,
              "blocks": [
                {
                  "kind": "speech",
                  "sourceText": "Test line B...",
                  "translatedLines": ["测试文本 B……"],
                  "rect": [0.78, 0.53, 0.13, 0.09],
                  "textColor": "#000000",
                  "maskColor": "#FFFFFF",
                  "maskAlpha": 0.85,
                  "cornerRadius": 0.18,
                  "rotationDegrees": -2,
                  "fontScale": 1.01,
                  "confidence": 0.98
                },
                {
                  "kind": "sfx",
                  "sourceText": "BOOM",
                  "translatedLines": ["轰隆"],
                  "rect": [0.44, 0.44, 0.47, 0.18],
                  "textColor": "#FFFFFF",
                  "maskColor": "#000000",
                  "maskAlpha": 0.56,
                  "cornerRadius": 0.13,
                  "rotationDegrees": 19,
                  "fontScale": 1.22,
                  "confidence": 0.97
                }
              ]
            }
        """.trimIndent()

        val page = parseAiTranslatedPagesJson(json).single()

        assertEquals(AiTranslationPageStatus.DONE, page.status)
        assertEquals(2, page.blocks.size)
        assertEquals(AiTranslationBlockKind.DIALOGUE, page.blocks[0].kind)
        assertEquals(AiTranslationBlockKind.SFX, page.blocks[1].kind)
    }

    @Test
    fun parseAiTranslatedBookReadsSourceTextProfileAndDefaultsLegacyBooksToAuto() {
        val withProfile = parseAiTranslatedBookJson(
            """
            {
              "schemaVersion": 1,
              "bookId": "book-1",
              "pageCount": 1,
              "translation": {
                "targetLocale": "zh-CN",
                "targetLanguageName": "简体中文",
                "sourceTextProfile": "korean_horizontal_webtoon"
              },
              "pages": []
            }
            """.trimIndent()
        )!!
        val legacy = parseAiTranslatedBookJson(
            """
            {
              "schemaVersion": 1,
              "bookId": "book-2",
              "pageCount": 1,
              "translation": {
                "targetLocale": "zh-CN",
                "targetLanguageName": "简体中文"
              },
              "pages": []
            }
            """.trimIndent()
        )!!

        assertEquals(AiSourceTextProfile.KOREAN_HORIZONTAL_WEBTOON.storedValue, withProfile.translation.sourceTextProfile)
        assertEquals(AiSourceTextProfile.AUTO.storedValue, legacy.translation.sourceTextProfile)
        assertEquals("", legacy.translation.sourceLanguageCode)
        assertEquals(AiSourceLanguageOrigin.AI_PENDING.storedValue, legacy.translation.sourceLanguageOrigin)
        assertEquals(AiSourceReadingDirection.UNKNOWN.storedValue, legacy.translation.sourceReadingDirection)
    }

    @Test
    fun failedPageErrorCategoryRoundTripsAndLegacyFailuresDefaultToUnknown() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val failedPage = AiTranslatedPage(
            pageIndex = 3,
            status = AiTranslationPageStatus.FAILED,
            errorSummary = "AI request timed out after 30s.",
            errorCategory = AiTranslationFailureCategory.NETWORK_OR_API.storedValue,
            errorHttpStatus = 429,
            retryAfterMs = 3_000L
        )

        store.saveBookNow(sampleBook().copy(pages = listOf(failedPage)))

        val savedPage = store.readBook("book-1")!!.pages.single()
        assertEquals(AiTranslationFailureCategory.NETWORK_OR_API.storedValue, savedPage.errorCategory)
        assertEquals(429, savedPage.errorHttpStatus)
        assertEquals(3_000L, savedPage.retryAfterMs)

        val legacyPage = parseAiTranslatedBookJson(
            """
            {
              "bookId": "legacy-book",
              "pageCount": 1,
              "pages": [
                {
                  "pageIndex": 0,
                  "status": "FAILED",
                  "errorSummary": "timeout"
                }
              ]
            }
            """.trimIndent()
        )!!.pages.single()

        assertEquals(AiTranslationFailureCategory.UNKNOWN.storedValue, legacyPage.errorCategory)
        assertEquals(null, legacyPage.errorHttpStatus)
        assertEquals(null, legacyPage.retryAfterMs)
    }

    @Test
    fun taskSummaryRoundTrips() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val summary = AiTranslationTaskSummary(
            bookId = "book-1",
            title = "Book",
            pageCount = 10,
            completedPages = 4,
            failedPages = 1,
            failureCategories = mapOf(AiTranslationFailureCategory.NETWORK_OR_API.storedValue to 1),
            targetPageIndexes = listOf(2, 3, 4),
            recoveryRequired = true,
            status = AiTranslationTaskStatus.RUNNING,
            updatedAt = 123
        )

        store.saveTaskState(AiTranslationTaskState(paused = false, tasks = listOf(summary)))

        val state = store.readTaskState()
        assertFalse(state.paused)
        assertEquals("book-1", state.tasks.single().bookId)
        assertEquals(4, state.tasks.single().completedPages)
        assertEquals(mapOf(AiTranslationFailureCategory.NETWORK_OR_API.storedValue to 1), state.tasks.single().failureCategories)
        assertEquals(listOf(2, 3, 4), state.tasks.single().targetPageIndexes)
        assertTrue(state.tasks.single().recoveryRequired)
    }

    @Test
    fun saveTaskStateKeepsQueuedAndFailedTasksVisible() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))

        store.saveTaskState(
            AiTranslationTaskState(
                tasks = listOf(
                    AiTranslationTaskSummary(bookId = "empty", pageCount = 10),
                    AiTranslationTaskSummary(bookId = "done", pageCount = 10, completedPages = 1),
                    AiTranslationTaskSummary(bookId = "failed", pageCount = 10, failedPages = 1)
                )
            )
        )

        assertEquals(listOf("empty", "done", "failed"), store.readTaskState().tasks.map { it.bookId })
    }

    @Test
    fun readTaskStateParsesStoredTasksAsTypedObjects() {
        val filesDir = temporaryFolder.newFolder("files")
        val store = AiTranslationStore(filesDir)
        val taskFile = java.io.File(filesDir, "ai_translation/tasks.json")
        taskFile.parentFile?.mkdirs()
        taskFile.writeText(
            """
            {
              "schemaVersion": 1,
              "paused": false,
                  "tasks": [
                {
                  "bookId": "empty",
                  "title": "Empty",
                  "pageCount": 10,
                  "completedPages": 0,
                  "failedPages": 0,
                  "status": "RUNNING",
                  "updatedAt": 1
                },
                {
                  "bookId": "failed-only",
                  "title": "Failed",
                  "pageCount": 10,
                  "completedPages": 0,
                  "failedPages": 1,
                  "status": "FAILED",
                  "updatedAt": 2
                },
                {
                  "bookId": "book-1",
                  "title": "Book",
                  "pageCount": 10,
                  "completedPages": 4,
                  "failedPages": 1,
                  "status": "RUNNING",
                  "updatedAt": 123
                }
              ]
            }
            """.trimIndent()
        )

        val tasks = store.readTaskState().tasks.associateBy { it.bookId }
        val task = requireNotNull(tasks["book-1"])

        assertEquals(setOf("empty", "failed-only", "book-1"), tasks.keys)
        assertEquals("book-1", task.bookId)
        assertEquals(AiTranslationTaskStatus.RUNNING, task.status)
        assertEquals(123, task.updatedAt)
        assertEquals(emptyList<Int>(), task.targetPageIndexes)
        assertFalse(task.recoveryRequired)
    }

    @Test
    fun queueRunnerConvertsInterruptedTasksAndPagesToRecoverableState() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(
            sampleBook().copy(
                pages = listOf(
                    AiTranslatedPage(
                        pageIndex = 0,
                        status = AiTranslationPageStatus.RUNNING,
                        blocks = listOf(
                            AiTranslationBlock(
                                localRegionId = "p0-r1",
                                regionStatus = AiTranslationRegionStatus.RUNNING
                            )
                        )
                    )
                )
            )
        )
        store.saveTaskState(
            AiTranslationTaskState(
                tasks = listOf(
                    AiTranslationTaskSummary(
                        bookId = "book-1",
                        pageCount = 1,
                        targetPageIndexes = listOf(0),
                        status = AiTranslationTaskStatus.RUNNING
                    )
                )
            )
        )

        AiTranslationQueueRunner(store).restoreRunningTasks()

        val restoredTask = store.readTaskState().tasks.single()
        val restoredPage = store.readBook("book-1")!!.pages.single()
        assertTrue(store.readTaskState().paused)
        assertEquals(AiTranslationTaskStatus.PAUSED, restoredTask.status)
        assertTrue(restoredTask.recoveryRequired)
        assertEquals(AiTranslationPageStatus.PENDING, restoredPage.status)
        assertEquals(AiTranslationRegionStatus.PENDING, restoredPage.blocks.single().regionStatus)
    }

    @Test
    fun upsertPagesReplacesOnlyMatchingPageIndexes() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(
            sampleBook().copy(
                pages = listOf(
                    AiTranslatedPage(pageIndex = 0, status = AiTranslationPageStatus.PENDING),
                    AiTranslatedPage(pageIndex = 1, status = AiTranslationPageStatus.PENDING)
                )
            )
        )

        store.upsertPages(
            bookId = "book-1",
            pages = listOf(AiTranslatedPage(pageIndex = 1, status = AiTranslationPageStatus.DONE))
        )

        val pages = store.readBook("book-1")!!.pages.sortedBy { it.pageIndex }
        assertEquals(AiTranslationPageStatus.PENDING, pages[0].status)
        assertEquals(AiTranslationPageStatus.DONE, pages[1].status)
    }

    @Test
    fun upsertPagesCreatesBookFileWhenMissing() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))

        store.upsertPages(
            bookId = "book-1",
            pages = listOf(AiTranslatedPage(pageIndex = 11, status = AiTranslationPageStatus.DONE))
        )

        val page = store.readBook("book-1")!!.pages.single()
        assertEquals(11, page.pageIndex)
        assertEquals(AiTranslationPageStatus.DONE, page.status)
    }

    @Test
    fun resetRunningPagesKeepsDonePagesAndTurnsRunningPagesPending() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(
            sampleBook().copy(
                pages = listOf(
                    AiTranslatedPage(
                        pageIndex = 0,
                        status = AiTranslationPageStatus.DONE,
                        blocks = listOf(AiTranslationBlock(localRegionId = "done"))
                    ),
                    AiTranslatedPage(
                        pageIndex = 1,
                        status = AiTranslationPageStatus.RUNNING,
                        blocks = listOf(
                            AiTranslationBlock(
                                localRegionId = "done-region",
                                regionStatus = AiTranslationRegionStatus.DONE,
                                translatedLines = listOf("完成")
                            ),
                            AiTranslationBlock(
                                localRegionId = "running-region",
                                regionStatus = AiTranslationRegionStatus.RUNNING
                            )
                        ),
                        errorSummary = "running",
                        errorCategory = AiTranslationFailureCategory.UNKNOWN.storedValue,
                        errorHttpStatus = 503,
                        retryAfterMs = 2_000L
                    ),
                    AiTranslatedPage(
                        pageIndex = 2,
                        status = AiTranslationPageStatus.FAILED,
                        errorSummary = "failed",
                        errorCategory = AiTranslationFailureCategory.NETWORK_OR_API.storedValue
                    )
                )
            )
        )

        store.resetRunningPages(bookId = "book-1", pageIndexes = listOf(0, 1, 2))

        val pages = store.readBook("book-1")!!.pages.sortedBy { it.pageIndex }
        assertEquals(AiTranslationPageStatus.DONE, pages[0].status)
        assertEquals(listOf("done"), pages[0].blocks.map { it.localRegionId })
        assertEquals(AiTranslationPageStatus.PENDING, pages[1].status)
        assertEquals(listOf("done-region", "running-region"), pages[1].blocks.map { it.localRegionId })
        assertEquals(
            listOf(AiTranslationRegionStatus.DONE, AiTranslationRegionStatus.PENDING),
            pages[1].blocks.map { it.regionStatus }
        )
        assertEquals("", pages[1].errorSummary)
        assertEquals("", pages[1].errorCategory)
        assertEquals(null, pages[1].errorHttpStatus)
        assertEquals(null, pages[1].retryAfterMs)
        assertEquals(AiTranslationPageStatus.FAILED, pages[2].status)
        assertEquals("failed", pages[2].errorSummary)
    }

    @Test
    fun recoverInterruptedPagesPreservesCompletedRegions() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(
            sampleBook().copy(
                pages = listOf(
                    AiTranslatedPage(
                        pageIndex = 0,
                        status = AiTranslationPageStatus.RUNNING,
                        blocks = listOf(
                            AiTranslationBlock(
                                localRegionId = "p0-r1",
                                regionStatus = AiTranslationRegionStatus.DONE,
                                translatedLines = listOf("完成")
                            ),
                            AiTranslationBlock(
                                localRegionId = "p0-r2",
                                regionStatus = AiTranslationRegionStatus.RUNNING
                            )
                        )
                    )
                )
            )
        )

        store.recoverInterruptedPages("book-1")

        val page = store.readBook("book-1")!!.pages.single()
        assertEquals(AiTranslationPageStatus.PENDING, page.status)
        assertEquals(
            listOf(AiTranslationRegionStatus.DONE, AiTranslationRegionStatus.PENDING),
            page.blocks.map { it.regionStatus }
        )
        assertEquals(listOf("完成"), page.blocks.first().translatedLines)
    }

    @Test
    fun markPagesRunningKeepsDoneRegionsAndQueuesFailedRegions() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(
            sampleBook().copy(
                pages = listOf(
                    AiTranslatedPage(
                        pageIndex = 0,
                        status = AiTranslationPageStatus.FAILED,
                        blocks = listOf(
                            AiTranslationBlock(
                                localRegionId = "p0-r1",
                                regionStatus = AiTranslationRegionStatus.DONE,
                                translatedLines = listOf("完成")
                            ),
                            AiTranslationBlock(
                                localRegionId = "p0-r2",
                                regionStatus = AiTranslationRegionStatus.FAILED
                            )
                        ),
                        errorSummary = "timeout",
                        errorCategory = AiTranslationFailureCategory.NETWORK_OR_API.storedValue
                    )
                )
            )
        )

        store.markPagesRunning("book-1", listOf(0), AiTranslationMode.LOCAL_DETECTION)

        val page = store.readBook("book-1")!!.pages.single()
        assertEquals(AiTranslationPageStatus.RUNNING, page.status)
        assertEquals(
            listOf(AiTranslationRegionStatus.DONE, AiTranslationRegionStatus.PENDING),
            page.blocks.map { it.regionStatus }
        )
        assertEquals("", page.errorSummary)
        assertEquals("", page.errorCategory)
    }

    @Test
    fun regionStatusRoundTripsAndLegacyBlocksMigrateFromPageState() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(
            sampleBook().copy(
                pages = listOf(
                    AiTranslatedPage(
                        pageIndex = 0,
                        status = AiTranslationPageStatus.FAILED,
                        blocks = listOf(
                            AiTranslationBlock(
                                localRegionId = "p0-r1",
                                regionStatus = AiTranslationRegionStatus.DONE,
                                translatedLines = listOf("完成")
                            ),
                            AiTranslationBlock(
                                localRegionId = "p0-r2",
                                regionStatus = AiTranslationRegionStatus.FAILED
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            listOf(AiTranslationRegionStatus.DONE, AiTranslationRegionStatus.FAILED),
            store.readBook("book-1")!!.pages.single().blocks.map { it.regionStatus }
        )

        val legacy = parseAiTranslatedBookJson(
            """
            {
              "bookId": "legacy-book",
              "pageCount": 1,
              "pages": [
                {
                  "pageIndex": 0,
                  "status": "FAILED",
                  "blocks": [
                    {"localRegionId":"p0-r1","translatedLines":["完成"]},
                    {"localRegionId":"p0-r2","translatedLines":[]}
                  ]
                }
              ]
            }
            """.trimIndent()
        )!!.pages.single()

        assertEquals(
            listOf(AiTranslationRegionStatus.DONE, AiTranslationRegionStatus.FAILED),
            legacy.blocks.map { it.regionStatus }
        )
    }

    @Test
    fun deletePageKeepsBookFileAndRemovesOnlyOnePage() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(
            sampleBook().copy(
                pages = listOf(
                    AiTranslatedPage(pageIndex = 0, status = AiTranslationPageStatus.DONE),
                    AiTranslatedPage(pageIndex = 1, status = AiTranslationPageStatus.DONE)
                )
            )
        )

        store.deletePage(bookId = "book-1", pageIndex = 0)

        assertEquals(listOf(1), store.readBook("book-1")!!.pages.map { it.pageIndex })
    }

    @Test
    fun deletePageClearsLocalDetectionCachesForThatPage() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val pageFourContext = AiTranslationLocalPageContext(
            pageIndex = 4,
            imageWidth = 1000,
            imageHeight = 1600,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p4-r1",
                    rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.2f, height = 0.3f),
                    textDirection = AiTranslationTextDirection.VERTICAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.9f,
                    estimatedFontScale = 1f
                )
            )
        )
        val pageFiveContext = pageFourContext.copy(
            pageIndex = 5,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p5-r1",
                    rect = AiTranslationRect(x = 0.4f, y = 0.2f, width = 0.2f, height = 0.3f),
                    textDirection = AiTranslationTextDirection.VERTICAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.9f,
                    estimatedFontScale = 1f
                )
            )
        )
        val pageFourCrop = byteArrayOf(4, 4, 4)
        val pageFiveCrop = byteArrayOf(5, 5, 5)
        store.saveBookNow(
            sampleBook().copy(
                pages = listOf(
                    AiTranslatedPage(pageIndex = 4, status = AiTranslationPageStatus.DONE),
                    AiTranslatedPage(pageIndex = 5, status = AiTranslationPageStatus.DONE)
                )
            )
        )
        store.saveLocalPageContext("book-1", pageIndex = 4, cacheKey = "context-key", context = pageFourContext)
        store.saveLocalPageContext("book-1", pageIndex = 5, cacheKey = "context-key", context = pageFiveContext)
        store.saveRegionCrop("book-1", pageIndex = 4, regionId = "p4-r1", cacheKey = "crop-key", bytes = pageFourCrop)
        store.saveRegionCrop("book-1", pageIndex = 5, regionId = "p5-r1", cacheKey = "crop-key", bytes = pageFiveCrop)

        store.deletePage(bookId = "book-1", pageIndex = 4)

        assertEquals(null, store.readLocalPageContext("book-1", pageIndex = 4, cacheKey = "context-key"))
        assertEquals(pageFiveContext, store.readLocalPageContext("book-1", pageIndex = 5, cacheKey = "context-key"))
        assertEquals(null, store.readRegionCrop("book-1", pageIndex = 4, regionId = "p4-r1", cacheKey = "crop-key"))
        assertArrayEquals(pageFiveCrop, store.readRegionCrop("book-1", pageIndex = 5, regionId = "p5-r1", cacheKey = "crop-key"))
    }

    @Test
    fun clearAllDeletesBooksTasksAndLocalCaches() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val context = AiTranslationLocalPageContext(
            pageIndex = 4,
            imageWidth = 1000,
            imageHeight = 1600,
            regions = listOf(
                AiTranslationLocalTextRegion(
                    id = "p4-r1",
                    rect = AiTranslationRect(x = 0.1f, y = 0.2f, width = 0.2f, height = 0.3f),
                    textDirection = AiTranslationTextDirection.VERTICAL,
                    textColor = "#111111",
                    backgroundColor = "#FFFFFF",
                    confidence = 0.9f,
                    estimatedFontScale = 1f
                )
            )
        )
        store.saveBookNow(sampleBook())
        store.saveTaskState(
            AiTranslationTaskState(
                tasks = listOf(AiTranslationTaskSummary(bookId = "book-1", pageCount = 10, completedPages = 1))
            )
        )
        store.saveLocalPageContext("book-1", pageIndex = 4, cacheKey = "context-key", context = context)
        store.saveRegionCrop("book-1", pageIndex = 4, regionId = "p4-r1", cacheKey = "crop-key", bytes = byteArrayOf(4))

        store.clearAll()

        assertEquals(null, store.readBook("book-1"))
        assertEquals(emptyList<AiTranslationTaskSummary>(), store.readTaskState().tasks)
        assertEquals(null, store.readLocalPageContext("book-1", pageIndex = 4, cacheKey = "context-key"))
        assertEquals(null, store.readRegionCrop("book-1", pageIndex = 4, regionId = "p4-r1", cacheKey = "crop-key"))
    }

    private fun sampleBook() = AiTranslatedBook(
        bookId = "book-1",
        seriesId = "series-1",
        title = "Book",
        pageCount = 1,
        pages = listOf(
            AiTranslatedPage(
                pageIndex = 0,
                status = AiTranslationPageStatus.DONE,
                imageWidth = 100,
                imageHeight = 200,
                blocks = emptyList()
            )
        )
    )
}
