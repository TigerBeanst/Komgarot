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
    fun aiHttpClientAllowsHighConcurrencyPerHost() {
        val client = aiTranslationHttpClient()

        assertEquals(64, client.dispatcher.maxRequests)
        assertEquals(32, client.dispatcher.maxRequestsPerHost)
    }

    @Test
    fun probeRequestChecksVisionInputAndStructuredOutput() {
        val root = JsonParser.parseString(buildAiServiceTestRequestJson("test-model")).asJsonObject
        val messages = root.getAsJsonArray("messages")
        val content = messages[0].asJsonObject.getAsJsonArray("content")

        assertEquals("test-model", root.get("model").asString)
        assertEquals(16, root.get("max_tokens").asInt)
        assertEquals(1, messages.size())
        assertEquals("user", messages[0].asJsonObject.get("role").asString)
        assertEquals("text", content[0].asJsonObject.get("type").asString)
        assertEquals("image_url", content[1].asJsonObject.get("type").asString)
        assertTrue(content[1].asJsonObject.getAsJsonObject("image_url").get("url").asString.startsWith("data:image/png;base64,"))
        assertEquals("json_object", root.getAsJsonObject("response_format").get("type").asString)
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
        assertEquals(AiTranslationErrorCategory.AUTHENTICATION, result.category)
        assertEquals(401, result.httpStatusCode)
    }

    @Test
    fun successfulHttpResponseReturnsLatencyAfterStructuredContentValidation() = runBlocking {
        val responseBody = """{"choices":[{"message":{"content":"{\"ok\":true}"}}]}"""
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
    fun serviceProbeClassifiesPlainTextAsResponseContractFailure() = runBlocking {
        val responseBody = """{"choices":[{"message":{"content":"plain text"}}]}"""
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
        ) as AiServiceTestResult.Failure

        assertEquals(AiTranslationErrorCategory.NON_JSON_RESPONSE, result.category)
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

    @Test
    fun translationSuccessKeepsUsageCounters() = runBlocking {
        val responseBody = """{"choices":[{"message":{"content":"{\"pages\":[]}"}}],"usage":{"prompt_tokens":120,"completion_tokens":35,"total_tokens":155}}"""
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

        val result = client.translate(
            baseUrl = "https://service.invalid/v1",
            apiKey = "secret",
            model = "test-model",
            systemPrompt = "system",
            userPrompt = "user",
            images = emptyList(),
            timeoutSeconds = 5
        )

        assertTrue(result is AiTranslationRequestResult.Success)
        val success = result as AiTranslationRequestResult.Success
        assertEquals(120L, success.usage.promptTokens)
        assertEquals(35L, success.usage.completionTokens)
        assertEquals(155L, success.usage.totalTokens)
    }

    @Test
    fun rateLimitFailureKeepsStatusAndRetryAfter() = runBlocking {
        val client = AiTranslationClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .header("Retry-After", "3")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        )

        val result = client.translate(
            baseUrl = "https://service.invalid/v1",
            apiKey = "secret",
            model = "test-model",
            systemPrompt = "system",
            userPrompt = "user",
            images = emptyList(),
            timeoutSeconds = 5
        ) as AiTranslationRequestResult.Failure

        assertEquals(AiTranslationErrorCategory.RATE_LIMITED, result.category)
        assertEquals(429, result.httpStatusCode)
        assertEquals(3_000L, result.retryAfterMs)
    }

    @Test
    fun httpErrorsUseActionableCategories() {
        assertEquals(AiTranslationErrorCategory.AUTHENTICATION, aiHttpErrorCategory(401, "{}"))
        assertEquals(AiTranslationErrorCategory.MODEL_CONFIGURATION, aiHttpErrorCategory(404, "model missing"))
        assertEquals(AiTranslationErrorCategory.VISION_UNSUPPORTED, aiHttpErrorCategory(400, "vision input unsupported"))
        assertEquals(AiTranslationErrorCategory.VISION_UNSUPPORTED, aiHttpErrorCategory(403, "image input unsupported"))
        assertEquals(AiTranslationErrorCategory.SERVER_TEMPORARY, aiHttpErrorCategory(503, "busy"))
    }

    @Test
    fun retryAfterSupportsSecondsAndHttpDate() {
        assertEquals(2_000L, parseAiRetryAfterMillis("2", nowEpochMs = 0L))
        assertEquals(
            1_000L,
            parseAiRetryAfterMillis("Thu, 01 Jan 1970 00:00:01 GMT", nowEpochMs = 0L)
        )
    }

    @Test
    fun invalidTranslationBaseUrlReturnsConfigurationFailure() = runBlocking {
        val result = AiTranslationClient().translate(
            baseUrl = "invalid-base-url",
            apiKey = "secret",
            model = "test-model",
            systemPrompt = "system",
            userPrompt = "user",
            images = emptyList(),
            timeoutSeconds = 5
        )

        assertTrue(result is AiTranslationRequestResult.Failure)
        assertEquals(
            AiTranslationErrorCategory.MODEL_CONFIGURATION,
            (result as AiTranslationRequestResult.Failure).category
        )
    }
}
