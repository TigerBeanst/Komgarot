package fail.tiger.komgarot.data.local

data class CacheSizeOption(val sizeMb: Int) {
    val bytes: Long get() = sizeMb.toLong() * 1024L * 1024L

    companion object {
        val values = listOf(256, 512, 1024, 2048, 4096).map(::CacheSizeOption)
        val default = CacheSizeOption(2048)

        fun fromMb(sizeMb: Int): CacheSizeOption =
            values.firstOrNull { it.sizeMb == sizeMb } ?: default
    }
}
