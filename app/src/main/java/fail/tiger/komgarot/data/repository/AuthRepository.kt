package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AuthPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

class AuthRepository(private val prefs: AuthPreferences) {
    private val client = OkHttpClient()

    suspend fun login(url: String, username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${url.trimEnd('/')}/api/v1/libraries")
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) error("HTTP ${response.code}: ${response.message}")
                prefs.save(url, username, password)
            }
        }

    suspend fun logout() = prefs.clear()
}
