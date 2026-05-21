package fail.tiger.komgarot

import java.util.concurrent.ConcurrentHashMap

object ThumbnailVersion {
    private val versions = ConcurrentHashMap<String, Int>()

    fun get(id: String): Int = versions[id] ?: 0

    fun bump(id: String) { versions[id] = (versions[id] ?: 0) + 1 }
}
