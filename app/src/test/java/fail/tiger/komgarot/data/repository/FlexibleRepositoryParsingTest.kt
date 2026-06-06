package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.LibraryDto
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import java.lang.reflect.ParameterizedType
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FlexibleRepositoryParsingTest {
    @Test
    fun repositoryListTypeKeepsConcreteDtoArgument() {
        val type = repositoryListType<LibraryDto>() as ParameterizedType

        assertEquals(List::class.java, type.rawType)
        assertEquals(LibraryDto::class.java, type.actualTypeArguments.single())
    }

    @Test
    fun libraryRepositoryAcceptsWrappedLibraries() = runBlocking {
        val api = retrofitApi(
            """
            {
              "libraries": [
                { "id": "library-1", "name": "Comics", "root": "/books" }
              ]
            }
            """.trimIndent()
        )

        val result = LibraryRepository(api).getLibraries()

        assertEquals("library-1", result.single().id)
        assertEquals("Comics", result.single().name)
    }

    @Test
    fun userRepositoryAcceptsWrappedApiKeys() = runBlocking {
        val api = retrofitApi(
            """
            {
              "apiKeys": [
                { "id": "key-1", "comment": "phone" }
              ]
            }
            """.trimIndent()
        )

        val result = UserRepository(api).getApiKeys().getOrThrow()

        assertEquals("key-1", result.single().id)
        assertEquals("phone", result.single().comment)
    }

    @Test
    fun userRepositoryAcceptsWrappedAuthenticationActivity() = runBlocking {
        val api = retrofitApi(
            """
            {
              "authenticationActivity": [
                { "dateTime": "2026-06-05T12:00:00Z", "success": true, "email": "admin@example.test" }
              ]
            }
            """.trimIndent()
        )

        val result = UserRepository(api).getMyAuthenticationActivity().getOrThrow()

        assertEquals("admin@example.test", result.content.single().email)
        assertEquals(true, result.content.single().success)
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
