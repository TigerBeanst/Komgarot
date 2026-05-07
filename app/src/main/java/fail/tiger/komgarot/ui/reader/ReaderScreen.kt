package fail.tiger.komgarot.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    startPage: Int,
    trackProgress: Boolean = true,
    onBack: () -> Unit,
    vm: ReaderViewModel
) {
    LaunchedEffect(bookId) { vm.load(bookId, startPage, trackProgress) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (vm.mode) {
            ReadingMode.PAGER -> PagerReader(vm)
            ReadingMode.SCROLL -> ScrollReader(vm)
        }

        if (vm.showControls) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Surface(color = Color.Black.copy(alpha = 0.6f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${vm.currentPage + 1} / ${vm.pageUrls.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.toggleMode() }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Toggle mode", tint = Color.White)
                        }
                    }
                }

                Surface(color = Color.Black.copy(alpha = 0.8f)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (vm.pageUrls.isNotEmpty()) {
                            Text(
                                "${vm.currentPage + 1} / ${vm.pageUrls.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { if (vm.currentPage > 0) vm.goToPage(vm.currentPage - 1) },
                                    enabled = vm.currentPage > 0
                                ) {
                                    Icon(Icons.Default.NavigateBefore, contentDescription = "Previous", tint = Color.White)
                                }
                                Slider(
                                    value = vm.currentPage.toFloat(),
                                    onValueChange = { vm.goToPage(it.toInt()) },
                                    valueRange = 0f..(vm.pageUrls.size - 1).toFloat(),
                                    steps = (vm.pageUrls.size - 2).coerceAtLeast(0),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { if (vm.currentPage < vm.pageUrls.size - 1) vm.goToPage(vm.currentPage + 1) },
                                    enabled = vm.currentPage < vm.pageUrls.size - 1
                                ) {
                                    Icon(Icons.Default.NavigateNext, contentDescription = "Next", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerReader(vm: ReaderViewModel) {
    if (vm.pageUrls.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = vm.currentPage) { vm.pageUrls.size }

    LaunchedEffect(pagerState.currentPage) {
        vm.updatePage(pagerState.currentPage)
    }
    LaunchedEffect(vm.currentPage) {
        if (pagerState.currentPage != vm.currentPage) {
            pagerState.scrollToPage(vm.currentPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val zoomState = rememberZoomState(maxScale = 5f)

        LaunchedEffect(page) {
            zoomState.reset()
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { vm.toggleControls() } },
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(vm.pageUrls[page])
                    .size(Size.ORIGINAL)
                    .build(),
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().zoomable(zoomState)
            ) {
                val imageState = painter.state
                if (imageState is coil.compose.AsyncImagePainter.State.Loading) {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp
                        )
                    }
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
fun ScrollReader(vm: ReaderViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { vm.toggleControls() }
        }
    ) {
        items(vm.pageUrls.indices.toList()) { index ->
            Box(Modifier.fillMaxWidth().wrapContentHeight(), contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = vm.pageUrls[index],
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val state = painter.state
                    if (state is coil.compose.AsyncImagePainter.State.Loading) {
                        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
        }
    }
}
