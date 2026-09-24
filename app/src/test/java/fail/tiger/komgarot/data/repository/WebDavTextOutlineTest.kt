package fail.tiger.komgarot.data.repository

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavTextOutlineTest {
    @Test
    fun gsonRoundTripPreservesTextOutline() {
        val restored = Gson().fromJson(
            Gson().toJson(WebDavBackupSettings(aiTranslationTextOutline = true)),
            WebDavBackupSettings::class.java
        )

        assertTrue(restored.aiTranslationTextOutline)
    }

    @Test
    fun gsonMissingTextOutlineDefaultsToFalse() {
        val restored = Gson().fromJson("{}", WebDavBackupSettings::class.java)

        assertFalse(restored.aiTranslationTextOutline)
    }
}
