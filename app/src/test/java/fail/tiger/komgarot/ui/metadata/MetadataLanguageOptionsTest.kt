package fail.tiger.komgarot.ui.metadata

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataLanguageOptionsTest {
    @Test
    fun basicLanguagesStayFirstAndDynamicTagsAreDeduplicated() {
        val options = buildMetadataLanguageOptions(
            currentLanguage = "zh-Hans",
            serverLanguages = listOf("ja", " fr ", "ZH", "de-DE", "")
        )

        assertEquals(
            listOf("", "en", "zh", "ja", "ko", "th", "de-DE", "fr", "zh-Hans"),
            options.map(MetadataLanguageOption::tag)
        )
    }

    @Test
    fun currentLanguageRemainsAvailableWithoutServerReferentialData() {
        val options = buildMetadataLanguageOptions(
            currentLanguage = "pt-BR",
            serverLanguages = emptyList()
        )

        assertEquals("pt-BR", options.last().tag)
    }

    @Test
    fun languageLoadingReturnsServerValues() = runBlocking {
        assertEquals(listOf("en", "ja"), loadMetadataLanguages { listOf("en", "ja") })
    }

    @Test
    fun languageLoadingFallsBackToEmptyList() = runBlocking {
        assertEquals(emptyList<String>(), loadMetadataLanguages { throw IOException("offline") })
    }

    @Test(expected = CancellationException::class)
    fun languageLoadingKeepsCoroutineCancellation() {
        runBlocking {
            loadMetadataLanguages { throw CancellationException("cancelled") }
        }
    }
}
