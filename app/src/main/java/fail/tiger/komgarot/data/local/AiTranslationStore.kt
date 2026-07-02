package fail.tiger.komgarot.data.local

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fail.tiger.komgarot.data.remote.AiTranslationLocalPageContext
import java.io.File
import java.security.MessageDigest

class AiTranslationStore(private val filesDir: File) {
    private val rootDir = File(filesDir, "ai_translation")
    private val booksDir = File(rootDir, "books")
    private val localContextDir = File(rootDir, "local_context")
    private val regionCropDir = File(rootDir, "region_crops")
    private val tasksFile = File(rootDir, "tasks.json")

    fun bookFile(bookId: String): File =
        File(booksDir, "${sanitizeBookId(bookId)}.json")

    @Synchronized
    fun readBook(bookId: String): AiTranslatedBook? {
        val file = bookFile(bookId)
        if (!file.isFile) return null
        return parseAiTranslatedBookJson(file.readText())
    }

    @Synchronized
    fun saveBookNow(book: AiTranslatedBook) {
        if (book.bookId.isBlank()) return
        val file = bookFile(book.bookId)
        file.parentFile?.mkdirs()
        writeAtomically(file, toAiTranslatedBookJson(book))
    }

    @Synchronized
    fun rawBookPageCount(bookId: String): Int? {
        val file = bookFile(bookId)
        if (!file.isFile) return null
        return runCatching {
            JsonParser.parseString(file.readText())
                .asObjectOrNull()
                ?.getAsJsonArrayOrNull("pages")
                ?.size()
        }.getOrNull()
    }

    @Synchronized
    fun rawBookState(bookId: String): String {
        val file = bookFile(bookId)
        if (!file.isFile) return "missing"
        val text = runCatching { file.readText() }.getOrElse { throwable ->
            return "readError=${throwable::class.java.simpleName}:${throwable.message.orEmpty()}"
        }
        val parseError = runCatching { JsonParser.parseString(text) }
            .exceptionOrNull()
            ?.let { "${it::class.java.simpleName}:${it.message.orEmpty().take(160)}" }
            ?: "none"
        val head = text.take(240).replace('\n', ' ')
        return "length=${text.length}, parseError=$parseError, head=$head"
    }

    @Synchronized
    fun upsertPages(bookId: String, pages: List<AiTranslatedPage>) {
        if (bookId.isBlank() || pages.isEmpty()) return
        val existing = readBook(bookId) ?: AiTranslatedBook(
            bookId = bookId,
            pageCount = pages.maxOf { it.pageIndex } + 1
        )
        val replacement = pages.associateBy { it.pageIndex }
        val merged = (existing.pages.filterNot { it.pageIndex in replacement.keys } + replacement.values)
            .sortedBy { it.pageIndex }
        val pageCount = maxOf(existing.pageCount, merged.lastOrNull()?.pageIndex?.plus(1) ?: 0)
        saveBookNow(existing.copy(pageCount = pageCount, pages = merged))
    }

    @Synchronized
    fun deletePage(bookId: String, pageIndex: Int) {
        val existing = readBook(bookId) ?: return
        saveBookNow(existing.copy(pages = existing.pages.filterNot { it.pageIndex == pageIndex }))
    }

    @Synchronized
    fun clearBook(bookId: String) {
        bookFile(bookId).delete()
        localContextDir.listFiles { file -> file.name.startsWith("${sanitizeBookId(bookId)}-") }.orEmpty().forEach { it.delete() }
        regionCropDir.listFiles { file -> file.name.startsWith("${sanitizeBookId(bookId)}-") }.orEmpty().forEach { it.delete() }
    }

    @Synchronized
    fun readLocalPageContext(bookId: String, pageIndex: Int, cacheKey: String): AiTranslationLocalPageContext? {
        val file = cacheFile(localContextDir, bookId, pageIndex, "context", cacheKey, "json")
        if (!file.isFile) return null
        return runCatching { storeGson.fromJson(file.readText(), AiTranslationLocalPageContext::class.java) }.getOrNull()
    }

    @Synchronized
    fun saveLocalPageContext(bookId: String, pageIndex: Int, cacheKey: String, context: AiTranslationLocalPageContext) {
        val file = cacheFile(localContextDir, bookId, pageIndex, "context", cacheKey, "json")
        file.parentFile?.mkdirs()
        writeAtomically(file, storeGson.toJson(context))
    }

    @Synchronized
    fun readRegionCrop(bookId: String, pageIndex: Int, regionId: String, cacheKey: String): ByteArray? {
        val file = cacheFile(regionCropDir, bookId, pageIndex, regionId, cacheKey, "jpg")
        return file.takeIf { it.isFile && it.length() > 0L }?.readBytes()
    }

    @Synchronized
    fun saveRegionCrop(bookId: String, pageIndex: Int, regionId: String, cacheKey: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val file = cacheFile(regionCropDir, bookId, pageIndex, regionId, cacheKey, "jpg")
        file.parentFile?.mkdirs()
        val tmp = File.createTempFile("${file.name}.", ".tmp", file.parentFile)
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) error("failed to replace ${file.name}")
            }
        } finally {
            tmp.delete()
        }
    }

    @Synchronized
    fun exportBooks(): List<AiTranslatedBook> {
        if (!booksDir.isDirectory) return emptyList()
        return booksDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?.mapNotNull { file -> parseAiTranslatedBookJson(file.readText()) }
            .orEmpty()
    }

    @Synchronized
    fun readTaskState(): AiTranslationTaskState {
        if (!tasksFile.isFile) return AiTranslationTaskState()
        return parseAiTranslationTaskStateJson(tasksFile.readText())
    }

    @Synchronized
    fun saveTaskState(state: AiTranslationTaskState) {
        tasksFile.parentFile?.mkdirs()
        writeAtomically(tasksFile, storeGson.toJson(state))
    }

    private fun writeAtomically(file: File, text: String) {
        val parent = file.parentFile ?: error("missing parent for ${file.name}")
        val tmp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            tmp.writeText(text)
            if (tmp.readText().isNotBlank()) {
                if (!tmp.renameTo(file)) {
                    file.delete()
                    if (!tmp.renameTo(file)) error("failed to replace ${file.name}")
                }
            } else {
                error("refusing to write blank ${file.name}")
            }
        } finally {
            tmp.delete()
        }
    }

    private fun sanitizeBookId(bookId: String): String =
        bookId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun cacheFile(dir: File, bookId: String, pageIndex: Int, id: String, cacheKey: String, extension: String): File {
        val name = listOf(sanitizeBookId(bookId), pageIndex.toString(), sanitizeBookId(id), sha256(cacheKey))
            .joinToString("-")
        return File(dir, "$name.$extension")
    }
}

private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun toAiTranslatedBookJson(book: AiTranslatedBook): String {
    val root = JsonObject().apply {
        addProperty("schemaVersion", book.schemaVersion)
        addProperty("bookId", book.bookId)
        addProperty("seriesId", book.seriesId)
        addProperty("title", book.title)
        addProperty("seriesTitle", book.seriesTitle)
        addProperty("pageCount", book.pageCount)
        add("fileFingerprint", JsonObject().apply {
            addProperty("mediaType", book.fileFingerprint.mediaType)
            addProperty("sizeBytes", book.fileFingerprint.sizeBytes)
        })
        add("translation", JsonObject().apply {
            addProperty("targetLocale", book.translation.targetLocale)
            addProperty("targetLanguageName", book.translation.targetLanguageName)
            addProperty("provider", book.translation.provider)
            addProperty("model", book.translation.model)
            addProperty("mode", book.translation.mode)
            addProperty("sourceTextProfile", book.translation.sourceTextProfile)
            addProperty("modePinned", book.translation.modePinned)
        })
        add("glossary", JsonArray().apply {
            book.glossary.forEach { entry ->
                add(JsonObject().apply {
                    addProperty("source", entry.source)
                    addProperty("target", entry.target)
                    addProperty("note", entry.note)
                })
            }
        })
        add("pages", JsonArray().apply {
            book.pages.forEach { page ->
                add(JsonObject().apply {
                    addProperty("pageIndex", page.pageIndex)
                    addProperty("status", page.status.name)
                    addProperty("retryCount", page.retryCount)
                    addProperty("updatedAt", page.updatedAt)
                    addProperty("imageWidth", page.imageWidth)
                    addProperty("imageHeight", page.imageHeight)
                    addProperty("mode", page.mode)
                    add("blocks", JsonArray().apply {
                        page.blocks.forEach { block ->
                            add(JsonObject().apply {
                                if (block.localRegionId.isNotBlank()) addProperty("localRegionId", block.localRegionId)
                                addProperty("kind", block.kind.name)
                                addProperty("sourceText", block.sourceText)
                                add("translatedLines", JsonArray().apply {
                                    block.translatedLines.forEach { line -> add(line) }
                                })
                                add("rect", JsonObject().apply {
                                    addProperty("x", block.rect.x)
                                    addProperty("y", block.rect.y)
                                    addProperty("width", block.rect.width)
                                    addProperty("height", block.rect.height)
                                })
                                if (block.translationRect != AiTranslationRect()) {
                                    add("translationRect", JsonObject().apply {
                                        addProperty("x", block.translationRect.x)
                                        addProperty("y", block.translationRect.y)
                                        addProperty("width", block.translationRect.width)
                                        addProperty("height", block.translationRect.height)
                                    })
                                }
                                addProperty("textColor", block.textColor)
                                addProperty("maskColor", block.maskColor)
                                addProperty("maskAlpha", block.maskAlpha)
                                addProperty("cornerRadius", block.cornerRadius)
                                addProperty("rotationDegrees", block.rotationDegrees)
                                addProperty("fontScale", block.fontScale)
                                addProperty("confidence", block.confidence)
                                addProperty("textDirection", block.textDirection.name.lowercase())
                            })
                        }
                    })
                    addProperty("errorSummary", page.errorSummary)
                    addProperty("errorCategory", page.errorCategory)
                })
            }
        })
    }
    return storeGson.toJson(root)
}

internal fun parseAiTranslatedBookJson(text: String): AiTranslatedBook? = runCatching {
    val root = JsonParser.parseString(text).asObjectOrNull() ?: return@runCatching null
    val baseRoot = root.deepCopy().asJsonObject.apply {
        remove("glossary")
        remove("pages")
        remove("h")
        remove("i")
    }
    val base = storeGson.fromJson(baseRoot, AiTranslatedBook::class.java)
    base.copy(
        bookId = root.getStringByAliases("bookId", "a") ?: base.bookId,
        seriesId = root.getStringByAliases("seriesId", "b") ?: base.seriesId,
        title = root.getStringByAliases("title", "c") ?: base.title,
        seriesTitle = root.getStringByAliases("seriesTitle", "d") ?: base.seriesTitle,
        pageCount = root.getIntByAliases("pageCount", "e") ?: base.pageCount,
        translation = (root.getByAliases("translation", "g")?.asObjectOrNull()?.let { translation ->
            base.translation.copy(
                targetLocale = translation.getStringByAliases("targetLocale").orEmpty(),
                targetLanguageName = translation.getStringByAliases("targetLanguageName").orEmpty(),
                provider = translation.getStringByAliases("provider").orEmpty().ifBlank { base.translation.provider },
                model = translation.getStringByAliases("model").orEmpty(),
                mode = translation.getStringByAliases("mode").orEmpty().ifBlank { AiTranslationMode.LOCAL_DETECTION.storedValue },
                sourceTextProfile = AiSourceTextProfile.fromStoredValue(
                    translation.getStringByAliases("sourceTextProfile").orEmpty()
                ).storedValue,
                modePinned = translation.getBooleanByAliases("modePinned").orFalse()
            )
        } ?: base.translation),
        glossary = root.getJsonArrayByAliases("glossary", "h")
            ?.mapNotNull { element ->
                element.asObjectOrNull()?.let { storeGson.fromJson(it, AiGlossaryEntry::class.java) }
            }
            .orEmpty(),
        pages = root.getJsonArrayByAliases("pages", "i")
            ?.mapNotNull(::parseAiTranslatedPageElement)
            .orEmpty()
    )
}.getOrNull()

internal fun parseAiTranslatedPagesJson(text: String): List<AiTranslatedPage> = runCatching {
    val root = JsonParser.parseString(text).asObjectOrNull() ?: return@runCatching emptyList()
    val pageElements = root.getAsJsonArrayOrNull("pages")?.toList()
        ?: root.takeIf { it.has("pageIndex") }?.let { listOf(it) }
        ?: emptyList()
    pageElements.mapNotNull(::parseAiTranslatedPageElement)
        .map { it.copy(status = AiTranslationPageStatus.DONE) }
}.getOrDefault(emptyList())

internal fun parseAiTranslationTaskStateJson(text: String): AiTranslationTaskState = runCatching {
    val root = JsonParser.parseString(text).asObjectOrNull() ?: return@runCatching AiTranslationTaskState()
    val base = storeGson.fromJson(root, AiTranslationTaskState::class.java)
    base.copy(
        tasks = root.getAsJsonArrayOrNull("tasks")
            ?.mapNotNull { element ->
                element.asObjectOrNull()?.let { storeGson.fromJson(it, AiTranslationTaskSummary::class.java) }
            }
            .orEmpty()
    )
}.getOrDefault(AiTranslationTaskState())

private fun parseAiTranslatedPageElement(element: JsonElement): AiTranslatedPage? {
    val obj = element.asObjectOrNull() ?: return null
    return runCatching {
        val status = parseAiTranslationPageStatus(obj.getStringByAliases("status", "b"))
        val errorCategory = obj.getStringByAliases("errorCategory", "j")
            .orEmpty()
            .ifBlank {
                if (status == AiTranslationPageStatus.FAILED) {
                    AiTranslationFailureCategory.UNKNOWN.storedValue
                } else {
                    ""
                }
            }
        AiTranslatedPage(
            pageIndex = obj.getIntByAliases("pageIndex", "a") ?: 0,
            status = status,
            retryCount = obj.getIntByAliases("retryCount") ?: 0,
            updatedAt = obj.getLongByAliases("updatedAt", "c", "d") ?: System.currentTimeMillis(),
            imageWidth = obj.getIntByAliases("imageWidth", "e") ?: 0,
            imageHeight = obj.getIntByAliases("imageHeight", "f") ?: 0,
            mode = obj.getStringByAliases("mode", "i").orEmpty().ifBlank { AiTranslationMode.LOCAL_DETECTION.storedValue },
            blocks = obj.getJsonArrayByAliases("blocks", "d", "g")
                ?.mapNotNull(::parseAiTranslationBlockElement)
                .orEmpty(),
            errorSummary = obj.getStringByAliases("errorSummary", "e", "h").orEmpty(),
            errorCategory = errorCategory
        )
    }.getOrNull()
}

private fun parseAiTranslationPageStatus(value: String?): AiTranslationPageStatus =
    value?.uppercase()
        ?.let { runCatching { AiTranslationPageStatus.valueOf(it) }.getOrNull() }
        ?: AiTranslationPageStatus.PENDING

private fun parseAiTranslationBlockElement(element: JsonElement): AiTranslationBlock? {
    val obj = element.asObjectOrNull() ?: return null
    return runCatching {
        AiTranslationBlock(
            localRegionId = obj.getStringByAliases("localRegionId", "n").orEmpty(),
            kind = parseAiTranslationBlockKind(obj.getStringByAliases("kind", "a")),
            sourceText = obj.getStringByAliases("sourceText", "b").orEmpty(),
            translatedLines = obj.getJsonArrayByAliases("translatedLines", "c")
                ?.mapNotNull { it.asStringOrNull() }
                .orEmpty(),
            rect = parseAiTranslationRect(obj.getByAliases("rect", "d")),
            translationRect = parseAiTranslationRect(obj.getByAliases("translationRect", "m")),
            textColor = obj.getStringByAliases("textColor", "e") ?: "#111111",
            maskColor = obj.getStringByAliases("maskColor", "f") ?: "#FFFFFF",
            maskAlpha = obj.getFloatByAliases("maskAlpha", "g") ?: 0.72f,
            cornerRadius = obj.getFloatByAliases("cornerRadius", "h") ?: 0.04f,
            rotationDegrees = obj.getFloatByAliases("rotationDegrees", "i") ?: 0f,
            fontScale = obj.getFloatByAliases("fontScale", "j") ?: 1f,
            confidence = obj.getFloatByAliases("confidence", "k") ?: 0f,
            textDirection = parseAiTranslationTextDirection(obj.getStringByAliases("textDirection", "l"))
        ).renderSafe()
    }.getOrNull()
}

private fun parseAiTranslationBlockKind(value: String?): AiTranslationBlockKind =
    when (value?.uppercase()) {
        "SPEECH" -> AiTranslationBlockKind.DIALOGUE
        else -> value?.uppercase()
            ?.let { runCatching { AiTranslationBlockKind.valueOf(it) }.getOrNull() }
            ?: AiTranslationBlockKind.OTHER
    }

private fun parseAiTranslationTextDirection(value: String?): AiTranslationTextDirection =
    when (value?.uppercase()) {
        "HORIZONTAL" -> AiTranslationTextDirection.HORIZONTAL
        "VERTICAL" -> AiTranslationTextDirection.VERTICAL
        else -> AiTranslationTextDirection.AUTO
    }

private fun parseAiTranslationRect(element: JsonElement?): AiTranslationRect {
    val array = element?.takeIf { it.isJsonArray }?.asJsonArray
    if (array != null && array.size() >= 4) {
        return AiTranslationRect(
            x = array[0].asFloatOrNull() ?: 0f,
            y = array[1].asFloatOrNull() ?: 0f,
            width = array[2].asFloatOrNull() ?: 0f,
            height = array[3].asFloatOrNull() ?: 0f
        )
    }
    val obj = element?.asObjectOrNull() ?: return AiTranslationRect()
    return AiTranslationRect(
        x = obj.getFloatByAliases("x", "a") ?: 0f,
        y = obj.getFloatByAliases("y", "b") ?: 0f,
        width = obj.getFloatByAliases("width", "c") ?: 0f,
        height = obj.getFloatByAliases("height", "d") ?: 0f
    )
}

private fun JsonObject.getAsJsonArrayOrNull(name: String) =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.getJsonArrayByAliases(vararg names: String) =
    names.firstNotNullOfOrNull { getAsJsonArrayOrNull(it) }

private fun JsonObject.getByAliases(vararg names: String) =
    names.firstNotNullOfOrNull { get(it) }

private fun JsonObject.getStringByAliases(vararg names: String) =
    names.firstNotNullOfOrNull { get(it)?.asStringOrNull() }

private fun JsonObject.getIntByAliases(vararg names: String) =
    names.firstNotNullOfOrNull { get(it)?.asIntOrNull() }

private fun JsonObject.getLongByAliases(vararg names: String) =
    names.firstNotNullOfOrNull { get(it)?.asLongOrNull() }

private fun JsonObject.getFloatByAliases(vararg names: String) =
    names.firstNotNullOfOrNull { get(it)?.asFloatOrNull() }

private fun JsonObject.getBooleanByAliases(vararg names: String) =
    names.firstNotNullOfOrNull { get(it)?.asBooleanOrNull() }

private fun JsonElement.asObjectOrNull(): JsonObject? =
    takeIf { it.isJsonObject }?.asJsonObject

private fun JsonElement.asStringOrNull(): String? =
    takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString

private fun JsonElement.asFloatOrNull(): Float? =
    runCatching { takeIf { it.isJsonPrimitive }?.asFloat }.getOrNull()

private fun JsonElement.asIntOrNull(): Int? =
    runCatching { takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()

private fun JsonElement.asLongOrNull(): Long? =
    runCatching { takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()

private fun JsonElement.asBooleanOrNull(): Boolean? =
    runCatching { takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()

private fun Boolean?.orFalse(): Boolean = this ?: false

data class AiTranslationTaskState(
    val schemaVersion: Int = 1,
    val paused: Boolean = false,
    val tasks: List<AiTranslationTaskSummary> = emptyList()
)

data class AiTranslationTaskSummary(
    val bookId: String = "",
    val title: String = "",
    val pageCount: Int = 0,
    val completedPages: Int = 0,
    val failedPages: Int = 0,
    val failureCategories: Map<String, Int> = emptyMap(),
    val status: AiTranslationTaskStatus = AiTranslationTaskStatus.IDLE,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AiTranslationTaskStatus {
    IDLE,
    QUEUED,
    RUNNING,
    PAUSED,
    DONE,
    FAILED
}

private val storeGson = Gson()
