package fail.tiger.komgarot.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import fail.tiger.komgarot.data.local.AiImageTransport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class AiTranslationImageInput(
    val pageIndex: Int,
    val transport: AiImageTransport,
    val mimeType: String,
    val base64: String,
    val imageUrl: String,
    val localRegionId: String = "",
    val fallbackBase64: String = base64
) {
    fun toOpenAiImageUrl(): String =
        when (transport) {
            AiImageTransport.BASE64 -> "data:$mimeType;base64,$base64"
            AiImageTransport.IMAGE_URL -> imageUrl
        }

    fun asBase64Fallback(): AiTranslationImageInput =
        copy(
            transport = AiImageTransport.BASE64,
            base64 = base64.ifBlank { fallbackBase64 },
            imageUrl = ""
        )

    fun metadataText(): String = if (localRegionId.isBlank()) {
        "Image 1: context; page=$pageIndex"
    } else {
        "Image 2: crop; page=$pageIndex"
    }
}

sealed interface AiTranslationRequestResult {
    data class Success(
        val normalizedJson: String,
        val usage: AiTranslationUsage = AiTranslationUsage()
    ) : AiTranslationRequestResult

    data class Failure(
        val category: AiTranslationErrorCategory,
        val summary: String,
        val httpStatusCode: Int? = null,
        val retryAfterMs: Long? = null
    ) : AiTranslationRequestResult
}

data class AiTranslationUsage(
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val totalTokens: Long = 0L
) {
    operator fun plus(other: AiTranslationUsage): AiTranslationUsage = AiTranslationUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        totalTokens = totalTokens + other.totalTokens
    )
}

sealed interface AiServiceTestResult {
    data class Success(
        val responseBody: String,
        val latencyMs: Long
    ) : AiServiceTestResult

    data class Failure(
        val detail: String,
        val category: AiTranslationErrorCategory = AiTranslationErrorCategory.NETWORK_OR_API,
        val httpStatusCode: Int? = null
    ) : AiServiceTestResult
}

enum class AiTranslationErrorCategory {
    NETWORK_OR_API,
    AUTHENTICATION,
    MODEL_CONFIGURATION,
    RATE_LIMITED,
    SERVER_TEMPORARY,
    VISION_UNSUPPORTED,
    NON_JSON_RESPONSE,
    JSON_VALIDATION_FAILED
}

class AiTranslationClient(httpClient: OkHttpClient = OkHttpClient()) {
    private val httpClient = aiTranslationHttpClient(httpClient)

    suspend fun translate(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiTranslationImageInput>,
        timeoutSeconds: Int = 30
    ): AiTranslationRequestResult {
        val responseTimeout = aiResponseTimeoutSeconds(timeoutSeconds)
        val writeTimeout = aiWriteTimeoutSeconds(responseTimeout)
        val call = try {
            val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
            val body = buildAiTranslationChatRequestJson(model, systemPrompt, userPrompt, images)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            httpClient.newBuilder()
                .connectTimeout(AI_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
                .readTimeout(responseTimeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(writeTimeout.toLong(), TimeUnit.SECONDS)
                .build()
                .newCall(request)
        } catch (throwable: IllegalArgumentException) {
            return AiTranslationRequestResult.Failure(
                category = AiTranslationErrorCategory.MODEL_CONFIGURATION,
                summary = throwable.message.orEmpty().ifBlank { "AI service URL is invalid." }
            )
        }

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(failureResult(e, responseTimeout))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { handledResponse ->
                            aiTranslationResultFromResponse(handledResponse)
                        }
                    }.getOrElse { throwable ->
                        failureResult(throwable, responseTimeout)
                    }
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            })
        }
    }

    suspend fun testService(
        baseUrl: String,
        apiKey: String,
        model: String,
        timeoutSeconds: Int = 30
    ): AiServiceTestResult {
        val startedAt = System.nanoTime()
        val responseTimeout = aiResponseTimeoutSeconds(timeoutSeconds)
        val result = runCatching {
            val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
            val body = buildAiServiceTestRequestJson(model)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            val call = httpClient.newBuilder()
                .connectTimeout(AI_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
                .readTimeout(responseTimeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(aiWriteTimeoutSeconds(responseTimeout).toLong(), TimeUnit.SECONDS)
                .build()
                .newCall(request)

            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resume(
                                AiServiceTestResult.Failure(
                                    detail = aiServiceTestExceptionDetail(e, responseTimeout),
                                    category = AiTranslationErrorCategory.NETWORK_OR_API
                                )
                            )
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000L
                        val responseResult = runCatching {
                            response.use { handledResponse ->
                                aiServiceTestResultFromResponse(handledResponse, latencyMs)
                            }
                        }.getOrElse { throwable ->
                            AiServiceTestResult.Failure(aiServiceTestExceptionDetail(throwable, responseTimeout))
                        }
                        if (continuation.isActive) continuation.resume(responseResult)
                    }
                })
            }
        }
        return result.getOrElse { throwable ->
            AiServiceTestResult.Failure(
                detail = aiServiceTestExceptionDetail(throwable, responseTimeout),
                category = if (throwable is IllegalArgumentException) {
                    AiTranslationErrorCategory.MODEL_CONFIGURATION
                } else {
                    AiTranslationErrorCategory.NETWORK_OR_API
                }
            )
        }
    }

    private fun aiTranslationResultFromResponse(response: Response): AiTranslationRequestResult {
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            return AiTranslationRequestResult.Failure(
                category = aiHttpErrorCategory(response.code, responseBody),
                summary = buildAiHttpFailureSummary(response.code, response.message, responseBody),
                httpStatusCode = response.code,
                retryAfterMs = parseAiRetryAfterMillis(response.header("Retry-After"))
            )
        }
        val json = extractAiTranslationJsonContent(responseBody)
        return AiTranslationRequestResult.Success(
            normalizedJson = json,
            usage = parseAiTranslationUsage(responseBody)
        )
    }

    private fun aiServiceTestResultFromResponse(
        response: Response,
        latencyMs: Long
    ): AiServiceTestResult {
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            return AiServiceTestResult.Failure(
                detail = buildAiServiceTestHttpFailureDetail(response.code, response.message, responseBody),
                category = aiHttpErrorCategory(response.code, responseBody),
                httpStatusCode = response.code
            )
        }
        val structuredContent = runCatching { extractAiTranslationJsonContent(responseBody) }
            .getOrElse { throwable ->
                return AiServiceTestResult.Failure(
                    detail = aiTranslationFailureSummary(throwable, 0),
                    category = AiTranslationErrorCategory.NON_JSON_RESPONSE,
                    httpStatusCode = response.code
                )
            }
        val structuredObject = runCatching { aiClientGson.fromJson(structuredContent, JsonObject::class.java) }.getOrNull()
        if (structuredObject == null) {
            return AiServiceTestResult.Failure(
                detail = "AI service returned an invalid structured response.",
                category = AiTranslationErrorCategory.JSON_VALIDATION_FAILED,
                httpStatusCode = response.code
            )
        }
        return AiServiceTestResult.Success(responseBody = responseBody, latencyMs = latencyMs)
    }

    private fun failureResult(throwable: Throwable, responseTimeout: Int): AiTranslationRequestResult =
        AiTranslationRequestResult.Failure(
            category = if (throwable is IllegalArgumentException) {
                AiTranslationErrorCategory.NON_JSON_RESPONSE
            } else {
                AiTranslationErrorCategory.NETWORK_OR_API
            },
            summary = aiTranslationFailureSummary(throwable, responseTimeout)
        )
}

internal fun aiTranslationHttpClient(baseClient: OkHttpClient = OkHttpClient()): OkHttpClient {
    val dispatcher = Dispatcher().apply {
        maxRequests = AI_HTTP_MAX_REQUESTS
        maxRequestsPerHost = AI_HTTP_MAX_REQUESTS_PER_HOST
    }
    return baseClient.newBuilder()
        .dispatcher(dispatcher)
        .build()
}

internal const val AI_HTTP_MAX_REQUESTS = 64
internal const val AI_HTTP_MAX_REQUESTS_PER_HOST = 32

internal fun aiResponseTimeoutSeconds(timeoutSeconds: Int): Int = timeoutSeconds.coerceAtLeast(0)

private fun aiWriteTimeoutSeconds(responseTimeoutSeconds: Int): Int =
    if (responseTimeoutSeconds == 0) 0 else maxOf(responseTimeoutSeconds, AI_WRITE_TIMEOUT_SECONDS)

private fun aiTranslationFailureSummary(throwable: Throwable, responseTimeoutSeconds: Int): String {
    if (throwable is SocketTimeoutException) {
        return if (responseTimeoutSeconds == 0) {
            "AI request timed out while waiting for network I/O."
        } else {
            "AI request timed out after ${responseTimeoutSeconds}s. Increase AI timeout or set it to 0 to wait without a response timeout."
        }
    }
    return throwable.message?.takeIf { it.isNotBlank() } ?: IOException::class.java.simpleName
}

fun buildAiHttpFailureSummary(statusCode: Int, statusMessage: String, responseBody: String): String {
    val status = "HTTP $statusCode $statusMessage".trim()
    val body = responseBody.trim().take(240)
    return if (body.isBlank()) status else "$status: $body"
}

internal fun aiHttpErrorCategory(statusCode: Int, responseBody: String): AiTranslationErrorCategory {
    val detail = responseBody.lowercase(Locale.ROOT)
    return when {
        (statusCode == 400 || statusCode == 403 || statusCode == 404) && detail.containsAnyAiVisionErrorMarker() ->
            AiTranslationErrorCategory.VISION_UNSUPPORTED
        statusCode == 401 || statusCode == 403 -> AiTranslationErrorCategory.AUTHENTICATION
        statusCode == 429 -> AiTranslationErrorCategory.RATE_LIMITED
        statusCode == 408 || statusCode == 425 || statusCode in 500..599 -> AiTranslationErrorCategory.SERVER_TEMPORARY
        statusCode == 400 || statusCode == 404 || statusCode == 422 -> AiTranslationErrorCategory.MODEL_CONFIGURATION
        else -> AiTranslationErrorCategory.NETWORK_OR_API
    }
}

private fun String.containsAnyAiVisionErrorMarker(): Boolean =
    contains("invalid_image") ||
        contains("image_url") ||
        contains("vision") ||
        contains("multimodal") ||
        contains("image input") ||
        contains("image content")

internal fun parseAiRetryAfterMillis(
    value: String?,
    nowEpochMs: Long = System.currentTimeMillis()
): Long? {
    val clean = value.orEmpty().trim()
    if (clean.isEmpty()) return null
    clean.toLongOrNull()?.let { seconds ->
        return seconds.coerceAtLeast(0L).coerceAtMost(AI_MAX_RETRY_AFTER_SECONDS) * 1000L
    }
    val parsedDate = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("GMT")
        }.parse(clean)
    }.getOrNull() ?: return null
    return (parsedDate.time - nowEpochMs)
        .coerceAtLeast(0L)
        .coerceAtMost(AI_MAX_RETRY_AFTER_SECONDS * 1000L)
}

internal fun parseAiTranslationUsage(responseJson: String): AiTranslationUsage {
    val usage = runCatching {
        aiClientGson.fromJson(responseJson, JsonObject::class.java)
            ?.getAsJsonObject("usage")
    }.getOrNull() ?: return AiTranslationUsage()
    return AiTranslationUsage(
        promptTokens = usage.longOrZero("prompt_tokens"),
        completionTokens = usage.longOrZero("completion_tokens"),
        totalTokens = usage.longOrZero("total_tokens")
    )
}

private fun JsonObject.longOrZero(name: String): Long =
    runCatching { get(name)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull() ?: 0L

fun buildAiServiceTestRequestJson(model: String): String {
    val root = JsonObject().apply {
        addProperty("model", model)
        add("messages", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "user")
                add("content", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "Inspect the image and return a JSON object with boolean field ok.")
                    })
                    add(JsonObject().apply {
                        addProperty("type", "image_url")
                        add("image_url", JsonObject().apply {
                            addProperty("url", AI_SERVICE_TEST_IMAGE_DATA_URL)
                        })
                    })
                })
            })
        })
        addProperty("max_tokens", 16)
        add("response_format", JsonObject().apply {
            addProperty("type", "json_object")
        })
    }
    return aiClientGson.toJson(root)
}

fun buildAiServiceTestHttpFailureDetail(statusCode: Int, statusMessage: String, responseBody: String): String {
    val status = "HTTP $statusCode $statusMessage".trim()
    val body = responseBody.trim().take(AI_SERVICE_TEST_MAX_BODY_CHARS)
    return if (body.isBlank()) status else "$status\n\n$body"
}

private fun aiServiceTestExceptionDetail(throwable: Throwable, responseTimeoutSeconds: Int): String {
    val type = throwable::class.java.simpleName
    return "$type: ${aiTranslationFailureSummary(throwable, responseTimeoutSeconds)}"
}

fun buildAiTranslationChatRequestJson(
    model: String,
    systemPrompt: String,
    userPrompt: String,
    images: List<AiTranslationImageInput>
): String {
    val root = JsonObject().apply {
        addProperty("model", model)
        add("messages", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemPrompt)
            })
            add(JsonObject().apply {
                addProperty("role", "user")
                add("content", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", userPrompt)
                    })
                    images.forEach { image ->
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", image.metadataText())
                        })
                        val imageUrl = image.toOpenAiImageUrl()
                        add(JsonObject().apply {
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply {
                                addProperty("url", imageUrl)
                            })
                        })
                    }
                })
            })
        })
        add("response_format", JsonObject().apply {
            addProperty("type", "json_object")
        })
    }
    return aiClientGson.toJson(root)
}

fun extractAiTranslationJsonContent(responseJson: String): String {
    val root = runCatching { aiClientGson.fromJson(responseJson, JsonObject::class.java) }.getOrNull()
        ?: throw IllegalArgumentException("AI response is not JSON")
    val message = root.getAsJsonArray("choices")
        ?.firstOrNull()
        ?.asJsonObject
        ?.getAsJsonObject("message")
        ?: throw IllegalArgumentException("AI response has no message")
    val content = message.get("content") ?: throw IllegalArgumentException("AI response has no content")
    val text = when {
        content.isJsonPrimitive -> content.asString
        content.isJsonArray -> content.asJsonArray.joinToString(separator = "\n") { part ->
            val obj = part.asJsonObject
            obj.get("text")?.asString.orEmpty()
        }
        else -> ""
    }.trim()
    val stripped = text
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    if (!stripped.startsWith("{")) throw IllegalArgumentException("AI response content is not JSON")
    return stripped
}

private val aiClientGson: Gson = GsonBuilder().disableHtmlEscaping().create()
private const val AI_SERVICE_TEST_MAX_BODY_CHARS = 4096
private const val AI_CONNECT_TIMEOUT_SECONDS = 30
private const val AI_WRITE_TIMEOUT_SECONDS = 120
private const val AI_MAX_RETRY_AFTER_SECONDS = 120L
private const val AI_SERVICE_TEST_IMAGE_DATA_URL =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
