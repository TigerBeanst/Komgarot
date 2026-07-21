package fail.tiger.komgarot.ui.metadata

import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

enum class MetadataLanguageName {
    Unset,
    English,
    Chinese,
    Japanese,
    Korean,
    Thai,
    Dynamic
}

data class MetadataLanguageOption(
    val tag: String,
    val name: MetadataLanguageName
)

private val basicMetadataLanguageOptions = listOf(
    MetadataLanguageOption("", MetadataLanguageName.Unset),
    MetadataLanguageOption("en", MetadataLanguageName.English),
    MetadataLanguageOption("zh", MetadataLanguageName.Chinese),
    MetadataLanguageOption("ja", MetadataLanguageName.Japanese),
    MetadataLanguageOption("ko", MetadataLanguageName.Korean),
    MetadataLanguageOption("th", MetadataLanguageName.Thai)
)

fun buildMetadataLanguageOptions(
    currentLanguage: String?,
    serverLanguages: List<String>
): List<MetadataLanguageOption> {
    val seen = basicMetadataLanguageOptions
        .mapTo(mutableSetOf()) { it.tag.languageKey() }
    val dynamicOptions = (serverLanguages + listOfNotNull(currentLanguage))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { seen.add(it.languageKey()) }
        .sortedBy { it.languageKey() }
        .map { MetadataLanguageOption(it, MetadataLanguageName.Dynamic) }
    return basicMetadataLanguageOptions + dynamicOptions
}

suspend fun loadMetadataLanguages(
    load: suspend () -> List<String>
): List<String> = try {
    load()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    emptyList()
}

private fun String.languageKey(): String = lowercase(Locale.ROOT)
