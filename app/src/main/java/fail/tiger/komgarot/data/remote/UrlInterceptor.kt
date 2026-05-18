package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.local.AuthPreferences
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

class UrlInterceptor(private val prefs: AuthPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = prefs.serverUrlBlocking
        val baseUrl = base.toHttpUrlOrNull() ?: return chain.proceed(chain.request())
        val request = chain.request()
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
