package fail.tiger.komgarot.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val KOMGAROT_RELEASES_API = "https://api.github.com/repos/TigerBeanst/Komgarot/releases/latest"

class AppUpdateRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun checkForUpdate(localVersionName: String): Result<GithubRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(KOMGAROT_RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            httpClient.newCall(request).execute().use { response ->
                require(response.isSuccessful) { response.message }
                val body = response.body?.string().orEmpty()
                parseGithubRelease(body)
                    ?.takeIf { release -> isRemoteVersionNewer(localVersionName, release.tagName) }
            }
        }
    }
}

data class GithubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String
)

internal fun parseGithubRelease(text: String): GithubRelease? = runCatching {
    val root = JsonParser.parseString(text).asJsonObjectOrNull() ?: return@runCatching null
    if (root.booleanOrFalse("draft") || root.booleanOrFalse("prerelease")) return@runCatching null
    val tagName = root.stringOrNull("tag_name")?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val htmlUrl = root.stringOrNull("html_url")?.takeIf { it.isNotBlank() } ?: return@runCatching null
    GithubRelease(
        tagName = tagName,
        name = root.stringOrNull("name").orEmpty().ifBlank { tagName },
        body = root.stringOrNull("body").orEmpty(),
        htmlUrl = htmlUrl
    )
}.getOrNull()

internal fun isRemoteVersionNewer(local: String, remote: String): Boolean {
    val localSegments = local.versionSegments()
    val remoteSegments = remote.versionSegments()
    val size = maxOf(localSegments.size, remoteSegments.size)
    for (index in 0 until size) {
        val localValue = localSegments.getOrElse(index) { 0 }
        val remoteValue = remoteSegments.getOrElse(index) { 0 }
        if (remoteValue != localValue) return remoteValue > localValue
    }
    return false
}

private fun String.versionSegments(): List<Int> =
    trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.')
        .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? =
    takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.stringOrNull(name: String): String? =
    runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull()

private fun JsonObject.booleanOrFalse(name: String): Boolean =
    runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull() ?: false
