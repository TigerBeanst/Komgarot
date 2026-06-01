package fail.tiger.komgarot

import androidx.compose.runtime.mutableStateMapOf

object ThumbnailVersion {
    private val versions = mutableStateMapOf<String, Int>()

    fun get(id: String): Int = versions[id] ?: 0

    fun bump(id: String) {
        if (id.isBlank()) return
        versions[id] = (versions[id] ?: 0) + 1
    }
}
