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
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo

    // 如果没有内容或内容不需要滚动，不显示滚动条
    if (totalItems == 0 || visibleItems.isEmpty()) return

    val firstVisibleIndex = visibleItems.firstOrNull()?.index ?: 0
    val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: 0

    // 如果所有内容都可见，不显示滚动条
    if (firstVisibleIndex == 0 && lastVisibleIndex >= totalItems - 1) return

    // 计算滚动进度（0.0 到 1.0）
    val scrollProgress = if (totalItems > 0) {
        firstVisibleIndex.toFloat() / totalItems.toFloat()
    } else 0f

    // 计算滚动条高度比例
    val visibleRatio = (lastVisibleIndex - firstVisibleIndex + 1).toFloat() / totalItems.toFloat()

    // 自动隐藏逻辑
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

        // 计算滚动条高度
        val thumbHeightPx = (maxHeightPx * visibleRatio).coerceAtLeast(thumbMinHeightPx)
        val thumbHeight = with(density) { thumbHeightPx.toDp() }

        // 计算滚动条位置
        val thumbOffsetPx = scrollProgress * (maxHeightPx - thumbHeightPx)
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

                            // 计算拖拽后的目标位置
                            val newOffsetPx = (thumbOffsetPx + dragAmount.y).coerceIn(0f, maxHeightPx - thumbHeightPx)
                            val newProgress = if (maxHeightPx - thumbHeightPx > 0) {
                                newOffsetPx / (maxHeightPx - thumbHeightPx)
                            } else 0f
                            val targetIndex = (newProgress * totalItems).toInt().coerceIn(0, totalItems - 1)

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
