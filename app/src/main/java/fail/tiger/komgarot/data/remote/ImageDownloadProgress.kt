package fail.tiger.komgarot.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

fun interface ImageDownloadProgressListener {
    fun onProgress(bytesRead: Long, contentLength: Long)
}

class ImageDownloadProgressInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val listener = request.tag(ImageDownloadProgressListener::class.java)
        val response = chain.proceed(request)
        val body = response.body
        return if (listener != null && body != null && response.isSuccessful) {
            response.newBuilder()
                .body(ProgressResponseBody(body, listener))
                .build()
        } else {
            response
        }
    }
}

private class ProgressResponseBody(
    private val delegate: ResponseBody,
    private val listener: ImageDownloadProgressListener
) : ResponseBody() {
    private var bufferedSource: BufferedSource? = null

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource {
        val source = bufferedSource
        if (source != null) return source

        return object : ForwardingSource(delegate.source()) {
            private var totalBytesRead = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                if (bytesRead >= 0L) {
                    totalBytesRead += bytesRead
                }
                listener.onProgress(totalBytesRead, contentLength())
                return bytesRead
            }
        }.buffer().also { bufferedSource = it }
    }
}
