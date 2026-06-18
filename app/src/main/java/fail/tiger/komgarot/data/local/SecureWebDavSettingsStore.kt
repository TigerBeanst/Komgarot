package fail.tiger.komgarot.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureWebDavSettingsStore(context: Context) {
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

    fun read(): SecureWebDavSettings = SecureWebDavSettings(
        url = prefs.getString(URL, "").orEmpty(),
        username = prefs.getString(USERNAME, "").orEmpty(),
        password = prefs.getString(PASSWORD, "").orEmpty()
    )

    fun save(settings: SecureWebDavSettings) {
        prefs.edit {
            putString(URL, settings.url.trimEnd('/'))
            putString(USERNAME, settings.username)
            putString(PASSWORD, settings.password)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        const val FILE_NAME = "secure_webdav_settings"
        const val URL = "url"
        const val USERNAME = "username"
        const val PASSWORD = "password"
    }
}

data class SecureWebDavSettings(
    val url: String = "",
    val username: String = "",
    val password: String = ""
)
