package fail.tiger.komgarot.data.local

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
                modePinned = true
            )
        )

        store.saveBookNow(book)

        assertTrue(store.bookFile("book-1").isFile)
        assertEquals(book.bookId, store.readBook("book-1")?.bookId)
        assertEquals(AiTranslationMode.LOCAL_DETECTION.storedValue, store.readBook("book-1")?.translation?.mode)
        assertTrue(store.readBook("book-1")?.translation?.modePinned == true)
        assertEquals(AiTranslationPageStatus.DONE, store.readBook("book-1")?.pages?.single()?.status)
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
    fun exportBooksReturnsOneJsonPerSavedBook() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        store.saveBookNow(sampleBook())
        store.saveBookNow(sampleBook().copy(bookId = "book-2"))

        assertEquals(listOf("book-1", "book-2"), store.exportBooks().map { it.bookId }.sorted())
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
        assertTrue(source.contains("AiTranslationTaskSummary::class.java"))
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
                      "translatedLines": ["没用……"],
                      "rect": [0.78, 0.53, 0.13, 0.09],
                      "textColor": "#000000",
                      "maskColor": "#FFFFFF"
                    },
                    {
                      "kind": "sfx",
                      "translatedLines": ["咕哦哦哦哦"],
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
              "c": "ドロヘドロ - Vol.01",
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
        assertEquals("ドロヘドロ - Vol.01", book.title)
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
                  "sourceText": "ギロ。",
                  "translatedLines": ["瞪视。"],
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
        assertEquals(listOf("瞪视。"), block.translatedLines)
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
                  "sourceText": "効かん…",
                  "translatedLines": ["没用……"],
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
                  "sourceText": "ゴオオオオオ",
                  "translatedLines": ["咕哦哦哦哦"],
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
    fun taskSummaryRoundTrips() {
        val store = AiTranslationStore(temporaryFolder.newFolder("files"))
        val summary = AiTranslationTaskSummary(
            bookId = "book-1",
            title = "Book",
            pageCount = 10,
            completedPages = 4,
            failedPages = 1,
            status = AiTranslationTaskStatus.RUNNING,
            updatedAt = 123
        )

        store.saveTaskState(AiTranslationTaskState(paused = false, tasks = listOf(summary)))

        val state = store.readTaskState()
        assertFalse(state.paused)
        assertEquals("book-1", state.tasks.single().bookId)
        assertEquals(4, state.tasks.single().completedPages)
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

        val task = store.readTaskState().tasks.single()

        assertEquals("book-1", task.bookId)
        assertEquals(AiTranslationTaskStatus.RUNNING, task.status)
        assertEquals(123, task.updatedAt)
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
