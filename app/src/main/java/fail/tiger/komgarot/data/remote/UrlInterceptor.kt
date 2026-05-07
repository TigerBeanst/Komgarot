package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.local.AuthPreferences
import okhttp3.Interceptor
import okhttp3.Response

class UrlInterceptor(private val prefs: AuthPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = prefs.serverUrlBlocking
        if (base.isEmpty()) return chain.proceed(chain.request())
        val newUrl = chain.request().url.toString().replace("http://localhost", base)
        return chain.proceed(chain.request().newBuilder().url(newUrl).build())
    }
}
