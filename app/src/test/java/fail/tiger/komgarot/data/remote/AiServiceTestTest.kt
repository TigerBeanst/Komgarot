package fail.tiger.komgarot.data.remote

import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServiceTestTest {
    @Test
    fun probeRequestUsesMinimalTextInputAndBoundedOutput() {
        val root = JsonParser.parseString(buildAiServiceTestRequestJson("test-model")).asJsonObject
        val messages = root.getAsJsonArray("messages")

        assertEquals("test-model", root.get("model").asString)
        assertEquals(1, root.get("max_tokens").asInt)
        assertEquals(1, messages.size())
        assertEquals("user", messages[0].asJsonObject.get("role").asString)
        assertEquals("Reply with OK.", messages[0].asJsonObject.get("content").asString)
        assertTrue(!root.has("response_format"))
        assertTrue(!root.toString().contains("image_url"))
    }

    @Test
    fun httpFailureKeepsStatusAndResponseBody() = runBlocking {
        val responseBody = """{"error":{"message":"invalid credential"}}"""
        val client = AiTranslationClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body(responseBody.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        )

        val result = client.testService(
            baseUrl = "https://service.invalid/v1",
            apiKey = "secret",
            model = "test-model",
            timeoutSeconds = 5
        )

        assertTrue(result is AiServiceTestResult.Failure)
        val detail = (result as AiServiceTestResult.Failure).detail
        assertTrue(detail.contains("HTTP 401 Unauthorized"))
        assertTrue(detail.contains(responseBody))
        assertTrue(!detail.contains("secret"))
    }

    @Test
    fun successfulHttpResponseReturnsLatencyAndRawBodyWithoutContentValidation() = runBlocking {
        val responseBody = """{"choices":[{"message":{"content":"any response"}}]}"""
        val client = AiTranslationClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(responseBody.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        )

        val result = client.testService(
            baseUrl = "https://service.invalid/v1",
            apiKey = "secret",
            model = "test-model",
            timeoutSeconds = 5
        )

        assertTrue(result is AiServiceTestResult.Success)
        val success = result as AiServiceTestResult.Success
        assertEquals(responseBody, success.responseBody)
        assertTrue(success.latencyMs >= 0)
    }

    @Test
    fun networkFailureShowsConcreteReasonWithoutApiKey() = runBlocking {
        val client = AiTranslationClient(
            OkHttpClient.Builder()
                .addInterceptor { throw IOException("network unreachable") }
                .build()
        )

        val result = client.testService(
            baseUrl = "https://service.invalid/v1",
            apiKey = "secret",
            model = "test-model",
            timeoutSeconds = 5
        )

        assertTrue(result is AiServiceTestResult.Failure)
        val detail = (result as AiServiceTestResult.Failure).detail
        assertTrue(detail.contains("IOException"))
        assertTrue(detail.contains("network unreachable"))
        assertTrue(!detail.contains("secret"))
    }
}
