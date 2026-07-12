package fail.tiger.komgarot.ui.book

import java.io.File
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
    }
}
