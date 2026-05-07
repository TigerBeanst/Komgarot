package fail.tiger.komgarot

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import fail.tiger.komgarot.data.local.AuthPreferences
import fail.tiger.komgarot.data.remote.AuthInterceptor
import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.UrlInterceptor
import fail.tiger.komgarot.data.repository.AuthRepository
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.data.repository.LibraryRepository
import fail.tiger.komgarot.data.repository.SeriesRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class KomgarotApp : Application(), ImageLoaderFactory {
    lateinit var authPreferences: AuthPreferences
    lateinit var okHttpClient: OkHttpClient
    lateinit var authRepository: AuthRepository
    lateinit var libraryRepository: LibraryRepository
    lateinit var seriesRepository: SeriesRepository
    lateinit var bookRepository: BookRepository

    override fun onCreate() {
        super.onCreate()
        authPreferences = AuthPreferences(this)
        val authInterceptor = AuthInterceptor(authPreferences)
        val urlInterceptor = UrlInterceptor(authPreferences)
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(urlInterceptor)
            .addInterceptor(authInterceptor)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(KomgaApi::class.java)
        authRepository = AuthRepository(authPreferences)
        libraryRepository = LibraryRepository(api)
        seriesRepository = SeriesRepository(api)
        bookRepository = BookRepository(api)

        clearOldCache()
    }

    private fun clearOldCache() {
        val diskCache = cacheDir.resolve("image_cache")
        if (diskCache.exists()) {
            val maxSize = 500L * 1024 * 1024
            val currentSize = diskCache.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            if (currentSize > maxSize) {
                diskCache.deleteRecursively()
            }
        }
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(500L * 1024 * 1024)
                .build()
        }
        .build()
}
