package fail.tiger.komgarot.data.remote

import android.content.Context
import fail.tiger.komgarot.data.local.ReaderPageCache
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import okio.sink

class ReaderPageCacheInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cacheEntry = request.tag(ReaderPageCache.Entry::class.java)
        val response = chain.proceed(request)
        val body = response.body

        return if (
            cacheEntry != null &&
            body != null &&
            request.method == "GET" &&
            response.isSuccessful
        ) {
            response.newBuilder()
                .body(ReaderPageCachingResponseBody(context, body, cacheEntry))
                .build()
        } else {
            response
        }
    }
}

private class ReaderPageCachingResponseBody(
    private val context: Context,
    private val delegate: ResponseBody,
    private val cacheEntry: ReaderPageCache.Entry
) : ResponseBody() {
    private var bufferedSource: BufferedSource? = null
    private var sink: BufferedSink? = null
    private var complete = false
    private var bytesWritten = 0L

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource {
        val source = bufferedSource
        if (source != null) return source

        cacheEntry.tempFile.parentFile?.mkdirs()
        val cacheSink = cacheEntry.tempFile.sink().buffer()
        sink = cacheSink

        return object : ForwardingSource(delegate.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                when {
                    bytesRead > 0L -> {
                        val copy = Buffer()
                        sink.copyTo(copy, sink.size - bytesRead, bytesRead)
                        cacheSink.write(copy, bytesRead)
                        bytesWritten += bytesRead
                    }
                    bytesRead == -1L -> {
                        finish()
                    }
                }
                return bytesRead
            }

            override fun close() {
                try {
                    super.close()
                } finally {
                    if (!complete) {
                        discard()
                    }
                }
            }
        }.buffer().also { bufferedSource = it }
    }

    private fun finish() {
        if (complete) return
        complete = true
        sink?.close()
        sink = null
        if (bytesWritten > 0L) {
            ReaderPageCache.commit(context, cacheEntry)
        } else {
            ReaderPageCache.discard(cacheEntry)
        }
    }

    private fun discard() {
        sink?.close()
        sink = null
        ReaderPageCache.discard(cacheEntry)
    }
}
