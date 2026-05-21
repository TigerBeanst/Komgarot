package fail.tiger.komgarot.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

private const val ScrollDirectionThresholdPx = 8
private val BottomNavigationBarHeight = 80.dp

@Composable
fun topLevelScrollableContentPadding(
    start: Dp = 16.dp,
    top: Dp = 16.dp,
    end: Dp = 16.dp,
    bottom: Dp = 16.dp
): PaddingValues =
    PaddingValues(
        start = start,
        top = top,
        end = end,
        bottom = bottom + BottomNavigationBarHeight +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )

@Composable
fun AutoHideBottomBarOnLazyListScroll(
    state: LazyListState,
    onVisibleChange: (Boolean) -> Unit
) {
    val currentOnVisibleChange = rememberUpdatedState(onVisibleChange)
    LaunchedEffect(state) {
        var previousIndex = state.firstVisibleItemIndex
        var previousOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                reportBottomBarVisibility(
                    index = index,
                    offset = offset,
                    previousIndex = previousIndex,
                    previousOffset = previousOffset,
                    onVisibleChange = currentOnVisibleChange.value
                )
                previousIndex = index
                previousOffset = offset
            }
    }
}

@Composable
fun AutoHideBottomBarOnLazyGridScroll(
    state: LazyGridState,
    onVisibleChange: (Boolean) -> Unit
) {
    val currentOnVisibleChange = rememberUpdatedState(onVisibleChange)
    LaunchedEffect(state) {
        var previousIndex = state.firstVisibleItemIndex
        var previousOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                reportBottomBarVisibility(
                    index = index,
                    offset = offset,
                    previousIndex = previousIndex,
                    previousOffset = previousOffset,
                    onVisibleChange = currentOnVisibleChange.value
                )
                previousIndex = index
                previousOffset = offset
            }
    }
}

private fun reportBottomBarVisibility(
    index: Int,
    offset: Int,
    previousIndex: Int,
    previousOffset: Int,
    onVisibleChange: (Boolean) -> Unit
) {
    if (index == 0 && offset == 0) {
        onVisibleChange(true)
        return
    }

    val scrollingDown = index > previousIndex ||
        (index == previousIndex && offset - previousOffset > ScrollDirectionThresholdPx)
    val scrollingUp = index < previousIndex ||
        (index == previousIndex && previousOffset - offset > ScrollDirectionThresholdPx)

    when {
        scrollingDown -> onVisibleChange(false)
        scrollingUp -> onVisibleChange(true)
    }
}
