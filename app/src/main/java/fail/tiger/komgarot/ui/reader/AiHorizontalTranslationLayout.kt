package fail.tiger.komgarot.ui.reader

import java.text.BreakIterator
import java.util.Locale

/**
 * Wraps a translated horizontal paragraph according to the target language.
 * The existing layout remains the fallback when the block has no target locale.
 */
internal fun horizontalTargetTextLines(
    lines: List<String>,
    widthDp: Float,
    fontSizeSp: Float,
    targetLocale: String
): List<String> {
    val clean = lines.map(String::trim).filter(String::isNotBlank)
    if (clean.isEmpty()) return emptyList()
    if (targetLocale.isBlank()) return balancedHorizontalLines(clean, widthDp, fontSizeSp)

    // capacity is an upper bound on visual units per line; the overlay measures a
    // line as visualUnits * fontSizeSp, so units <= widthDp / fontSizeSp keeps the
    // estimated line width within the available width.
    val capacity = (widthDp / fontSizeSp).coerceAtLeast(1f)
    val language = targetLocale.normalizedHorizontalTargetLanguage()
    val usesWordSpaces = language in TARGET_LANGUAGES_WITH_WORD_SPACES
    val joined = if (usesWordSpaces) {
        clean.joinToString(" ")
    } else {
        clean.joinToString("")
    }
    return if (usesWordSpaces) {
        wrapHorizontalTargetWords(joined, capacity)
    } else {
        wrapHorizontalTargetGraphemes(joined, capacity)
    }
}

internal fun horizontalTargetLocaleUsesWordSpaces(targetLocale: String): Boolean =
    targetLocale.normalizedHorizontalTargetLanguage() in TARGET_LANGUAGES_WITH_WORD_SPACES

private fun String.normalizedHorizontalTargetLanguage(): String =
    trim().replace('_', '-').substringBefore('-').lowercase(Locale.ROOT)

private fun wrapHorizontalTargetWords(text: String, capacity: Float): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    var current = ""
    var currentUnits = 0f
    words.forEach { word ->
        val wordUnits = horizontalTargetVisualUnits(word)
        if (current.isBlank()) {
            if (wordUnits <= capacity) {
                current = word
                currentUnits = wordUnits
            } else {
                result += wrapHorizontalTargetGraphemes(word, capacity)
            }
        } else if (currentUnits + 1f + wordUnits <= capacity) {
            current = "$current $word"
            currentUnits += 1f + wordUnits
        } else {
            result += current
            current = ""
            currentUnits = 0f
            if (wordUnits <= capacity) {
                current = word
                currentUnits = wordUnits
            } else {
                result += wrapHorizontalTargetGraphemes(word, capacity)
            }
        }
    }
    if (current.isNotBlank()) result += current
    return result
}

private fun wrapHorizontalTargetGraphemes(text: String, capacity: Float): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var currentUnits = 0f
    horizontalTargetGraphemes(text).forEach { grapheme ->
        val units = horizontalTargetGraphemeUnits(grapheme)
        val combining = grapheme.all(Char::isCombiningMark)
        if (!combining && current.isNotEmpty() && currentUnits + units > capacity) {
            if (grapheme.lastOrNull()?.isClosingPunctuation() == true) {
                current.append(grapheme)
                currentUnits += units
                return@forEach
            }
            result += current.toString()
            current = StringBuilder()
            currentUnits = 0f
        } else if (!combining && current.isEmpty() && result.isNotEmpty() &&
            grapheme.lastOrNull()?.isClosingPunctuation() == true
        ) {
            result[result.lastIndex] += grapheme
            return@forEach
        }
        current.append(grapheme)
        currentUnits += units
    }
    if (current.isNotEmpty()) result += current.toString()
    return result
}

private fun horizontalTargetGraphemes(text: String): List<String> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    val result = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result += text.substring(start, end)
        start = end
        end = iterator.next()
    }
    return result
}

private fun horizontalTargetGraphemeUnits(grapheme: String): Float {
    if (grapheme.isEmpty()) return 0f
    val codePoint = grapheme.codePointAt(0)
    if (codePoint > Char.MAX_VALUE.code) return 1f
    return horizontalTargetCharacterUnits(codePoint.toChar())
}

private fun horizontalTargetVisualUnits(value: String): Float =
    horizontalTargetGraphemes(value).fold(0f) { total, grapheme ->
        total + horizontalTargetGraphemeUnits(grapheme)
    }

private fun horizontalTargetCharacterUnits(character: Char): Float = when {
    character.isWhitespace() -> 1f
    character.code in 0x3040..0x30FF -> 1f
    character.code in 0x3400..0x4DBF -> 1f
    character.code in 0x4E00..0x9FFF -> 1f
    character.code in 0xAC00..0xD7AF -> 1f
    else -> 0.58f
}

private fun Char.isClosingPunctuation(): Boolean = this in CLOSING_PUNCTUATION

private fun Char.isCombiningMark(): Boolean = when (Character.getType(this)) {
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt() -> true
    else -> false
}

private val TARGET_LANGUAGES_WITH_WORD_SPACES = setOf(
    "af", "cs", "da", "de", "en", "es", "fi", "fr", "hu", "id", "it", "ko", "ms", "nl", "no", "pl", "pt", "ro", "sk", "sl", "sv", "sw", "tr", "vi"
)

private val CLOSING_PUNCTUATION = setOf(
    '。', '，', '、', '．', '！', '？', '：', '；', '）', '】', '》', '」', '』', '〉', '〕', '］', '｝',
    '.', ',', '!', '?', ':', ';', ')', ']', '}', '…'
)
