package fail.tiger.komgarot.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureAiSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun read(): SecureAiSettings = SecureAiSettings(
        apiKey = prefs.getString(API_KEY, "").orEmpty(),
        imageUrlExtraQuery = prefs.getString(IMAGE_URL_EXTRA_QUERY, "").orEmpty(),
        s3Endpoint = prefs.getString(S3_ENDPOINT, "").orEmpty(),
        s3Region = prefs.getString(S3_REGION, "").orEmpty(),
        s3Bucket = prefs.getString(S3_BUCKET, "").orEmpty(),
        s3AccessKey = prefs.getString(S3_ACCESS_KEY, "").orEmpty(),
        s3SecretKey = prefs.getString(S3_SECRET_KEY, "").orEmpty(),
        s3PathPrefix = prefs.getString(S3_PATH_PREFIX, DEFAULT_S3_PATH_PREFIX).orEmpty(),
        s3TtlSeconds = prefs.getInt(S3_TTL_SECONDS, DEFAULT_S3_TTL_SECONDS),
        s3PathStyle = prefs.getBoolean(S3_PATH_STYLE, true)
    )

    fun saveApiKey(value: String) {
        prefs.edit { putString(API_KEY, value.trim()) }
    }

    fun saveImageUrlExtraQuery(value: String) {
        prefs.edit { putString(IMAGE_URL_EXTRA_QUERY, value.trim()) }
    }

    fun saveS3Settings(settings: SecureAiSettings) {
        prefs.edit {
            putString(S3_ENDPOINT, settings.s3Endpoint.trim())
            putString(S3_REGION, settings.s3Region.trim())
            putString(S3_BUCKET, settings.s3Bucket.trim())
            putString(S3_ACCESS_KEY, settings.s3AccessKey.trim())
            putString(S3_SECRET_KEY, settings.s3SecretKey.trim())
            putString(S3_PATH_PREFIX, settings.s3PathPrefix.trim().ifBlank { DEFAULT_S3_PATH_PREFIX })
            putInt(S3_TTL_SECONDS, settings.s3TtlSeconds.coerceIn(60, 3600))
            putBoolean(S3_PATH_STYLE, settings.s3PathStyle)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        const val FILE_NAME = "secure_ai_settings"
        const val API_KEY = "api_key"
        const val IMAGE_URL_EXTRA_QUERY = "image_url_extra_query"
        const val S3_ENDPOINT = "s3_endpoint"
        const val S3_REGION = "s3_region"
        const val S3_BUCKET = "s3_bucket"
        const val S3_ACCESS_KEY = "s3_access_key"
        const val S3_SECRET_KEY = "s3_secret_key"
        const val S3_PATH_PREFIX = "s3_path_prefix"
        const val S3_TTL_SECONDS = "s3_ttl_seconds"
        const val S3_PATH_STYLE = "s3_path_style"
        const val DEFAULT_S3_PATH_PREFIX = "ai-temp"
        const val DEFAULT_S3_TTL_SECONDS = 300
    }
}

data class SecureAiSettings(
    val apiKey: String = "",
    val imageUrlExtraQuery: String = "",
    val s3Endpoint: String = "",
    val s3Region: String = "",
    val s3Bucket: String = "",
    val s3AccessKey: String = "",
    val s3SecretKey: String = "",
    val s3PathPrefix: String = SecureAiSettingsStore.DEFAULT_S3_PATH_PREFIX,
    val s3TtlSeconds: Int = SecureAiSettingsStore.DEFAULT_S3_TTL_SECONDS,
    val s3PathStyle: Boolean = true
) {
    fun hasCompleteS3ImageUrlConfiguration(): Boolean =
        s3Endpoint.isNotBlank() &&
            s3Region.isNotBlank() &&
            s3Bucket.isNotBlank() &&
            s3AccessKey.isNotBlank() &&
            s3SecretKey.isNotBlank()
}
