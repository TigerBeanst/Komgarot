package fail.tiger.komgarot

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.AiTranslationStore
import fail.tiger.komgarot.data.local.CacheMaintenance
import fail.tiger.komgarot.data.local.SecureAiSettingsStore
import fail.tiger.komgarot.data.local.SecureWebDavSettingsStore
import fail.tiger.komgarot.data.remote.AuthInterceptor
import fail.tiger.komgarot.data.remote.AiTranslationClient
import fail.tiger.komgarot.data.remote.ImageDownloadProgressInterceptor
import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.ReaderPageCacheInterceptor
import fail.tiger.komgarot.data.remote.UrlInterceptor
import fail.tiger.komgarot.data.repository.AdminRepository
import fail.tiger.komgarot.data.repository.AppUpdateRepository
import fail.tiger.komgarot.data.repository.AiLocalModelRepository
import fail.tiger.komgarot.data.repository.AiPaddleTextDetector
import fail.tiger.komgarot.data.repository.AiLocalTextDetector
import fail.tiger.komgarot.data.repository.AiTranslationQueueRunner
import fail.tiger.komgarot.data.repository.AiTranslationRepository
import fail.tiger.komgarot.data.repository.AuthRepository
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.CollectionRepository
import fail.tiger.komgarot.data.repository.LibraryRepository
import fail.tiger.komgarot.data.repository.ReadListRepository
import fail.tiger.komgarot.data.repository.SeriesRepository
import fail.tiger.komgarot.data.repository.UserRepository
import fail.tiger.komgarot.data.repository.WebDavBackupRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val RETROFIT_PLACEHOLDER_BASE_URL = "https://komgarot.invalid/"

class KomgarotApp : Application(), ImageLoaderFactory {
    lateinit var authPreferences: AuthPreferences
    lateinit var secureAiSettingsStore: SecureAiSettingsStore
    lateinit var secureWebDavSettingsStore: SecureWebDavSettingsStore
    lateinit var aiTranslationStore: AiTranslationStore
    lateinit var okHttpClient: OkHttpClient
    lateinit var aiTranslationClient: AiTranslationClient
    lateinit var authRepository: AuthRepository
    lateinit var libraryRepository: LibraryRepository
    lateinit var seriesRepository: SeriesRepository
    lateinit var bookRepository: BookRepository
    lateinit var userRepository: UserRepository
    lateinit var collectionRepository: CollectionRepository
    lateinit var readListRepository: ReadListRepository
    lateinit var adminRepository: AdminRepository
    lateinit var appUpdateRepository: AppUpdateRepository
    var aiTranslationRepositoryOrNull: AiTranslationRepository? = null
        private set
    val aiTranslationRepository: AiTranslationRepository?
        get() = aiTranslationRepositoryOrNull
    lateinit var aiLocalModelRepository: AiLocalModelRepository
    var aiTranslationQueueRunner: AiTranslationQueueRunner? = null
        private set
    lateinit var webDavBackupRepository: WebDavBackupRepository

    override fun onCreate() {
        super.onCreate()
        DebugPerformanceDiagnostics.install()
        authPreferences = AuthPreferences(this)
        secureAiSettingsStore = SecureAiSettingsStore(this)
        secureWebDavSettingsStore = SecureWebDavSettingsStore(this)
        aiTranslationStore = AiTranslationStore(filesDir)
        CacheMaintenance.clearOnStartupIfNeeded(this, authPreferences)
        val authInterceptor = AuthInterceptor(authPreferences)
        val urlInterceptor = UrlInterceptor(authPreferences)
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(urlInterceptor)
            .addInterceptor(authInterceptor)
            .addNetworkInterceptor(ReaderPageCacheInterceptor(this) { authPreferences.readerCacheSizeBytesBlocking })
            .addNetworkInterceptor(ImageDownloadProgressInterceptor())
            .build()
        aiTranslationClient = AiTranslationClient(OkHttpClient())
        val retrofit = Retrofit.Builder()
            .baseUrl(RETROFIT_PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(KomgaApi::class.java)
        authRepository = AuthRepository(authPreferences)
        libraryRepository = LibraryRepository(api)
        seriesRepository = SeriesRepository(api)
        bookRepository = BookRepository(api)
        userRepository = UserRepository(api)
        collectionRepository = CollectionRepository(api)
        readListRepository = ReadListRepository(api)
        adminRepository = AdminRepository(api)
        appUpdateRepository = AppUpdateRepository()
        aiLocalModelRepository = AiLocalModelRepository(filesDir)
        if (BuildConfig.AI_TRANSLATION_AVAILABLE) {
            aiTranslationRepositoryOrNull = AiTranslationRepository(
                context = applicationContext,
                bookRepository = bookRepository,
                prefs = authPreferences,
                secureAiSettingsStore = secureAiSettingsStore,
                store = aiTranslationStore,
                komgaHttpClient = okHttpClient,
                localTextDetector = AiLocalTextDetector(
                    paddleTextDetector = AiPaddleTextDetector(applicationContext, aiLocalModelRepository)
                ),
                aiClient = aiTranslationClient
            )
            aiTranslationQueueRunner = AiTranslationQueueRunner(
                repository = requireNotNull(aiTranslationRepositoryOrNull),
                store = aiTranslationStore,
                prefs = authPreferences
            )
        }
        webDavBackupRepository = WebDavBackupRepository(
            prefs = authPreferences,
            secureAiSettingsStore = secureAiSettingsStore,
            secureWebDavSettingsStore = secureWebDavSettingsStore,
            aiTranslationStore = aiTranslationStore
        )
        aiTranslationQueueRunner?.restoreRunningTasks()
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .respectCacheHeaders(false)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.12)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(authPreferences.coverCacheSizeBytesBlocking)
                .build()
        }
        .build()
}
