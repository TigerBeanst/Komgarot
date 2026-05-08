package fail.tiger.komgarot.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore("auth")

class AuthPreferences(private val context: Context) {
    private val SERVER_URL = stringPreferencesKey("server_url")
    private val USERNAME = stringPreferencesKey("username")
    private val PASSWORD = stringPreferencesKey("password")
    private val ALWAYS_INCOGNITO = booleanPreferencesKey("always_incognito")
    private val PRELOAD_PAGES = intPreferencesKey("preload_pages")

    val serverUrl: Flow<String> = context.dataStore.data.map { it[SERVER_URL] ?: "" }
    val username: Flow<String> = context.dataStore.data.map { it[USERNAME] ?: "" }
    val alwaysIncognito: Flow<Boolean> = context.dataStore.data.map { it[ALWAYS_INCOGNITO] ?: false }
    val preloadPages: Flow<Int> = context.dataStore.data.map { it[PRELOAD_PAGES] ?: 5 }

    val serverUrlBlocking: String get() = runBlocking { serverUrl.first() }
    val usernameBlocking: String get() = runBlocking { username.first() }
    val passwordBlocking: String get() = runBlocking { context.dataStore.data.map { it[PASSWORD] ?: "" }.first() }
    val alwaysIncognitoBlocking: Boolean get() = runBlocking { alwaysIncognito.first() }

    suspend fun save(url: String, user: String, pass: String) {
        context.dataStore.edit {
            it[SERVER_URL] = url.trimEnd('/')
            it[USERNAME] = user
            it[PASSWORD] = pass
        }
    }

    suspend fun setAlwaysIncognito(value: Boolean) {
        context.dataStore.edit { it[ALWAYS_INCOGNITO] = value }
    }

    suspend fun setPreloadPages(value: Int) {
        context.dataStore.edit { it[PRELOAD_PAGES] = value }
    }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
