package fail.tiger.komgarot.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay

private const val MAX_THUMBNAIL_RETRIES = 4

@Composable
fun rememberStableImageRequest(
    url: String,
    cacheKey: String = url,
    retryKey: Int = 0,
    skipDiskCacheRead: Boolean = false
): ImageRequest {
    val context = LocalContext.current
    return remember(url, cacheKey, retryKey, skipDiskCacheRead) {
        val memoryCacheKey = if (retryKey == 0) cacheKey else "$cacheKey:retry:$retryKey"
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(memoryCacheKey)
            .placeholderMemoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .setParameter("retry_key", retryKey, memoryCacheKey = null)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(if (skipDiskCacheRead) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
}

@Composable
fun ThumbnailImage(
    url: String,
    cacheKey: String = url,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxRetries: Int = MAX_THUMBNAIL_RETRIES
) {
    var retryKey by remember(url, cacheKey) { mutableIntStateOf(0) }
    var failedRetryKey by remember(url, cacheKey) { mutableIntStateOf(-1) }

    LaunchedEffect(failedRetryKey) {
        val failedKey = failedRetryKey
        if (failedKey >= 0 && failedKey == retryKey && retryKey < maxRetries) {
            delay(thumbnailRetryDelayMillis(failedKey))
            retryKey = failedKey + 1
        }
    }

    AsyncImage(
        model = rememberStableImageRequest(
            url = url,
            cacheKey = cacheKey,
            retryKey = retryKey,
            skipDiskCacheRead = retryKey > 0
        ),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = {
            failedRetryKey = retryKey
        },
        onSuccess = {
            failedRetryKey = -1
        }
    )
}

private fun thumbnailRetryDelayMillis(retryKey: Int): Long =
    700L * (1 shl retryKey.coerceAtMost(3))
