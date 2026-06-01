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

    fun entry(context: Context, url: String): Entry {
        val file = cacheFile(context, url)
        val tempFile = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
        return Entry(url = url, file = file, tempFile = tempFile)
    }

    fun commit(context: Context, entry: Entry): Boolean {
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
            prune(context)
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

    fun size(context: Context): Long =
        cacheDir(context)
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    private fun cacheFile(context: Context, url: String): File {
        return File(cacheDir(context), sha256(url))
    }

    private fun prune(context: Context) {
        val dir = cacheDir(context)
        val files = dir.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") }.orEmpty()
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= READER_PAGE_CACHE_MAX_SIZE_BYTES) return

        for (file in files.sortedBy { it.lastModified() }) {
            val length = file.length()
            if (file.delete()) {
                totalSize -= length
            }
            if (totalSize <= READER_PAGE_CACHE_TARGET_SIZE_BYTES) break
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "reader_page_cache")
}
