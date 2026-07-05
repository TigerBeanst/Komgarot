package fail.tiger.komgarot.data.local

import android.content.Context
import java.io.File
import java.security.MessageDigest

private const val READER_PAGE_CACHE_MAX_SIZE_BYTES = 2L * 1024L * 1024L * 1024L
private const val READER_PAGE_CACHE_TARGET_SIZE_BYTES = 1800L * 1024L * 1024L

object ReaderPageCache {
    data class Entry(
        val url: String,
        val file: File,
        val tempFile: File
    )

    fun cachedFile(context: Context, url: String): File? {
        val file = cacheFile(context, url)
        return file.takeIf { it.isFile && it.length() > 0L }?.also {
            it.setLastModified(System.currentTimeMillis())
        }
    }

    fun cachedFile(context: Context, bookId: String, url: String): File? {
        val file = cacheFile(context.cacheDir, bookId, url)
        val legacyFile = cacheFile(context, url)
        return listOf(file, legacyFile).firstOrNull { it.isFile && it.length() > 0L }?.also {
            it.setLastModified(System.currentTimeMillis())
        }
    }

    fun cachedFile(context: Context, seriesId: String, bookId: String, url: String): File? {
        val file = cacheFile(context.cacheDir, seriesId, bookId, url)
        val bookFile = cacheFile(context.cacheDir, bookId, url)
        val legacyFile = cacheFile(context, url)
        return listOf(file, bookFile, legacyFile).firstOrNull { it.isFile && it.length() > 0L }?.also {
            it.setLastModified(System.currentTimeMillis())
        }
    }

    fun hasCachedFile(context: Context, url: String): Boolean {
        val file = cacheFile(context, url)
        return file.isFile && file.length() > 0L
    }

    fun hasCachedFile(context: Context, bookId: String, url: String): Boolean {
        val file = cacheFile(context.cacheDir, bookId, url)
        val legacyFile = cacheFile(context, url)
        return file.isFile && file.length() > 0L || legacyFile.isFile && legacyFile.length() > 0L
    }

    fun hasCachedFile(context: Context, seriesId: String, bookId: String, url: String): Boolean {
        val file = cacheFile(context.cacheDir, seriesId, bookId, url)
        val bookFile = cacheFile(context.cacheDir, bookId, url)
        val legacyFile = cacheFile(context, url)
        return file.isFile && file.length() > 0L ||
            bookFile.isFile && bookFile.length() > 0L ||
            legacyFile.isFile && legacyFile.length() > 0L
    }

    fun entry(context: Context, url: String): Entry {
        val file = cacheFile(context, url)
        val tempFile = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
        return Entry(url = url, file = file, tempFile = tempFile)
    }

    fun entry(context: Context, bookId: String, url: String): Entry {
        val file = cacheFile(context.cacheDir, bookId, url)
        val tempFile = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
        return Entry(url = url, file = file, tempFile = tempFile)
    }

    fun entry(context: Context, seriesId: String, bookId: String, url: String): Entry {
        val file = cacheFile(context.cacheDir, seriesId, bookId, url)
        val tempFile = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
        return Entry(url = url, file = file, tempFile = tempFile)
    }

    fun commit(context: Context, entry: Entry): Boolean =
        commit(context, entry, READER_PAGE_CACHE_MAX_SIZE_BYTES)

    fun commit(context: Context, entry: Entry, maxSizeBytes: Long): Boolean {
        if (!entry.tempFile.isFile || entry.tempFile.length() == 0L) {
            entry.tempFile.delete()
            return false
        }
        entry.file.parentFile?.mkdirs()
        val committed = if (entry.file.isFile && entry.file.length() > 0L) {
            entry.tempFile.delete()
            true
        } else {
            entry.tempFile.renameTo(entry.file)
        }
        if (committed) {
            entry.file.setLastModified(System.currentTimeMillis())
            prune(context, maxSizeBytes)
        } else {
            entry.tempFile.delete()
        }
        return committed
    }

    fun discard(entry: Entry) {
        entry.tempFile.delete()
    }

    fun clear(context: Context) {
        cacheDir(context).deleteRecursively()
    }

    fun clear(cacheDir: File) {
        readerPageCacheDir(cacheDir).deleteRecursively()
    }

    fun clearBook(context: Context, bookId: String) {
        clearBook(context.cacheDir, bookId)
    }

    fun clearBook(cacheDir: File, bookId: String) {
        if (bookId.isBlank()) return
        val bookHash = sanitizeId(bookId)
        readerPageCacheDir(cacheDir)
            .listFiles { file ->
                file.isFile && (
                    file.name.startsWith("$bookHash-") ||
                        file.name.contains("-$bookHash-")
                    )
            }
            .orEmpty()
            .forEach { it.delete() }
    }

    fun clearSeries(context: Context, seriesId: String) {
        clearSeries(context.cacheDir, seriesId)
    }

    fun clearSeries(cacheDir: File, seriesId: String) {
        if (seriesId.isBlank()) return
        val prefix = "${sanitizeId(seriesId)}-"
        readerPageCacheDir(cacheDir)
            .listFiles { file -> file.isFile && file.name.startsWith(prefix) }
            .orEmpty()
            .forEach { it.delete() }
    }

    fun size(context: Context): Long =
        cacheDir(context)
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    fun size(cacheDir: File): Long =
        readerPageCacheDir(cacheDir)
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    fun cachedBooksSize(context: Context, cachedBooks: List<CachedBookEntry>): Long =
        cachedBooksSize(context.cacheDir, cachedBooks)

    fun cachedBooksSize(cacheDir: File, cachedBooks: List<CachedBookEntry>): Long {
        if (cachedBooks.isEmpty()) return 0L
        val bookHashes = cachedBooks
            .mapNotNull { entry -> entry.bookId.takeIf { it.isNotBlank() } }
            .map(::sanitizeId)
            .toSet()
        if (bookHashes.isEmpty()) return 0L
        return readerPageCacheDir(cacheDir)
            .listFiles { file ->
                file.isFile && bookHashes.any { hash ->
                    file.name.startsWith("$hash-") || file.name.contains("-$hash-")
                }
            }
            .orEmpty()
            .distinctBy { it.absolutePath }
            .sumOf { it.length() }
    }

    fun prune(context: Context, maxSizeBytes: Long) {
        prune(context.cacheDir, maxSizeBytes, targetSizeBytes(maxSizeBytes))
    }

    fun prune(cacheDir: File, maxSizeBytes: Long, targetSizeBytes: Long = targetSizeBytes(maxSizeBytes)) {
        val dir = readerPageCacheDir(cacheDir)
        val files = dir.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") }.orEmpty()
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxSizeBytes) return

        for (file in files.sortedBy { it.lastModified() }) {
            val length = file.length()
            if (file.delete()) {
                totalSize -= length
            }
            if (totalSize <= targetSizeBytes) break
        }
    }

    fun cacheFile(cacheDir: File, bookId: String, url: String): File {
        return File(readerPageCacheDir(cacheDir), "${sanitizeId(bookId)}-${sha256(url)}")
    }

    fun cacheFile(cacheDir: File, seriesId: String, bookId: String, url: String): File {
        return File(readerPageCacheDir(cacheDir), "${sanitizeId(seriesId)}-${sanitizeId(bookId)}-${sha256(url)}")
    }

    private fun cacheFile(context: Context, url: String): File {
        return File(cacheDir(context), sha256(url))
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun targetSizeBytes(maxSizeBytes: Long): Long =
        if (maxSizeBytes == READER_PAGE_CACHE_MAX_SIZE_BYTES) {
            READER_PAGE_CACHE_TARGET_SIZE_BYTES
        } else {
            (maxSizeBytes * 9L) / 10L
        }

    private fun sanitizeId(id: String): String =
        sha256(id).take(16)

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "reader_page_cache")

    private fun readerPageCacheDir(cacheDir: File): File =
        File(cacheDir, "reader_page_cache")
}
