package fail.tiger.komgarot.ui.metadata

import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class CoverUploadFailureTest {
    @Test
    fun httpFailureIncludesStatusAndServerBody() {
        val error = HttpException(
            Response.error<Unit>(
                502,
                "{\"message\":\"thumbnail storage unavailable\"}".toResponseBody()
            )
        )

        val message = coverUploadFailureMessage(error, "封面更新失败", "需要管理员权限")

        assertTrue(message.contains("封面更新失败"))
        assertTrue(message.contains("HTTP 502"))
        assertTrue(message.contains("thumbnail storage unavailable"))
    }

    @Test
    fun forbiddenFailureUsesPermissionMessageAndStatus() {
        val error = HttpException(Response.error<Unit>(403, "".toResponseBody()))

        assertEquals(
            "需要管理员权限 (HTTP 403)",
            coverUploadFailureMessage(error, "封面更新失败", "需要管理员权限")
        )
    }

    @Test
    fun networkFailureIncludesExceptionDetail() {
        val message = coverUploadFailureMessage(
            IOException("connection reset"),
            "封面更新失败",
            "需要管理员权限"
        )

        assertEquals("封面更新失败: connection reset", message)
    }

    @Test
    fun payloadTooLargeFailureExplainsServerLimit() {
        val error = HttpException(Response.error<Unit>(413, "".toResponseBody()))

        assertEquals(
            "服务器拒绝了过大的封面，请提高上传大小上限。 (HTTP 413)",
            coverUploadFailureMessage(
                error = error,
                fallbackMessage = "封面更新失败",
                forbiddenMessage = "需要管理员权限",
                payloadTooLargeMessage = "服务器拒绝了过大的封面，请提高上传大小上限。"
            )
        )
    }
}
