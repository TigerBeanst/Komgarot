package fail.tiger.komgarot.ui.book

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLoadingSkeletonStructureTest {
    @Test
    fun skeletonsMirrorTargetLayouts() {
        val file = File("src/main/java/fail/tiger/komgarot/ui/book/BookLoadingSkeletons.kt")
        assertTrue(file.exists())
        val source = file.readText()

        assertTrue(source.contains("fun BookDetailLoadingSkeleton("))
        assertTrue(source.contains("fun BookGridLoadingSkeleton("))
        assertTrue(source.contains("FloatingDetailActions("))
        assertTrue(source.contains("GridCells.Adaptive(104.dp)"))
        assertTrue(source.contains("items(8)"))
        assertTrue(source.contains(".height(ImmersiveDetailDefaults.HeaderHeight)"))
        assertTrue(source.contains(".padding(top = ImmersiveDetailDefaults.IdentityTopPadding)"))
        assertTrue(source.contains(".width(ImmersiveDetailDefaults.CoverWidth)"))
        assertTrue(source.contains("BookSkeletonBlock(Modifier.fillMaxWidth(0.36f).height(14.dp))"))
        assertTrue(source.contains("contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)"))

        val cardStart = source.indexOf("private fun BookGridCardLoadingSkeleton")
        val cardEnd = source.indexOf("private fun BookSkeletonBlock", cardStart)
        assertTrue(cardStart >= 0)
        assertTrue(cardEnd > cardStart)
        val cardSource = source.substring(cardStart, cardEnd)
        assertTrue(cardSource.contains("fillMaxWidth().aspectRatio(0.7f)"))
        assertFalse(cardSource.contains("height(14.dp)"))
        assertFalse(cardSource.contains("height(12.dp)"))
    }
}
