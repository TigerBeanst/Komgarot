# Links, Tags, and Offline Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Implement clickable tag filtering, openable Komga metadata links, and book-level offline page caching.

**Architecture:** Add small pure helpers for URL normalization, reader page URL construction, and download state formatting. Wire tag clicks through navigation into `SeriesViewModel` filters. Use existing Coil and `ReaderPageCache` infrastructure to download book pages from `BookDetailViewModel`.

**Tech Stack:** Kotlin, Jetpack Compose, Android Navigation Compose, Coil, Retrofit repositories, JUnit 4.

---

## File Structure

- Modify `app/src/main/java/fail/tiger/komgarot/data/remote/dto/BookSearchDto.kt`: add a `SearchCondition.greaterThan()` helper if needed by tests that inspect condition helpers.
- Modify `app/src/main/java/fail/tiger/komgarot/data/repository/SeriesRepository.kt`: support initial tag filters through existing `SeriesFilters`.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/navigation/Screen.kt`: add `tag` query argument to `Screen.Series`.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt`: pass tag route args and callbacks to detail screens.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/series/SeriesViewModel.kt`: accept `initialTag`, apply `SeriesFilters(tag = value)`, and reset paging when it changes.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/series/SeriesScreen.kt`: accept `initialTag`, show active tag filter in status chips through existing count.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/book/BookScreen.kt`: render series tags as clickable chips and pass `onTagClick`.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailScreen.kt`: render book tags and links, add download button, pass `onTagClick`, invoke URL opener.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailViewModel.kt`: own download state and call the downloader.
- Create `app/src/main/java/fail/tiger/komgarot/ui/metadata/ExternalLinks.kt`: URL normalization and external intent helper.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/metadata/MetadataScreen.kt`: render read-only links as clickable external links.
- Create `app/src/main/java/fail/tiger/komgarot/ui/reader/ReaderPageUrl.kt`: shared page URL construction.
- Modify `app/src/main/java/fail/tiger/komgarot/ui/reader/ReaderViewModel.kt`: use shared page URL function.
- Create `app/src/main/java/fail/tiger/komgarot/data/local/BookDownloadCache.kt`: download/cache pages and thumbnails.
- Modify `app/src/main/res/values/strings.xml` and `app/src/main/res/values-zh-rCN/strings.xml`: add labels for links and download states.
- Modify `app/src/test/java/fail/tiger/komgarot/data/repository/SeriesRepositoryTest.kt`: add tag filter assertion.
- Create `app/src/test/java/fail/tiger/komgarot/ui/metadata/ExternalLinksTest.kt`: URL normalization tests.
- Create `app/src/test/java/fail/tiger/komgarot/ui/bookdetail/BookDownloadStateTest.kt`: download state label tests.
- Modify `app/src/test/java/fail/tiger/komgarot/ui/navigation/ScreenTest.kt`: verify tag route encoding.

### Task 1: External URL Normalization

**Files:**
- Create: `app/src/main/java/fail/tiger/komgarot/ui/metadata/ExternalLinks.kt`
- Test: `app/src/test/java/fail/tiger/komgarot/ui/metadata/ExternalLinksTest.kt`

- [x] **Step 1: Write the failing tests**

```kotlin
package fail.tiger.komgarot.ui.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLinksTest {
    @Test
    fun blankUrlReturnsNull() {
        assertNull(normalizeExternalUrl("   "))
    }

    @Test
    fun hostWithoutSchemeGetsHttpsScheme() {
        assertEquals("https://example.com/title", normalizeExternalUrl(" example.com/title "))
    }

    @Test
    fun existingSchemeIsPreserved() {
        assertEquals("komga://series/1", normalizeExternalUrl("komga://series/1"))
        assertEquals("https://example.com", normalizeExternalUrl("https://example.com"))
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.metadata.ExternalLinksTest`

Expected: FAIL because `normalizeExternalUrl` is unresolved.

- [x] **Step 3: Add minimal implementation**

```kotlin
package fail.tiger.komgarot.ui.metadata

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fail.tiger.komgarot.R

fun normalizeExternalUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val uri = Uri.parse(trimmed)
    return if (uri.scheme.isNullOrBlank()) "https://$trimmed" else trimmed
}

fun openExternalUrl(context: Context, value: String) {
    val normalized = normalizeExternalUrl(value) ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.operation_failed), Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, context.getString(R.string.operation_failed), Toast.LENGTH_SHORT).show()
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.metadata.ExternalLinksTest`

Expected: PASS.

### Task 2: Shared Reader Page URL Builder

**Files:**
- Create: `app/src/main/java/fail/tiger/komgarot/ui/reader/ReaderPageUrl.kt`
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/reader/ReaderViewModel.kt`
- Test: `app/src/test/java/fail/tiger/komgarot/ui/reader/ReaderPageRequestsTest.kt`

- [x] **Step 1: Write the failing tests**

Add to `ReaderPageRequestsTest`:

```kotlin
@Test
fun directImagePageUrlUsesOriginalEndpoint() {
    val page = PageDto(number = 3, mediaType = "image/jpeg", width = 100, height = 200)

    assertEquals("https://komga.test/api/v1/books/book-1/pages/3", readerPageUrl("https://komga.test", "book-1", page))
}

@Test
fun nonDirectImagePageUrlRequestsPngConversion() {
    val page = PageDto(number = 4, mediaType = "application/pdf", width = 100, height = 200)

    assertEquals("https://komga.test/api/v1/books/book-1/pages/4?convert=png", readerPageUrl("https://komga.test", "book-1", page))
}
```

Also add import:

```kotlin
import fail.tiger.komgarot.data.remote.dto.PageDto
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.reader.ReaderPageRequestsTest`

Expected: FAIL because `readerPageUrl` is private in `ReaderViewModel`.

- [x] **Step 3: Add shared function and remove duplicate private logic**

Create `ReaderPageUrl.kt`:

```kotlin
package fail.tiger.komgarot.ui.reader

import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.remote.dto.PageDto

private val directImageMediaTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
    "image/jxl",
    "image/heif",
    "image/avif"
)

fun readerPageUrl(serverUrl: String, bookId: String, page: PageDto): String {
    val url = KomgaUrls.page(serverUrl, bookId, page.number)
    return if (page.mediaType.lowercase() in directImageMediaTypes) url else "$url?convert=png"
}
```

In `ReaderViewModel.kt`, delete the private `directImageMediaTypes` and private `readerPageUrl()` definitions. Existing calls will resolve to the shared function.

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.reader.ReaderPageRequestsTest`

Expected: PASS.

### Task 3: Tag Filter Routing

**Files:**
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/series/SeriesViewModel.kt`
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/series/SeriesScreen.kt`
- Test: `app/src/test/java/fail/tiger/komgarot/ui/navigation/ScreenTest.kt`
- Test: `app/src/test/java/fail/tiger/komgarot/data/repository/SeriesRepositoryTest.kt`

- [x] **Step 1: Write failing tests**

Add to `ScreenTest`:

```kotlin
@Test
fun seriesRouteEncodesTagFilter() {
    assertEquals("series/all?tag=sci%20fi", Screen.Series.go(id = null, tag = "sci fi"))
}
```

Add to `SeriesRepositoryTest`:

```kotlin
@Test
fun tagFilterCreatesSeriesTagCondition() {
    val search = buildSeriesSearch(libraryId = null, filters = SeriesFilters(tag = "sci fi"))

    val condition = search.condition.orEmpty()
    assertEquals("SERIES", condition["operator"])
    val tag = condition["tag"].asMap()
    assertEquals("IS", tag["operator"])
    assertEquals("sci fi", tag["value"])
}
```

- [x] **Step 2: Run tests to verify Screen test fails and repository test passes or confirms existing behavior**

Run:
`./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.navigation.ScreenTest --tests fail.tiger.komgarot.data.repository.SeriesRepositoryTest`

Expected: `ScreenTest` FAILS because `tag` is unsupported. `SeriesRepositoryTest` should PASS if existing tag filter is correct.

- [x] **Step 3: Implement route and view model initialization**

Change `Screen.Series`:

```kotlin
object Series : Screen("series/{libraryId}?search={search}&tag={tag}") {
    fun go(id: String?, search: String? = null, tag: String? = null): String {
        val base = "series/${encodeArg(id ?: "all")}"
        val params = listOfNotNull(
            search?.let { "search=${encodeArg(it)}" },
            tag?.let { "tag=${encodeArg(it)}" }
        )
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }
}
```

Change `SeriesViewModel.init()` signature and initialization:

```kotlin
fun init(id: String?, initialSearch: String? = null, initialTag: String? = null) {
    val normalizedInitialSearch = initialSearch?.trim().orEmpty()
    val normalizedInitialTag = initialTag?.trim()?.takeIf { it.isNotEmpty() }
    if (libraryId != id) {
        libraryId = id
        paging.reset()
        initialized = false
    }
    if (normalizedInitialSearch != searchQuery) {
        initialized = false
        paging.reset()
        applySearchState(normalizedInitialSearch)
    }
    if (normalizedInitialTag != filters.tag) {
        initialized = false
        paging.reset()
        filters = filters.copy(tag = normalizedInitialTag)
    }
    if (!initialized) {
        initialized = true
        loadMore()
    }
}
```

Change `SeriesScreen` signature and launch:

```kotlin
fun SeriesScreen(
    libraryId: String?,
    initialSearch: String? = null,
    initialTag: String? = null,
    ...
) {
    LaunchedEffect(libraryId, initialSearch, initialTag) { vm.init(libraryId, initialSearch, initialTag) }
}
```

In `AppNavGraph`, add route arg:

```kotlin
navArgument("tag") { type = NavType.StringType; nullable = true; defaultValue = null }
```

Decode and pass:

```kotlin
val tag = back.arguments?.getString("tag")?.let { Screen.decodeArg(it) }
SeriesScreen(..., initialTag = tag, ...)
```

- [x] **Step 4: Run tests to verify pass**

Run:
`./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.navigation.ScreenTest --tests fail.tiger.komgarot.data.repository.SeriesRepositoryTest`

Expected: PASS.

### Task 4: Clickable Tags and Links in Detail UI

**Files:**
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/book/BookScreen.kt`
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailScreen.kt`
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

- [x] **Step 1: Add UI callback signatures**

In `BookScreen`:

```kotlin
onTagClick: (String) -> Unit = {},
```

In `BookDetailScreen`:

```kotlin
onTagClick: (String) -> Unit = {},
```

Wire navigation callbacks:

```kotlin
onTagClick = { tag -> navController.navigate(Screen.Series.go(id = null, tag = tag)) }
```

- [x] **Step 2: Render series tags as chips**

In `BookScreen`, replace plain tags text with:

```kotlin
if (series.metadata.tags.isNotEmpty()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        series.metadata.tags.forEach { tag ->
            AssistChip(onClick = { onTagClick(tag) }, label = { Text(tag) })
        }
    }
}
```

Add `ExperimentalLayoutApi` opt-in and needed imports.

- [x] **Step 3: Render book tags and links**

In `BookDetailScreen`, replace tag `InfoRow` with chips:

```kotlin
if (!meta?.tags.isNullOrEmpty()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        meta!!.tags.forEach { tag ->
            AssistChip(onClick = { onTagClick(tag) }, label = { Text(tag) })
        }
    }
}
```

Add links below tags:

```kotlin
if (!meta?.links.isNullOrEmpty()) {
    Text(stringResource(R.string.metadata_links), style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        meta!!.links.filter { normalizeExternalUrl(it.url) != null }.forEach { link ->
            AssistChip(
                onClick = { openExternalUrl(context, link.url) },
                label = { Text(link.label.ifBlank { link.url }) }
            )
        }
    }
}
```

- [x] **Step 4: Add strings**

Add to English strings:

```xml
<string name="metadata_links">Links</string>
```

Add to Simplified Chinese strings:

```xml
<string name="metadata_links">外链</string>
```

- [x] **Step 5: Build compile check**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.navigation.ScreenTest`

Expected: PASS and Kotlin compile succeeds.

### Task 5: Metadata Read-Only Link Rendering

**Files:**
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/metadata/MetadataScreen.kt`

- [x] **Step 1: Find existing `LinksEditor` behavior**

Inspect `LinksEditor` in `MetadataScreen.kt` and identify the `editing == false` rendering path.

- [x] **Step 2: Replace read-only link text with clickable chips**

Use existing `LinksEditor(links, editing, onChange)` signature. In the non-editing branch, render:

```kotlin
val context = LocalContext.current
val visibleLinks = links.filter { normalizeExternalUrl(it.url) != null }
if (visibleLinks.isEmpty()) {
    Text(stringResource(R.string.metadata_no_links), color = MaterialTheme.colorScheme.onSurfaceVariant)
} else {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visibleLinks.forEach { link ->
            AssistChip(
                onClick = { openExternalUrl(context, link.url) },
                label = { Text(link.label.ifBlank { link.url }) }
            )
        }
    }
}
```

- [x] **Step 3: Run compile check**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.metadata.ExternalLinksTest`

Expected: PASS and Kotlin compile succeeds.

### Task 6: Book Download State and Cache Helper

**Files:**
- Create: `app/src/main/java/fail/tiger/komgarot/data/local/BookDownloadCache.kt`
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailViewModel.kt`
- Test: `app/src/test/java/fail/tiger/komgarot/ui/bookdetail/BookDownloadStateTest.kt`

- [x] **Step 1: Write failing state tests**

```kotlin
package fail.tiger.komgarot.ui.bookdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDownloadStateTest {
    @Test
    fun downloadingStateExposesProgress() {
        val state = BookDownloadState.Downloading(completedPages = 2, totalPages = 5)

        assertEquals(2, state.completedPages)
        assertEquals(5, state.totalPages)
        assertTrue(state.isRunning)
    }

    @Test
    fun cachedStateIsComplete() {
        val state = BookDownloadState.Cached(totalPages = 5)

        assertEquals(5, state.totalPages)
        assertEquals(false, state.isRunning)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.bookdetail.BookDownloadStateTest`

Expected: FAIL because `BookDownloadState` is unresolved.

- [x] **Step 3: Add state model**

In `BookDetailViewModel.kt`:

```kotlin
sealed interface BookDownloadState {
    val isRunning: Boolean get() = false

    data object Idle : BookDownloadState
    data class Downloading(val completedPages: Int, val totalPages: Int) : BookDownloadState {
        override val isRunning: Boolean get() = true
    }
    data class Cached(val totalPages: Int) : BookDownloadState
    data class Failed(val message: String) : BookDownloadState
}
```

- [x] **Step 4: Create cache helper**

```kotlin
package fail.tiger.komgarot.data.local

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import fail.tiger.komgarot.data.remote.KomgaUrls
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.repository.BookRepository
import fail.tiger.komgarot.ui.reader.readerPageRequest
import fail.tiger.komgarot.ui.reader.readerPageUrl
import fail.tiger.komgarot.ThumbnailVersion

data class BookDownloadProgress(val completedPages: Int, val totalPages: Int)

class BookDownloadCache(
    private val context: Context,
    private val repo: BookRepository
) {
    suspend fun cacheBook(
        serverUrl: String,
        bookId: String,
        knownBook: BookDto?,
        onProgress: (BookDownloadProgress) -> Unit
    ): Int {
        val book = knownBook ?: repo.getBookById(bookId).getOrThrow()
        val pages = repo.getPages(bookId)
        val imageLoader = context.imageLoader
        cacheThumbnail(serverUrl, book.id)
        var completed = 0
        onProgress(BookDownloadProgress(completed, pages.size))
        pages.forEach { page ->
            val url = readerPageUrl(serverUrl, book.id, page)
            if (!ReaderPageCache.hasCachedFile(context, book.seriesId, book.id, url)) {
                val request = readerPageRequest(
                    context = context,
                    url = url,
                    seriesId = book.seriesId,
                    bookId = book.id,
                    cacheVersion = ThumbnailVersion.get(book.id),
                    allowHardware = false,
                    originalSize = true
                )
                imageLoader.execute(request)
            }
            completed++
            onProgress(BookDownloadProgress(completed, pages.size))
        }
        return pages.size
    }

    private suspend fun cacheThumbnail(serverUrl: String, bookId: String) {
        val thumbnailVersion = ThumbnailVersion.get(bookId)
        val url = KomgaUrls.bookThumbnail(serverUrl, bookId, thumbnailVersion)
        val key = thumbnailCacheKey(ThumbnailCacheTarget.Book(bookId))
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
        context.imageLoader.execute(request)
    }
}
```

- [x] **Step 5: Inject and use downloader in view model**

Update constructor:

```kotlin
private val downloadCache: BookDownloadCache,
```

Add state:

```kotlin
var downloadState by mutableStateOf<BookDownloadState>(BookDownloadState.Idle)
```

Add function:

```kotlin
fun downloadForOffline(serverUrl: String) {
    if (downloadState.isRunning || currentBookId.isBlank()) return
    viewModelScope.launch {
        downloadState = BookDownloadState.Downloading(0, 0)
        runCatching {
            downloadCache.cacheBook(serverUrl, currentBookId, book) { progress ->
                downloadState = BookDownloadState.Downloading(progress.completedPages, progress.totalPages)
            }
        }.onSuccess { total ->
            downloadState = BookDownloadState.Cached(total)
        }.onFailure { throwable ->
            downloadState = BookDownloadState.Failed(throwable.message?.takeIf { it.isNotBlank() } ?: loadBookDetailFailed)
        }
    }
}
```

Update `Factory` to accept `context` or `BookDownloadCache`, and instantiate with `BookDownloadCache(context.applicationContext, bookRepo)`.

- [x] **Step 6: Run state test**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.bookdetail.BookDownloadStateTest`

Expected: PASS.

### Task 7: Download Button UI

**Files:**
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/bookdetail/BookDetailScreen.kt`
- Modify: `app/src/main/java/fail/tiger/komgarot/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

- [x] **Step 1: Update view model factory usage**

In `AppNavGraph`, change `BookDetailViewModel.Factory` call to include `app.applicationContext` or a downloader dependency according to Task 6 implementation.

- [x] **Step 2: Add strings**

English:

```xml
<string name="download_for_offline">Download</string>
<string name="download_for_offline_progress">Downloading %1$d / %2$d</string>
<string name="download_for_offline_cached">Cached %d pages</string>
<string name="download_for_offline_failed">Download failed</string>
```

Simplified Chinese:

```xml
<string name="download_for_offline">下载</string>
<string name="download_for_offline_progress">下载中 %1$d / %2$d</string>
<string name="download_for_offline_cached">已缓存 %d 页</string>
<string name="download_for_offline_failed">下载失败</string>
```

- [x] **Step 3: Add button near reading actions**

After `BookDetailReadingActions`, add:

```kotlin
BookDownloadAction(
    state = vm.downloadState,
    onClick = { vm.downloadForOffline(serverUrl) }
)
```

Add composable:

```kotlin
@Composable
private fun BookDownloadAction(
    state: BookDownloadState,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = !state.isRunning,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        val text = when (state) {
            BookDownloadState.Idle -> stringResource(R.string.download_for_offline)
            is BookDownloadState.Downloading -> stringResource(
                R.string.download_for_offline_progress,
                state.completedPages,
                state.totalPages
            )
            is BookDownloadState.Cached -> stringResource(R.string.download_for_offline_cached, state.totalPages)
            is BookDownloadState.Failed -> stringResource(R.string.download_for_offline_failed)
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
```

- [x] **Step 4: Run compile check**

Run: `./gradlew testDebugUnitTest --tests fail.tiger.komgarot.ui.bookdetail.BookDownloadStateTest`

Expected: PASS and Kotlin compile succeeds.

### Task 8: Full Verification

**Files:**
- All files changed above.

- [x] **Step 1: Run unit tests**

Run: `./gradlew testDebugUnitTest`

Expected: PASS.

- [x] **Step 2: Inspect git diff**

Run: `git diff --stat`

Expected: Changes are scoped to navigation, metadata links, detail UI, download cache, strings, and tests.

- [x] **Step 3: Commit implementation**

```bash
git add app/src/main/java app/src/main/res app/src/test/java docs/superpowers/plans/2026-06-05-links-tags-offline-cache.md
git commit -m "实现标签筛选链接跳转和离线缓存"
```
