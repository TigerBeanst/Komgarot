package fail.tiger.komgarot.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LazyGridScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.Gray.copy(alpha = 0.6f),
    thumbWidth: Dp = 6.dp,
    thumbMinHeight: Dp = 48.dp
) {
    val scrollbarInfo by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems == 0 || visibleItems.isEmpty()) {
                null
            } else {
                val firstVisibleIndex = visibleItems.first().index
                val lastVisibleIndex = visibleItems.last().index
                if (firstVisibleIndex == 0 && lastVisibleIndex >= totalItems - 1) {
                    null
                } else {
                    LazyGridScrollbarInfo(
                        totalItems = totalItems,
                        scrollProgress = firstVisibleIndex.toFloat() / totalItems.toFloat(),
                        visibleRatio = (lastVisibleIndex - firstVisibleIndex + 1).toFloat() / totalItems.toFloat()
                    )
                }
            }
        }
    }

    val info = scrollbarInfo ?: return

    var isScrolling by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrolling) 150 else 500),
        label = "scrollbar_alpha"
    )

    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            isScrolling = true
        } else {
            delay(1000)
            isScrolling = false
        }
    }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(
        modifier = modifier.alpha(alpha)
    ) {
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val thumbMinHeightPx = with(density) { thumbMinHeight.toPx() }

        val thumbHeightPx = (maxHeightPx * info.visibleRatio).coerceAtLeast(thumbMinHeightPx)
        val thumbHeight = with(density) { thumbHeightPx.toDp() }

        val thumbOffsetPx = info.scrollProgress * (maxHeightPx - thumbHeightPx)
        val thumbOffset = with(density) { thumbOffsetPx.toDp() }

        Box(
            modifier = Modifier
                .offset(y = thumbOffset)
                .width(thumbWidth)
                .height(thumbHeight)
                .background(thumbColor, RoundedCornerShape(thumbWidth / 2))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isScrolling = true },
                        onDragEnd = {
                            coroutineScope.launch {
                                delay(1000)
                                isScrolling = false
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            val newOffsetPx = (thumbOffsetPx + dragAmount.y).coerceIn(0f, maxHeightPx - thumbHeightPx)
                            val newProgress = if (maxHeightPx - thumbHeightPx > 0) {
                                newOffsetPx / (maxHeightPx - thumbHeightPx)
                            } else 0f
                            val targetIndex = (newProgress * info.totalItems).toInt().coerceIn(0, info.totalItems - 1)

                            scrollJob?.cancel()
                            scrollJob = coroutineScope.launch {
                                state.scrollToItem(targetIndex)
                            }
                        }
                    )
                }
        )
    }
}

private data class LazyGridScrollbarInfo(
    val totalItems: Int,
    val scrollProgress: Float,
    val visibleRatio: Float
)
