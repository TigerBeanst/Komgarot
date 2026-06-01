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
    private val READING_DIRECTION = stringPreferencesKey("reading_direction")
    private val PAGE_FIT = stringPreferencesKey("page_fit")
    private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

    @Volatile private var cachedServerUrl = ""
    @Volatile private var cachedUsername = ""
    @Volatile private var cachedPassword = ""
    private val secureAuthStore = SecureAuthStore(context)

    val serverUrl: Flow<String> = context.dataStore.data.map { cachedServerUrl }
    val username: Flow<String> = context.dataStore.data.map { cachedUsername }
    val alwaysIncognito: Flow<Boolean> = context.dataStore.data.map { it[ALWAYS_INCOGNITO] ?: false }
    val preloadPages: Flow<Int> = context.dataStore.data.map { it[PRELOAD_PAGES] ?: 5 }
    val readingDirection: Flow<String> = context.dataStore.data.map { it[READING_DIRECTION] ?: "LTR" }
    val pageFit: Flow<String> = context.dataStore.data.map { it[PAGE_FIT] ?: "FIT" }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[KEEP_SCREEN_ON] ?: true }

    init {
        runBlocking {
            val data = context.dataStore.data.first()
            if (data.contains(SERVER_URL) || data.contains(USERNAME) || data.contains(PASSWORD)) {
                context.dataStore.edit {
                    it.remove(SERVER_URL)
                    it.remove(USERNAME)
                    it.remove(PASSWORD)
                }
                secureAuthStore.clear()
            }
            val credentials = secureAuthStore.read()
            cachedServerUrl = credentials.serverUrl
            cachedUsername = credentials.username
            cachedPassword = credentials.password
        }
    }

    val serverUrlBlocking: String get() = cachedServerUrl
    val usernameBlocking: String get() = cachedUsername
    val passwordBlocking: String get() = cachedPassword
    val alwaysIncognitoBlocking: Boolean get() = runBlocking { alwaysIncognito.first() }

    suspend fun save(url: String, user: String, pass: String) {
        val cleanUrl = url.trimEnd('/')
        context.dataStore.edit {
            it.remove(SERVER_URL)
            it.remove(USERNAME)
            it.remove(PASSWORD)
        }
        secureAuthStore.save(cleanUrl, user, pass)
        cachedServerUrl = cleanUrl
        cachedUsername = user
        cachedPassword = pass
    }

    suspend fun setAlwaysIncognito(value: Boolean) {
        context.dataStore.edit { it[ALWAYS_INCOGNITO] = value }
    }

    suspend fun setPreloadPages(value: Int) {
        context.dataStore.edit { it[PRELOAD_PAGES] = value }
    }

    suspend fun setReadingDirection(value: String) {
        context.dataStore.edit { it[READING_DIRECTION] = value }
    }

    suspend fun setPageFit(value: String) {
        context.dataStore.edit { it[PAGE_FIT] = value }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[KEEP_SCREEN_ON] = value }
    }

    private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    private val APP_LOCK_TIMEOUT = intPreferencesKey("app_lock_timeout_minutes")

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[APP_LOCK_ENABLED] ?: false }
    val appLockTimeout: Flow<Int> = context.dataStore.data.map { it[APP_LOCK_TIMEOUT] ?: 0 }
    val appLockEnabledBlocking: Boolean get() = runBlocking { appLockEnabled.first() }
    val appLockTimeoutBlocking: Int get() = runBlocking { appLockTimeout.first() }

    suspend fun setAppLockEnabled(value: Boolean) {
        context.dataStore.edit { it[APP_LOCK_ENABLED] = value }
    }

    suspend fun setAppLockTimeout(minutes: Int) {
        context.dataStore.edit { it[APP_LOCK_TIMEOUT] = minutes }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
        secureAuthStore.clear()
        cachedServerUrl = ""
        cachedUsername = ""
        cachedPassword = ""
    }
}
