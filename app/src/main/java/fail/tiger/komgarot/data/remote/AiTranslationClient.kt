package fail.tiger.komgarot.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import fail.tiger.komgarot.data.local.AiImageTransport

data class AiTranslationImageInput(
    val pageIndex: Int,
    val transport: AiImageTransport,
    val mimeType: String,
    val base64: String,
    val imageUrl: String,
    val localRegionId: String = ""
) {
    fun toOpenAiImageUrl(): String =
        when (transport) {
            AiImageTransport.BASE64 -> "data:$mimeType;base64,$base64"
            AiImageTransport.IMAGE_URL -> imageUrl
        }

    fun metadataText(): String = if (localRegionId.isBlank()) {
        "imageRole=page_context; pageIndex=$pageIndex"
    } else {
        "imageRole=text_region; pageIndex=$pageIndex; localRegionId=$localRegionId"
    }
}

sealed interface AiTranslationRequestResult {
    data class Success(val normalizedJson: String) : AiTranslationRequestResult
    data class Failure(val category: AiTranslationErrorCategory, val summary: String) : AiTranslationRequestResult
}

enum class AiTranslationErrorCategory {
    NETWORK_OR_API,
    VISION_UNSUPPORTED,
    NON_JSON_RESPONSE,
    JSON_VALIDATION_FAILED
}

class AiTranslationClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    fun translate(
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
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildAiTranslationChatRequestJson(model, systemPrompt, userPrompt, images)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        return runCatching {
            httpClient.newBuilder()
                .connectTimeout(AI_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
                .readTimeout(responseTimeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(writeTimeout.toLong(), TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val category = if (response.code == 400 || response.code == 404) {
                        AiTranslationErrorCategory.VISION_UNSUPPORTED
                    } else {
                        AiTranslationErrorCategory.NETWORK_OR_API
                    }
                    return AiTranslationRequestResult.Failure(
                        category,
                        buildAiHttpFailureSummary(response.code, response.message, responseBody)
                    )
                }
                val json = extractAiTranslationJsonContent(responseBody)
                AiTranslationRequestResult.Success(json)
            }
        }.getOrElse { throwable ->
            AiTranslationRequestResult.Failure(
                category = if (throwable is IllegalArgumentException) {
                    AiTranslationErrorCategory.NON_JSON_RESPONSE
                } else {
                    AiTranslationErrorCategory.NETWORK_OR_API
                },
                summary = aiTranslationFailureSummary(throwable, responseTimeout)
            )
        }
    }
}

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
private const val AI_CONNECT_TIMEOUT_SECONDS = 30
private const val AI_WRITE_TIMEOUT_SECONDS = 120
