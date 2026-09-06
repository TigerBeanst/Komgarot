package fail.tiger.komgarot.data.repository

class AiMangaLensOnnxDetector(
    modelRepository: AiLocalModelRepository
) : AiBubbleRegionDetector {
    override fun detect(source: IntArray, width: Int, height: Int): List<AiBubbleRegion> = emptyList()

    override fun cacheVersion(): String = "bubble-lite-v1"
}
