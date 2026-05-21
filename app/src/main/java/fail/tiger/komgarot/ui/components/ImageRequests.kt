package fail.tiger.komgarot.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun rememberStableImageRequest(
    url: String,
    cacheKey: String = url
): ImageRequest {
    val context = LocalContext.current
    return remember(url, cacheKey) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(cacheKey)
            .placeholderMemoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
}
