package fail.tiger.komgarot.data.local

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import java.io.File

enum class CacheClearTarget {
    All,
    Covers,
    ReaderPages
}

object CacheMaintenance {
    fun clearOnStartupIfNeeded(context: Context, prefs: AuthPreferences) {
        if (prefs.clearCacheOnStartupBlocking) {
            clear(context, CacheClearTarget.All)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clear(context: Context, target: CacheClearTarget) {
        when (target) {
            CacheClearTarget.All -> {
                clearCoverCache(context)
                clearReaderCache(context)
            }
            CacheClearTarget.Covers -> clearCoverCache(context)
            CacheClearTarget.ReaderPages -> clearReaderCache(context)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun clearCoverCache(context: Context) {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
        File(context.cacheDir, "image_cache").deleteRecursively()
    }

    private fun clearReaderCache(context: Context) {
        ReaderPageCache.clear(context)
        BookDownloadIndex(context.cacheDir).clear()
    }
}
