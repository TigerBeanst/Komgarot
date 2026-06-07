package fail.tiger.komgarot

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.local.CacheMaintenance
import fail.tiger.komgarot.data.remote.AuthInterceptor
import fail.tiger.komgarot.data.remote.ImageDownloadProgressInterceptor
import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.ReaderPageCacheInterceptor
import fail.tiger.komgarot.data.remote.UrlInterceptor
import fail.tiger.komgarot.data.repository.AdminRepository
import fail.tiger.komgarot.data.repository.AuthRepository
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.CollectionRepository
import fail.tiger.komgarot.data.repository.LibraryRepository
import fail.tiger.komgarot.data.repository.ReadListRepository
import fail.tiger.komgarot.data.repository.SeriesRepository
import fail.tiger.komgarot.data.repository.UserRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val RETROFIT_PLACEHOLDER_BASE_URL = "https://komgarot.invalid/"

class KomgarotApp : Application(), ImageLoaderFactory {
    lateinit var authPreferences: AuthPreferences
    lateinit var okHttpClient: OkHttpClient
    lateinit var authRepository: AuthRepository
    lateinit var libraryRepository: LibraryRepository
    lateinit var seriesRepository: SeriesRepository
    lateinit var bookRepository: BookRepository
    lateinit var userRepository: UserRepository
    lateinit var collectionRepository: CollectionRepository
    lateinit var readListRepository: ReadListRepository
    lateinit var adminRepository: AdminRepository

    override fun onCreate() {
        super.onCreate()
        authPreferences = AuthPreferences(this)
        CacheMaintenance.clearOnStartupIfNeeded(this, authPreferences)
        val authInterceptor = AuthInterceptor(authPreferences)
        val urlInterceptor = UrlInterceptor(authPreferences)
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(urlInterceptor)
            .addInterceptor(authInterceptor)
            .addNetworkInterceptor(ReaderPageCacheInterceptor(this) { authPreferences.readerCacheSizeBytesBlocking })
            .addNetworkInterceptor(ImageDownloadProgressInterceptor())
            .build()
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
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .respectCacheHeaders(false)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
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
