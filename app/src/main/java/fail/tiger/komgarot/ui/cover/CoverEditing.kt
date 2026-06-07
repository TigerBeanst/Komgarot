package fail.tiger.komgarot.ui.cover

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

enum class CoverCrop { Full, LeftHalf, RightHalf }

const val CoverUploadMaxEdge = 2048

data class CoverSize(
    val width: Int,
    val height: Int
)

data class CoverCropRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

fun coverCropRect(imageWidth: Int, imageHeight: Int, crop: CoverCrop): CoverCropRect {
    val safeWidth = imageWidth.coerceAtLeast(1)
    val safeHeight = imageHeight.coerceAtLeast(1)
    val half = (safeWidth / 2).coerceAtLeast(1)
    return when (crop) {
        CoverCrop.Full -> CoverCropRect(0, 0, safeWidth, safeHeight)
        CoverCrop.LeftHalf -> CoverCropRect(0, 0, half, safeHeight)
        CoverCrop.RightHalf -> {
            val x = if (safeWidth == 1) 0 else safeWidth / 2
            CoverCropRect(x, 0, safeWidth - x, safeHeight)
        }
    }
}

fun cropCoverBitmap(bitmap: Bitmap, crop: CoverCrop): Bitmap {
    val rect = coverCropRect(bitmap.width, bitmap.height, crop)
    return Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
}

fun scaledCoverSize(width: Int, height: Int, maxEdge: Int = CoverUploadMaxEdge): CoverSize {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val safeMaxEdge = maxEdge.coerceAtLeast(1)
    val largestEdge = maxOf(safeWidth, safeHeight)
    if (largestEdge <= safeMaxEdge) return CoverSize(safeWidth, safeHeight)

    val scale = safeMaxEdge.toFloat() / largestEdge
    return CoverSize(
        width = (safeWidth * scale).roundToInt().coerceAtLeast(1),
        height = (safeHeight * scale).roundToInt().coerceAtLeast(1)
    )
}

fun scaleCoverBitmapForUpload(bitmap: Bitmap, maxEdge: Int = CoverUploadMaxEdge): Bitmap {
    val size = scaledCoverSize(bitmap.width, bitmap.height, maxEdge)
    if (size.width == bitmap.width && size.height == bitmap.height) return bitmap
    return bitmap.scale(size.width, size.height)
}

fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 90): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

fun writeTemporaryCoverImage(context: Context, bitmap: Bitmap): Uri {
    val file = File(context.cacheDir, "cover_candidate_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}
