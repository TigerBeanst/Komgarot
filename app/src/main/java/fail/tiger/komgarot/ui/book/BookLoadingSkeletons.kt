package fail.tiger.komgarot.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fail.tiger.komgarot.R
import fail.tiger.komgarot.ui.components.FloatingDetailActions
import fail.tiger.komgarot.ui.components.ImmersiveDetailDefaults

@Composable
fun BookDetailLoadingSkeleton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BookSkeletonBlock(
            Modifier
                .fillMaxWidth()
                .height(ImmersiveDetailDefaults.HeaderHeight),
            shape = RoundedCornerShape(0.dp)
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    top = ImmersiveDetailDefaults.IdentityTopPadding,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                BookSkeletonBlock(
                    Modifier
                        .width(ImmersiveDetailDefaults.CoverWidth)
                        .aspectRatio(0.7f)
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BookSkeletonBlock(Modifier.fillMaxWidth().height(28.dp))
                    BookSkeletonBlock(Modifier.fillMaxWidth(0.58f).height(18.dp))
                    BookSkeletonBlock(Modifier.fillMaxWidth(0.42f).height(14.dp))
                }
            }
            repeat(3) {
                BookSkeletonBlock(Modifier.fillMaxWidth().height(52.dp))
            }
            HorizontalDivider()
            repeat(4) { index ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BookSkeletonBlock(Modifier.width(88.dp).height(16.dp))
                    BookSkeletonBlock(
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.52f else 0.36f)
                            .height(16.dp)
                    )
                }
            }
        }
        FloatingDetailActions(
            onBack = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            backIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        )
    }
}

@Composable
fun BookGridLoadingSkeleton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BookSkeletonBlock(
            Modifier
                .fillMaxWidth()
                .height(ImmersiveDetailDefaults.HeaderHeight),
            shape = RoundedCornerShape(0.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = ImmersiveDetailDefaults.IdentityTopPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        BookSkeletonBlock(
                            Modifier
                                .width(ImmersiveDetailDefaults.CoverWidth)
                                .aspectRatio(0.7f)
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BookSkeletonBlock(Modifier.fillMaxWidth().height(28.dp))
                            BookSkeletonBlock(Modifier.fillMaxWidth(0.58f).height(18.dp))
                            BookSkeletonBlock(Modifier.fillMaxWidth(0.42f).height(18.dp))
                        }
                    }
                    BookSkeletonBlock(Modifier.fillMaxWidth(0.36f).height(14.dp))
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
            }
            items(8) {
                BookGridCardLoadingSkeleton()
            }
        }
        FloatingDetailActions(
            onBack = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            backIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        )
    }
}

@Composable
private fun BookGridCardLoadingSkeleton() {
    BookSkeletonBlock(Modifier.fillMaxWidth().aspectRatio(0.7f))
}

@Composable
private fun BookSkeletonBlock(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}
