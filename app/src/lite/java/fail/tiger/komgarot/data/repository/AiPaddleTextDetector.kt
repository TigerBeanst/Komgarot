package fail.tiger.komgarot.data.repository

import android.content.Context
import android.graphics.Bitmap
import fail.tiger.komgarot.data.local.AiSettings
import fail.tiger.komgarot.data.remote.AiTranslationLocalTextRegion

class AiPaddleTextDetector(
    private val context: Context,
    private val modelRepository: AiLocalModelRepository
) {
    fun detect(
        bitmap: Bitmap,
        pixels: IntArray,
        pageIndex: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        settings: AiSettings,
        maxRegions: Int
    ): List<AiTranslationLocalTextRegion> = emptyList()
}
