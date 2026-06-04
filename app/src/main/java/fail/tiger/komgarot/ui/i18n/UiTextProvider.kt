package fail.tiger.komgarot.ui.i18n

import android.content.Context
import androidx.annotation.StringRes

interface UiTextProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
}

class AndroidUiTextProvider(context: Context) : UiTextProvider {
    private val appContext = context.applicationContext

    override fun get(id: Int, vararg args: Any): String =
        appContext.getString(id, *args)
}
