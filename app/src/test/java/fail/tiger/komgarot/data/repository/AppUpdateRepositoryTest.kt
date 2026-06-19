package fail.tiger.komgarot.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun versionTagsCompareByNumericSegments() {
        assertTrue(isRemoteVersionNewer(local = "v1.0.2.9", remote = "v1.0.2.10"))
        assertTrue(isRemoteVersionNewer(local = "1.0.2.9", remote = "v1.1.0.1"))
        assertFalse(isRemoteVersionNewer(local = "v1.1.0.81", remote = "v1.1.0.81"))
        assertFalse(isRemoteVersionNewer(local = "v1.1.1.1", remote = "v1.1.0.99"))
    }

    @Test
    fun githubReleaseJsonParsesReleaseFields() {
        val json = """
            {
              "tag_name": "v1.1.0.82",
              "name": "Komgarot v1.1.0.82",
              "body": "更新日志正文",
              "html_url": "https://github.com/TigerBeanst/Komgarot/releases/tag/v1.1.0.82",
              "draft": false,
              "prerelease": false
            }
        """.trimIndent()

        val release = parseGithubRelease(json)

        assertEquals("v1.1.0.82", release?.tagName)
        assertEquals("Komgarot v1.1.0.82", release?.name)
        assertEquals("更新日志正文", release?.body)
        assertEquals("https://github.com/TigerBeanst/Komgarot/releases/tag/v1.1.0.82", release?.htmlUrl)
    }
}
