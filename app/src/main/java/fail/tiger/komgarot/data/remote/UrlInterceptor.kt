package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.local.AuthPreferences
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.io.IOException

class UrlInterceptor(private val prefs: AuthPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isRetrofitPlaceholder()) {
            return chain.proceed(request)
        }

        val base = prefs.serverUrlBlocking
        val baseUrl = base.toHttpUrlOrNull() ?: throw IOException("Komga server URL is not configured")
        val newPath = baseUrl.encodedPath.trimEnd('/') + request.url.encodedPath
        val newUrl = request.url.newBuilder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .encodedPath(newPath)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

private fun okhttp3.HttpUrl.isRetrofitPlaceholder(): Boolean =
    scheme == "https" && host == "komgarot.invalid" && port == 443
