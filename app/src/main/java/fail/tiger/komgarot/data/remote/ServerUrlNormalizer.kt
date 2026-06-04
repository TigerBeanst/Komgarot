package fail.tiger.komgarot.data.remote

fun normalizeServerUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""
    return if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "http://$trimmed"
    }
}
