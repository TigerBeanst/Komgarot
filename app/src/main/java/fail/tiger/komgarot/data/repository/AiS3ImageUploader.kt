package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.local.SecureAiSettings
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SortedMap
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class AiS3ImageUrlConfig(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val pathPrefix: String = "ai-temp",
    val ttlSeconds: Int = 300,
    val pathStyle: Boolean = true
) {
    fun isComplete(): Boolean = endpoint.isNotBlank() &&
        region.isNotBlank() &&
        bucket.isNotBlank() &&
        accessKey.isNotBlank() &&
        secretKey.isNotBlank()
}

fun SecureAiSettings.s3ImageUrlConfigOrNull(): AiS3ImageUrlConfig? =
    takeIf { it.hasCompleteS3ImageUrlConfiguration() }?.let {
        AiS3ImageUrlConfig(
            endpoint = it.s3Endpoint,
            region = it.s3Region,
            bucket = it.s3Bucket,
            accessKey = it.s3AccessKey,
            secretKey = it.s3SecretKey,
            pathPrefix = it.s3PathPrefix,
            ttlSeconds = it.s3TtlSeconds.coerceIn(60, 3600),
            pathStyle = it.s3PathStyle
        )
    }

class AiS3ImageUploader(
    private val httpClient: OkHttpClient,
    private val config: AiS3ImageUrlConfig
) {
    fun objectKey(bookId: String, pageIndex: Int, imageId: String, extension: String): String =
        aiS3ObjectKey(bookId, pageIndex, imageId, extension, config.pathPrefix)

    fun uploadImage(bytes: ByteArray, mimeType: String, objectKey: String): String {
        val putUrl = aiS3PresignedUrl(config, method = "PUT", objectKey = objectKey)
        val request = Request.Builder()
            .url(putUrl)
            .put(bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("S3 image upload failed: HTTP ${response.code} ${response.message}")
            }
        }
        return aiS3PresignedUrl(config, method = "GET", objectKey = objectKey)
    }
}

fun testAiS3ImageUrlUpload(
    config: AiS3ImageUrlConfig,
    httpClient: OkHttpClient = OkHttpClient(),
    nowMillis: Long = System.currentTimeMillis()
): Result<String> = runCatching {
    val probeId = "probe-$nowMillis"
    AiS3ImageUploader(httpClient, config).uploadImage(
        bytes = "komgarot-s3-probe".toByteArray(StandardCharsets.UTF_8),
        mimeType = "text/plain",
        objectKey = aiS3ObjectKey(
            bookId = "settings-test",
            pageIndex = 0,
            imageId = probeId,
            extension = "txt",
            pathPrefix = config.pathPrefix
        )
    )
}

fun aiS3ObjectKey(
    bookId: String,
    pageIndex: Int,
    imageId: String,
    extension: String,
    pathPrefix: String = "ai-temp"
): String {
    val safePrefix = pathPrefix.trim('/').ifBlank { "ai-temp" }
    val safeBook = bookId.s3PathSegment()
    val safeImage = imageId.s3PathSegment()
    val safeExtension = extension.trim('.').ifBlank { "jpg" }.s3PathSegment()
    return "$safePrefix/$safeBook/page-$pageIndex-$safeImage.$safeExtension"
}

fun aiS3PresignedUrl(
    config: AiS3ImageUrlConfig,
    method: String,
    objectKey: String,
    nowMillis: Long = System.currentTimeMillis()
): String {
    require(config.isComplete()) { "S3 image URL configuration is incomplete." }
    val methodUpper = method.uppercase(Locale.US)
    val endpoint = URI(config.endpoint.trimEnd('/'))
    val host = if (config.pathStyle) endpoint.host else "${config.bucket}.${endpoint.host}"
    val scheme = endpoint.scheme ?: "https"
    val port = endpoint.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
    val basePath = endpoint.rawPath.orEmpty().trimEnd('/')
    val encodedKey = objectKey.trim('/').split('/').joinToString("/") { awsEncode(it) }
    val canonicalUri = if (config.pathStyle) {
        "$basePath/${awsEncode(config.bucket)}/$encodedKey"
    } else {
        "$basePath/$encodedKey"
    }.replace(Regex("/{2,}"), "/")
    val dates = awsDates(nowMillis)
    val credentialScope = "${dates.shortDate}/${config.region}/s3/aws4_request"
    val query = sortedMapOf(
        "X-Amz-Algorithm" to "AWS4-HMAC-SHA256",
        "X-Amz-Credential" to "${config.accessKey}/$credentialScope",
        "X-Amz-Date" to dates.longDate,
        "X-Amz-Expires" to config.ttlSeconds.coerceIn(60, 3600).toString(),
        "X-Amz-SignedHeaders" to "host"
    )
    val canonicalQuery = query.awsQueryString()
    val canonicalHeaders = "host:$host$port\n"
    val canonicalRequest = listOf(
        methodUpper,
        canonicalUri,
        canonicalQuery,
        canonicalHeaders,
        "host",
        "UNSIGNED-PAYLOAD"
    ).joinToString("\n")
    val stringToSign = listOf(
        "AWS4-HMAC-SHA256",
        dates.longDate,
        credentialScope,
        sha256Hex(canonicalRequest)
    ).joinToString("\n")
    val signature = hmacSha256Hex(signingKey(config.secretKey, dates.shortDate, config.region), stringToSign)
    val signedQuery = (query + ("X-Amz-Signature" to signature)).toSortedMap().awsQueryString()
    return "$scheme://$host$port$canonicalUri?$signedQuery"
}

private data class AwsDates(val shortDate: String, val longDate: String)

private fun awsDates(nowMillis: Long): AwsDates {
    val date = Date(nowMillis)
    val short = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
    val long = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
    return AwsDates(shortDate = short, longDate = long)
}

private fun SortedMap<String, String>.awsQueryString(): String =
    entries.joinToString("&") { (key, value) -> "${awsEncode(key)}=${awsEncode(value)}" }

private fun signingKey(secretKey: String, shortDate: String, region: String): ByteArray {
    val kDate = hmacSha256("AWS4$secretKey".toByteArray(StandardCharsets.UTF_8), shortDate)
    val kRegion = hmacSha256(kDate, region)
    val kService = hmacSha256(kRegion, "s3")
    return hmacSha256(kService, "aws4_request")
}

private fun hmacSha256(key: ByteArray, value: String): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
}

private fun hmacSha256Hex(key: ByteArray, value: String): String = hmacSha256(key, value).toHex()

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.s3PathSegment(): String =
    trim().ifBlank { "empty" }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

private fun awsEncode(value: String): String = buildString {
    value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        val char = unsigned.toChar()
        if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '_' || char == '.' || char == '~') {
            append(char)
        } else {
            append('%')
            append("%02X".format(unsigned))
        }
    }
}
