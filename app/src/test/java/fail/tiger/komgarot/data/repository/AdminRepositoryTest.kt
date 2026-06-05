package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AdminRepositoryTest {
    @Test
    fun librariesAcceptArray() = runBlocking {
        val api = retrofitApi(
            """
            [
              { "id": "library-1", "name": "Comics", "root": "/books" }
            ]
            """.trimIndent()
        )

        val result = AdminRepository(api).getLibraries().getOrThrow()

        assertEquals("library-1", result.single().id)
        assertEquals("Comics", result.single().name)
    }

    @Test
    fun librariesAcceptWrappedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "libraries": [
                { "id": "library-1", "name": "Comics", "root": "/books" }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getLibraries().getOrThrow()

        assertEquals("library-1", result.single().id)
        assertEquals("Comics", result.single().name)
    }

    @Test
    fun usersAcceptPagedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "content": [
                { "id": "user-1", "email": "admin@example.test", "roles": ["ADMIN", "USER"] }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getUsers().getOrThrow()

        assertEquals("user-1", result.single().id)
        assertEquals("admin@example.test", result.single().email)
    }

    @Test
    fun apiKeysAcceptWrappedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "apiKeys": [
                { "id": "key-1", "comment": "phone" }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getApiKeys().getOrThrow()

        assertEquals("key-1", result.single().id)
        assertEquals("phone", result.single().comment)
    }

    @Test
    fun historyAcceptsWrappedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "events": [
                { "id": "event-1", "type": "BOOK_IMPORTED", "timestamp": "2026-06-05T12:00:00Z" }
              ],
              "totalPages": 1,
              "totalElements": 1,
              "number": 0,
              "size": 30
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getHistory().getOrThrow()

        assertEquals("event-1", result.content.single().id)
        assertEquals("BOOK_IMPORTED", result.content.single().type)
    }

    @Test
    fun duplicateBooksAcceptArray() = runBlocking {
        val api = retrofitApi(
            """
            [
              { "id": "book-1", "name": "Duplicate" }
            ]
            """.trimIndent()
        )

        val result = AdminRepository(api).getDuplicateBooks().getOrThrow()

        assertEquals("book-1", result.content.single().id)
        assertEquals("Duplicate", result.content.single().name)
    }

    @Test
    fun knownPageHashesAcceptWrappedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "hashes": [
                { "hash": "abc", "size": 10, "matchCount": 2 }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getKnownPageHashes().getOrThrow()

        assertEquals("abc", result.content.single().hash)
        assertEquals(2, result.content.single().matchCount)
    }

    @Test
    fun unknownPageHashesAcceptWrappedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "content": [
                { "hash": "def", "size": 12, "matchCount": 3 }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getUnknownPageHashes().getOrThrow()

        assertEquals("def", result.content.single().hash)
        assertEquals(3, result.content.single().matchCount)
    }

    @Test
    fun authenticationActivityAcceptsWrappedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "authenticationActivity": [
                { "dateTime": "2026-06-05T12:00:00Z", "success": true, "email": "admin@example.test" }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getAuthenticationActivity().getOrThrow()

        assertEquals("admin@example.test", result.content.single().email)
        assertEquals(true, result.content.single().success)
    }

    @Test
    fun announcementsUseJsonFeedItems() = runBlocking {
        val api = retrofitApi(
            """
            {
              "version": "https://jsonfeed.org/version/1.1",
              "title": "Komga announcements",
              "items": [
                {
                  "id": "announcement-1",
                  "title": "Komga 1.0",
                  "summary": "Release notes",
                  "date_modified": "2026-06-01T12:00:00Z",
                  "_komga": { "read": false }
                }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getAnnouncements().getOrThrow()

        assertEquals(1, result.size)
        assertEquals("announcement-1", result.single().id)
        assertEquals("Komga 1.0", result.single().title)
        assertEquals("Release notes", result.single().message)
        assertEquals("2026-06-01T12:00:00Z", result.single().date)
        assertEquals(false, result.single().read)
    }

    @Test
    fun announcementsAcceptLegacyArray() = runBlocking {
        val api = retrofitApi(
            """
            [
              {
                "id": "announcement-1",
                "title": "Komga 1.0",
                "message": "Release notes",
                "date": "2026-06-01",
                "read": true
              }
            ]
            """.trimIndent()
        )

        val result = AdminRepository(api).getAnnouncements().getOrThrow()

        assertEquals("announcement-1", result.single().id)
        assertEquals("Release notes", result.single().message)
        assertEquals(true, result.single().read)
    }

    @Test
    fun oauthProvidersAcceptWrappedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "providers": [
                { "name": "oidc", "label": "OIDC" }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getOAuthProviders().getOrThrow()

        assertEquals("oidc", result.single().name)
        assertEquals("OIDC", result.single().label)
    }

    @Test
    fun releasesAcceptWrappedObject() = runBlocking {
        val api = retrofitApi(
            """
            {
              "releases": [
                { "version": "1.0.0", "url": "https://example.test/release" }
              ]
            }
            """.trimIndent()
        )

        val result = AdminRepository(api).getReleases().getOrThrow()

        assertEquals("1.0.0", result.single().version)
        assertEquals("https://example.test/release", result.single().url)
    }

    private fun retrofitApi(json: String): KomgaApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("https://komgarot.invalid/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KomgaApi::class.java)
    }
}
