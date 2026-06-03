package fail.tiger.komgarot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ImmersiveDetailDefaults {
    val HeaderHeight = 330.dp
    val IdentityTopPadding = 180.dp
    val CoverWidth = 120.dp
}

@Composable
fun ImmersiveDetailScaffold(
    backgroundImageUrl: String,
    backgroundImageCacheKey: String,
    coverImageUrl: String,
    coverImageCacheKey: String,
    contentDescription: String?,
    padding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    headerHeight: Dp = ImmersiveDetailDefaults.HeaderHeight,
    coverWidth: Dp = ImmersiveDetailDefaults.CoverWidth,
    topOverlap: Dp = ImmersiveDetailDefaults.IdentityTopPadding,
    actions: @Composable BoxScope.() -> Unit,
    titleContent: @Composable () -> Unit,
    bodyContent: @Composable () -> Unit
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ImmersiveDetailBackground(
            imageUrl = backgroundImageUrl,
            imageCacheKey = backgroundImageCacheKey,
            headerHeight = headerHeight
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = topOverlap, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ImmersiveDetailIdentityRow(
                coverImageUrl = coverImageUrl,
                coverImageCacheKey = coverImageCacheKey,
                contentDescription = contentDescription,
                coverWidth = coverWidth,
                titleContent = titleContent
            )
            bodyContent()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            content = actions
        )
    }
}

@Composable
fun ImmersiveDetailBackground(
    imageUrl: String,
    imageCacheKey: String,
    modifier: Modifier = Modifier,
    headerHeight: Dp = ImmersiveDetailDefaults.HeaderHeight
) {
    ThumbnailImage(
        url = imageUrl,
        cacheKey = imageCacheKey,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().height(headerHeight)
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                        MaterialTheme.colorScheme.surface
                    ),
                    startY = 180f
                )
            )
    )
}

@Composable
fun ImmersiveDetailIdentityRow(
    coverImageUrl: String,
    coverImageCacheKey: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    coverWidth: Dp = ImmersiveDetailDefaults.CoverWidth,
    titleContent: @Composable () -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Card(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.width(coverWidth)
        ) {
            ThumbnailImage(
                url = coverImageUrl,
                cacheKey = coverImageCacheKey,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            titleContent()
        }
    }
}

@Composable
fun FloatingDetailActions(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backIcon: @Composable () -> Unit,
    trailingActions: @Composable () -> Unit = {}
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FloatingDetailIconButton(onClick = onBack) {
            backIcon()
        }
        Spacer(Modifier.weight(1f))
        trailingActions()
    }
}

@Composable
fun FloatingDetailIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.42f))
    ) {
        content()
    }
}
