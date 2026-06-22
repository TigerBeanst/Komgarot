package fail.tiger.komgarot.data.repository

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiS3ImageUploaderTest {
    @Test
    fun presignedUrlsUseShortTtlAndDoNotExposeSecretKey() {
        val config = AiS3ImageUrlConfig(
            endpoint = "https://s3.example.test",
            region = "us-east-1",
            bucket = "komgarot-ai",
            accessKey = "AKIDEXAMPLE",
            secretKey = "SECRETEXAMPLE",
            pathPrefix = "ai-temp",
            ttlSeconds = 300,
            pathStyle = true
        )
        val key = aiS3ObjectKey(bookId = "book 1", pageIndex = 3, imageId = "region:4", extension = "jpg")

        val putUrl = aiS3PresignedUrl(
            config = config,
            method = "PUT",
            objectKey = key,
            nowMillis = 1_700_000_000_000L
        )
        val getUrl = aiS3PresignedUrl(
            config = config,
            method = "GET",
            objectKey = key,
            nowMillis = 1_700_000_000_000L
        )

        assertTrue(key.startsWith("ai-temp/"))
        assertTrue(putUrl.contains("X-Amz-Expires=300"))
        assertTrue(getUrl.contains("X-Amz-Expires=300"))
        assertTrue(putUrl.contains("X-Amz-Credential=AKIDEXAMPLE"))
        assertTrue(getUrl.contains("X-Amz-Signature="))
        assertFalse(putUrl.contains("SECRETEXAMPLE"))
        assertFalse(getUrl.contains("SECRETEXAMPLE"))
    }

    @Test
    fun s3ReachabilityProbeUploadsTinyObjectAndReturnsGetUrl() {
        val seenMethods = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                seenMethods += request.method
                assertEquals(null, request.header("Authorization"))
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()
        val config = AiS3ImageUrlConfig(
            endpoint = "https://s3.example.test",
            region = "us-east-1",
            bucket = "komgarot-ai",
            accessKey = "AKIDEXAMPLE",
            secretKey = "SECRETEXAMPLE",
            pathPrefix = "ai-temp",
            ttlSeconds = 300,
            pathStyle = true
        )

        val result = testAiS3ImageUrlUpload(config, client)

        assertTrue(result.isSuccess)
        assertEquals(listOf("PUT"), seenMethods)
        assertTrue(result.getOrThrow().contains("X-Amz-Expires=300"))
    }
}
