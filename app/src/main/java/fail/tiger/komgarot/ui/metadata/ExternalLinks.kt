package fail.tiger.komgarot.ui.metadata

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fail.tiger.komgarot.R

fun normalizeExternalUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return if (SCHEME_PATTERN.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
}

fun openExternalUrl(context: Context, value: String) {
    val normalized = normalizeExternalUrl(value) ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.operation_failed), Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, context.getString(R.string.operation_failed), Toast.LENGTH_SHORT).show()
    }
}

private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
