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
        imageUrlExtraQuery = prefs.getString(IMAGE_URL_EXTRA_QUERY, "").orEmpty()
    )

    fun saveApiKey(value: String) {
        prefs.edit { putString(API_KEY, value.trim()) }
    }

    fun saveImageUrlExtraQuery(value: String) {
        prefs.edit { putString(IMAGE_URL_EXTRA_QUERY, value.trim()) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        const val FILE_NAME = "secure_ai_settings"
        const val API_KEY = "api_key"
        const val IMAGE_URL_EXTRA_QUERY = "image_url_extra_query"
    }
}

data class SecureAiSettings(
    val apiKey: String = "",
    val imageUrlExtraQuery: String = ""
)
