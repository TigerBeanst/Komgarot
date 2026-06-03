package fail.tiger.komgarot.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveDetailHeaderDefaultsTest {
    @Test
    fun immersiveDetailDefaultsKeepBookAndSeriesHeadersAligned() {
        assertEquals(330.dp, ImmersiveDetailDefaults.HeaderHeight)
        assertEquals(180.dp, ImmersiveDetailDefaults.IdentityTopPadding)
        assertEquals(120.dp, ImmersiveDetailDefaults.CoverWidth)
    }
}
