package fail.tiger.komgarot.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nStructureTest {
    private val root = File("src/main")

    @Test
    fun englishAndSimplifiedChineseResourcesExposeSameStringNames() {
        val defaultResources = stringResourceNames(File(root, "res/values/strings.xml"))
        val simplifiedChineseResources = stringResourceNames(File(root, "res/values-zh-rCN/strings.xml"))

        assertEquals(defaultResources, simplifiedChineseResources)
    }

    @Test
    fun mainKotlinSourcesDoNotKeepHardcodedUiText() {
        val sourceRoot = File(root, "java/fail/tiger/komgarot")
        val offenders = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("//") -> null
                        chineseUiTextPattern.containsMatchIn(line) -> "${file.relativeTo(root)}:${index + 1}: $trimmed"
                        hardcodedUiTextPattern.containsMatchIn(line) && !allowedHardcodedUiText(line) ->
                            "${file.relativeTo(root)}:${index + 1}: $trimmed"
                        else -> null
                    }
                }
            }
            .toList()

        assertTrue(
            "Hardcoded UI text should move to string resources:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    private fun stringResourceNames(file: File): Set<String> {
        val text = file.readText()
        val stringNames = Regex("""<string\s+name="([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
        val pluralNames = Regex("""<plurals\s+name="([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
        return (stringNames + pluralNames).toSortedSet()
    }

    private companion object {
        val chineseUiTextPattern = Regex(""""[^"]*[\u4E00-\u9FFF][^"]*"""")
        val hardcodedUiTextPattern = Regex(
            """((?<![A-Za-z])Text\("[A-Za-z]|label\s*=\s*\{\s*Text\("[A-Za-z]|placeholder\s*=\s*\{\s*Text\("[A-Za-z]|contentDescription\s*=\s*"[A-Za-z]|title\s*=\s*"[A-Za-z]|text\s*=\s*"[A-Za-z]|confirmText\s*=\s*"[A-Za-z])"""
        )

        private fun allowedHardcodedUiText(line: String): Boolean {
            val allowedSnippets = listOf(
                """Text(value)""",
                """Text(title)""",
                """Text(text)""",
                """Text(label)""",
                """Text(message)""",
                """Text(event.type)""",
                """Text(event.timestamp)""",
                """Text(item.type)""",
                """Text(item.title)""",
                """Text(item.message)""",
                """Text(item.email""",
                """Text(key.comment)""",
                """Text(library.name""",
                """Text(user.email""",
                """Text(book.metadata.title""",
                """Text(series.metadata.title""",
                """Text(collection.name""",
                """Text(readList.name""",
                """Text(meta.summary""",
                """Text(value.ifEmpty""",
                """Text(author.name""",
                """Text(author.name, role""",
                """Text("$""",
                """Text("OAuth"""
            )
            return allowedSnippets.any { line.contains(it) }
        }
    }
}
