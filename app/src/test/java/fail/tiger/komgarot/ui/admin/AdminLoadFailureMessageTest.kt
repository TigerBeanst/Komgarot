package fail.tiger.komgarot.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminLoadFailureMessageTest {
    @Test
    fun failureMessageKeepsSectionNameAndErrorDetail() {
        val error = IllegalStateException("Expected BEGIN_ARRAY but was BEGIN_OBJECT")

        assertEquals(
            "公告加载失败: Expected BEGIN_ARRAY but was BEGIN_OBJECT",
            adminLoadFailureMessage("公告加载失败", error)
        )
    }

    @Test
    fun failureMessageFallsBackToSectionName() {
        assertEquals("公告加载失败", adminLoadFailureMessage("公告加载失败", RuntimeException()))
    }
}
