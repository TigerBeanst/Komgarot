package fail.tiger.komgarot.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal class SecureAuthStore(context: Context) {
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

    fun read(): AuthCredentials = AuthCredentials(
        serverUrl = prefs.getString(SERVER_URL, "").orEmpty(),
        username = prefs.getString(USERNAME, "").orEmpty(),
        password = prefs.getString(PASSWORD, "").orEmpty()
    )

    fun save(serverUrl: String, username: String, password: String) {
        prefs.edit {
            putString(SERVER_URL, serverUrl)
            putString(USERNAME, username)
            putString(PASSWORD, password)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    internal companion object {
        const val FILE_NAME = "secure_auth"
        private const val SERVER_URL = "server_url"
        private const val USERNAME = "username"
        private const val PASSWORD = "password"
    }
}

internal data class AuthCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)
