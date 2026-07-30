package fail.tiger.komgarot.data.repository

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryStructureTest {
    private val source = File("src/main/java/fail/tiger/komgarot/data/repository/AuthRepository.kt").readText()

    @Test
    fun serverUrlUpdateVerifiesSavedCredentialsBeforePersisting() {
        val updateStart = source.indexOf("suspend fun updateServerUrl(")
        val updateEnd = source.indexOf("private fun verifyServer(", updateStart)
        val updateSource = source.substring(updateStart, updateEnd)

        assertTrue(updateSource.contains("normalizeServerUrl(url)"))
        assertTrue(updateSource.contains("prefs.usernameBlocking"))
        assertTrue(updateSource.contains("prefs.passwordBlocking"))
        assertTrue(updateSource.indexOf("verifyServer(") < updateSource.indexOf("prefs.save("))
        assertTrue(source.contains(".url(\"${'$'}{url.trimEnd('/')}/api/v1/libraries\")"))
        assertTrue(source.contains("client.newCall(request).execute().use"))
    }
}
