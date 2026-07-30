package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.remote.normalizeServerUrl
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
                val normalizedUrl = normalizeServerUrl(url)
                verifyServer(normalizedUrl, username, password)
                prefs.save(normalizedUrl, username, password)
            }
        }

    suspend fun updateServerUrl(url: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalizedUrl = normalizeServerUrl(url)
                require(normalizedUrl.isNotBlank()) { "Server URL is empty" }
                val username = prefs.usernameBlocking
                val password = prefs.passwordBlocking
                verifyServer(
                    url = normalizedUrl,
                    username = username,
                    password = password
                )
                prefs.save(normalizedUrl, username, password)
            }
        }

    private fun verifyServer(url: String, username: String, password: String) {
        val request = Request.Builder()
            .url("${url.trimEnd('/')}/api/v1/libraries")
            .header("Authorization", Credentials.basic(username, password))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}: ${response.message}")
        }
    }

    suspend fun logout() = prefs.clear()
}
