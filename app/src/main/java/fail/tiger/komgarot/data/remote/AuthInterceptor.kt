package fail.tiger.komgarot.data.remote

import fail.tiger.komgarot.data.local.AuthPreferences
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val prefs: AuthPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", Credentials.basic(prefs.usernameBlocking, prefs.passwordBlocking))
                .build()
        )
}
