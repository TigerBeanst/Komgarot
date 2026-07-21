package fail.tiger.komgarot.ui.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationOverlayStructureTest {
    private val overlaySource = File("src/main/java/fail/tiger/komgarot/ui/reader/AiTranslationOverlay.kt").readText()
    private val readerSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderScreen.kt").readText()
    private val readerViewModelSource = File("src/main/java/fail/tiger/komgarot/ui/reader/ReaderViewModel.kt").readText()

    @Test
    fun overlayDefinesBinaryIconModes() {
        assertTrue(overlaySource.contains("AiTranslationDisplayMode.OFF"))
        assertTrue(readerSource.contains("AiTranslationDisplayMode.ON"))
        assertTrue(!overlaySource.contains("AiTranslationDisplayMode.PAGE"))
        assertTrue(!overlaySource.contains("AiTranslationDisplayMode.ALL"))
        assertTrue(!overlaySource.contains("\"∞\""))
        assertTrue(!overlaySource.contains("val badge"))
    }

    @Test
    fun readerBottomControlsUseTwoCompactRows() {
        val controlsStart = readerSource.indexOf("private fun ReaderBottomControls(")
        val controlsEnd = readerSource.indexOf("private fun ReaderAiTranslationProgressControl(", controlsStart)
        val controlsSource = readerSource.substring(controlsStart, controlsEnd)

        assertTrue(controlsSource.contains("Slider("))
        assertTrue(controlsSource.contains("text = \"${'$'}{vm.currentPage + 1}/${'$'}{vm.pageUrls.size}\""))
        assertTrue(controlsSource.contains("R.string.reader_quick_preload"))
        assertTrue(controlsSource.contains("ReaderAiTranslationProgressControl("))
        assertFalse(controlsSource.contains("ReaderQuickSettingsRow("))
    }

    @Test
    fun bottomAiControlKeepsProgressAndLongPressActions() {
        val controlStart = readerSource.indexOf("private fun ReaderAiTranslationProgressControl(")
        val controlEnd = readerSource.indexOf("private fun ReaderQuickChipLabel(", controlStart)
        val controlSource = readerSource.substring(controlStart, controlEnd)

        assertTrue(controlSource.contains("readerAiTranslationProgressText(page)"))
        assertTrue(controlSource.contains("R.string.reader_ai_window_progress_short"))
        assertTrue(controlSource.contains("vm.handleAiTranslationButtonClick(preloadPages)"))
        assertTrue(controlSource.contains("vm.showAiTranslationPageActions = true"))
        assertTrue(controlSource.contains("Icons.Default.Translate"))
        assertTrue(controlSource.contains("combinedClickable"))
        assertTrue(controlSource.contains("failed -> MaterialTheme.colorScheme.errorContainer"))
        assertFalse(readerSource.contains("AiTranslationFloatingButton("))
    }

    @Test
    fun readerRendersAiOverlayInPagerAndScrollModes() {
        assertTrue(readerSource.contains("AiTranslationOverlay"))
        assertTrue(readerSource.contains("ReaderAiTranslationProgressControl"))
        assertTrue(readerSource.contains("readerAiStatusStringRes"))
        assertTrue(readerSource.contains("readerAiTranslationProgressText(page)"))
    }

    @Test
    fun overlayUsesRegionStateForResumeProgressAndPlaceholderVisibility() {
        assertTrue(overlaySource.contains("block.regionStatus == AiTranslationRegionStatus.DONE"))
        assertTrue(overlaySource.contains("safe.regionStatus == AiTranslationRegionStatus.PENDING"))
        assertTrue(overlaySource.contains("safe.regionStatus == AiTranslationRegionStatus.RUNNING"))
        assertTrue(overlaySource.contains("if (!hasTranslatedText && !showProcessingPlaceholder) return@forEach"))
    }

    @Test
    fun readerTracksCurrentBookAiTranslationModeWhenPageHasNoSavedMode() {
        assertTrue(readerViewModelSource.contains("var currentAiTranslationMode by mutableStateOf(AiTranslationMode.LOCAL_DETECTION)"))
        assertTrue(readerViewModelSource.contains("fun currentAiTranslationModeForPage(pageIndex: Int): String"))
    }

    @Test
    fun pagerImageAndAiOverlayShareZoomableContainer() {
        assertTrue(readerSource.contains("ZoomableReaderPageContent("))
        val zoomablePageContentStart = readerSource.indexOf("private fun ZoomableReaderPageContent(")
        assertTrue(zoomablePageContentStart >= 0)
        val zoomablePageContent = readerSource.substring(zoomablePageContentStart)
        assertTrue(zoomablePageContent.contains(".zoomable("))
        assertTrue(zoomablePageContent.contains("SubcomposeAsyncImage("))
        assertTrue(zoomablePageContent.contains("AiTranslationOverlay("))
        assertTrue(zoomablePageContent.contains("var pageImageLoaded by remember(pageRequestState.request)"))
        assertTrue(zoomablePageContent.contains("pageImageLoaded = true"))
        assertTrue(zoomablePageContent.contains("page = aiTranslatedPage.takeIf { pageImageLoaded }"))
        assertTrue(zoomablePageContent.contains("fillWidth = pageFit == \"WIDTH\""))
    }

    @Test
    fun scrollReaderWaitsForPageImageBeforeShowingAiOverlay() {
        val scrollStart = readerSource.indexOf("fun ScrollReader(")
        assertTrue(scrollStart >= 0)
        val scrollSource = readerSource.substring(scrollStart)
        assertTrue(scrollSource.contains("var pageImageLoaded by remember(pageRequestState.request)"))
        assertTrue(scrollSource.contains("pageImageLoaded = true"))
        assertTrue(scrollSource.contains("page = vm.currentAiTranslatedPage(index).takeIf { pageImageLoaded }"))
    }

    @Test
    fun overlayUsesOcrMaskPlacementAndColors() {
        assertTrue(overlaySource.contains("BoxWithConstraints"))
        assertTrue(overlaySource.contains("block.rect"))
        assertTrue(overlaySource.contains("val sourceMaskPlacements = aiTranslationSourceMaskRects("))
        assertTrue(overlaySource.contains("val textPlacement = aiTranslationTextPlacement("))
        assertTrue(overlaySource.contains("sourceMaskPlacements = sourceMaskPlacements"))
        assertTrue(overlaySource.contains("pageWidthDp = bounds.width.value"))
        assertTrue(overlaySource.contains("pageHeightDp = bounds.height.value"))
        assertTrue(overlaySource.contains("columns = sourceMaskPlacements"))
        assertTrue(overlaySource.contains("val sourceGapMetrics = aiTranslationSourceColumnMetrics("))
        assertTrue(overlaySource.contains("imageContentBounds("))
        assertTrue(overlaySource.contains("fillWidth: Boolean = false"))
        assertTrue(overlaySource.contains("page.imageWidth"))
        assertTrue(overlaySource.contains("page.imageHeight"))
        assertTrue(overlaySource.contains("parseAiColor(safe.maskColor)"))
        assertTrue(overlaySource.contains("parseAiColor(safe.textColor)"))
        assertTrue(overlaySource.contains("AiTranslationTextDirection.VERTICAL"))
        assertTrue(overlaySource.contains("toVerticalText("))
        assertTrue(overlaySource.contains("coerceIn(0f, 1f)"))
        assertTrue(overlaySource.contains("HorizontalTextLineBackground("))
        assertTrue(overlaySource.contains("VerticalTextColumnBackground("))
        assertTrue(overlaySource.contains("contentAlignment = Alignment.TopCenter"))
        assertTrue(overlaySource.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue(overlaySource.contains("verticalAlignment = Alignment.Top"))
        assertTrue(overlaySource.contains("val textGroupGap = if (safe.textDirection == AiTranslationTextDirection.VERTICAL)"))
        assertTrue(overlaySource.contains("aiTranslationVerticalColumnGapDp(sourceGapMetrics.medianGapDp, safe.kind).dp"))
        assertTrue(overlaySource.contains("horizontalArrangement = Arrangement.spacedBy(textGroupGap)"))
        assertTrue(overlaySource.contains("CompactVerticalTextColumn("))
        assertTrue(overlaySource.contains("letterSpacing = 0.sp"))
        assertTrue(overlaySource.contains("orientation = TextBackgroundOrientation.VERTICAL"))
        assertTrue(overlaySource.contains("private fun Modifier.background("))
        assertTrue(overlaySource.contains("softWrap = true"))
        assertTrue(overlaySource.contains("lineHeight = "))
        assertTrue(!overlaySource.contains("aiTranslationFontScale("))
        assertTrue(overlaySource.contains(".heightIn(min = blockHeight)"))
        assertTrue(!overlaySource.contains(".clipToBounds()"))
        assertTrue(overlaySource.contains("preferredHorizontalLineWidthDp("))
        assertTrue(overlaySource.contains("widthIn(max = maxWidth)"))
        assertTrue(overlaySource.contains("verticalColumnWidthDp("))
        assertTrue(overlaySource.contains("aiTranslationFontSizeSp("))
        assertTrue(overlaySource.contains("textLength = safe.translatedLines.sumOf"))
        assertTrue(overlaySource.contains("sizeFromBox"))
        assertTrue(!overlaySource.contains("sizeFromTextWidth"))
        assertTrue(overlaySource.contains("sizeFromTextHeight"))
        assertTrue(overlaySource.contains("lineHeight = (fontSizeSp * lineHeightMultiplier).sp"))
        assertTrue(overlaySource.contains("AI_TRANSLATION_HORIZONTAL_LINE_HEIGHT_MULTIPLIER = 0.96f"))
        assertTrue(overlaySource.contains("AI_TRANSLATION_VERTICAL_LINE_HEIGHT_MULTIPLIER = 0.92f"))
        assertTrue(overlaySource.contains("maxWidth = Constraints.Infinity"))
        assertTrue(overlaySource.contains("fontWeight = FontWeight.SemiBold"))
        assertTrue(!overlaySource.contains("FontStyle.Italic"))
        assertTrue(!overlaySource.contains("fontStyle ="))
        assertTrue(overlaySource.contains("PlatformTextStyle(includeFontPadding = false)"))
        assertTrue(overlaySource.contains("val inlineTextPadding = if (usesSolidTextBoxMask) 0.dp else 0.5.dp"))
        assertTrue(overlaySource.contains("val verticalColumnHorizontalPadding = if ("))
        assertTrue(overlaySource.contains("AI_TRANSLATION_VERTICAL_TEXT_COLUMN_HORIZONTAL_PADDING_DP.dp"))
        assertTrue(overlaySource.contains("AI_TRANSLATION_SOURCE_TEXT_MASK_PADDING_DP / pageWidthDp"))
        assertTrue(overlaySource.contains("AI_TRANSLATION_SOURCE_TEXT_MASK_PADDING_DP / pageHeightDp"))
        assertTrue(overlaySource.contains("sourceColumnWidthDp - AI_TRANSLATION_VERTICAL_TEXT_COLUMN_HORIZONTAL_PADDING_DP * 2f"))
        assertTrue(overlaySource.contains("sourceColumnWidthDp = sourceColumnMetrics.fontWidthDp"))
        assertTrue(overlaySource.contains("val horizontalLinePadding = if (usesSolidTextBoxMask) 0.dp else 1.dp"))
        assertTrue(overlaySource.contains("padding(horizontal = horizontalPadding, vertical = verticalPadding)"))
        assertTrue(overlaySource.contains("verticalArrangement = Arrangement.spacedBy(textGroupGap)"))
        assertTrue(overlaySource.contains("verticalTextColumnsForDisplay("))
        assertTrue(overlaySource.contains("attachDanglingPunctuationToPrevious()"))
        assertTrue(overlaySource.contains("AI_TRANSLATION_TRAILING_PUNCTUATION"))
        assertTrue(overlaySource.contains("usesSolidAiTranslationMask()"))
        assertTrue(overlaySource.contains("normalTextBoxMask("))
        assertTrue(overlaySource.contains("AiTranslationSourceTextMask("))
        assertTrue(overlaySource.contains("sourceMaskPlacements.forEach { sourceMaskPlacement ->"))
        assertTrue(overlaySource.contains(".width(sourceMaskWidth)"))
        assertTrue(overlaySource.contains(".height(sourceMaskHeight)"))
        assertTrue(overlaySource.contains(".graphicsLayer(rotationZ = safe.rotationDegrees)"))
        assertTrue(overlaySource.contains("Color.Transparent"))
        assertTrue(overlaySource.contains("AI_TRANSLATION_NORMAL_TEXT_MASK_ALPHA"))
        assertTrue(!overlaySource.contains(".background(\n                        parseAiColor(safe.maskColor).copy(alpha = safe.maskAlpha)"))
    }

    @Test
    fun overlayShowsLocalDetectionPlaceholderForEmptyRunningBlocks() {
        assertTrue(overlaySource.contains("val hasTranslatedText = safe.translatedLines.any { it.isNotBlank() }"))
        assertTrue(overlaySource.contains("if (!hasTranslatedText)"))
        assertTrue(overlaySource.contains("AiTranslationRegionPlaceholder("))
        assertTrue(overlaySource.contains("placeholderAlpha = AI_TRANSLATION_PLACEHOLDER_ALPHA"))
        assertTrue(overlaySource.contains("modifier = Modifier"))
        assertTrue(overlaySource.contains(".height(sourceMaskHeight)"))
        val placeholderStart = overlaySource.indexOf("private fun AiTranslationRegionPlaceholder(")
        val placeholderEnd = overlaySource.indexOf("private fun toVerticalText(", placeholderStart)
        val placeholderSource = overlaySource.substring(placeholderStart, placeholderEnd)
        assertTrue(!placeholderSource.contains(".fillMaxSize()"))
    }

    @Test
    fun verticalTextUsesExplicitGlyphAdvanceForCompactInlineSpacing() {
        assertTrue(overlaySource.contains("CompactVerticalTextColumn("))
        assertTrue(overlaySource.contains("verticalGlyphAdvanceDp(fontSizeSp, glyphSpacingMultiplier)"))
        assertTrue(overlaySource.contains("verticalGlyphAdvanceDp(fontSizeSp, glyphSpacingMultiplier).roundToPx()"))
        assertTrue(overlaySource.contains("placeable.placeRelative"))
    }

    @Test
    fun readerPassesConfiguredVerticalGlyphSpacingIntoOverlay() {
        assertTrue(readerSource.contains("val aiVerticalGlyphSpacingPercent by vm.prefs.aiVerticalGlyphSpacingPercent.collectAsStateWithLifecycle"))
        assertTrue(readerSource.contains("aiVerticalGlyphSpacingMultiplier(aiVerticalGlyphSpacingPercent)"))
        assertTrue(readerSource.contains("verticalGlyphSpacingMultiplier = aiVerticalGlyphSpacingMultiplier"))
    }

    @Test
    fun verticalTextUsesUprightPunctuation() {
        assertTrue(overlaySource.contains("'…' -> '︙'"))
        assertTrue(overlaySource.contains("'—' -> '︱'"))
        assertTrue(overlaySource.contains("'ー' -> '｜'"))
        assertTrue(overlaySource.contains("'（' -> '︵'"))
        assertTrue(overlaySource.contains("'）' -> '︶'"))
    }

    @Test
    fun pageContextMenuOmitsAiTestAction() {
        assertTrue(readerSource.contains("PageContextMenu("))
        assertTrue(!readerSource.contains("aiTestModeEnabled ="))
        assertTrue(!readerSource.contains("R.string.reader_ai_test_current_page"))
        assertTrue(!readerSource.contains("vm.testCurrentAiTranslationPage()"))
    }

    @Test
    fun pageContextMenuOwnsReaderSettingsRemovedFromBottomControls() {
        val menuStart = readerSource.indexOf("private fun PageContextMenu(")
        val menuEnd = readerSource.indexOf("private fun saveBitmapToGallery(", menuStart)
        val menuSource = readerSource.substring(menuStart, menuEnd)

        assertTrue(menuSource.contains("vm.prefs.pageFit.collectAsStateWithLifecycle"))
        assertTrue(menuSource.contains("vm.prefs.readingDirection.collectAsStateWithLifecycle"))
        assertTrue(menuSource.contains("vm.prefs.tapPageTurn.collectAsStateWithLifecycle"))
        assertTrue(menuSource.contains("vm.prefs.setPageFit("))
        assertTrue(menuSource.contains("vm.prefs.setReadingDirection("))
        assertTrue(menuSource.contains("vm.prefs.setTapPageTurn("))
        assertTrue(menuSource.contains("R.string.reader_action_cache_status"))
        assertTrue(menuSource.contains("ReaderPageCache.hasCachedFile("))
        assertTrue(menuSource.contains(".verticalScroll(rememberScrollState())"))
    }

    @Test
    fun scrollReaderOpensTheSamePageContextMenuOnLongPress() {
        val scrollStart = readerSource.indexOf("fun ScrollReader(")
        val scrollSource = readerSource.substring(scrollStart)

        assertTrue(scrollSource.contains("var longPressUrl by remember"))
        assertTrue(scrollSource.contains("onLongClick = { longPressUrl = url }"))
        assertTrue(scrollSource.contains("PageContextMenu("))
    }

    @Test
    fun readerShowsAiTranslationErrorsInCopyableDialog() {
        assertTrue(readerSource.contains("aiTranslationMessageNonce"))
        assertTrue(readerSource.contains("aiTranslationMessageRes"))
        assertTrue(readerSource.contains("aiTranslationMessageText"))
        assertTrue(readerSource.contains("vm.aiTranslationMessageText.takeIf { it.isNotBlank() } ?: aiTranslationMessageFallback"))
        assertTrue(readerSource.contains("aiTranslationErrorDialogMessage = message"))
        assertTrue(readerSource.contains("AlertDialog("))
        assertTrue(readerSource.contains("SelectionContainer"))
        assertTrue(readerSource.contains("setPrimaryClip"))
        assertTrue(readerViewModelSource.contains("R.string.reader_ai_test_started"))
        assertTrue(readerViewModelSource.contains("R.string.reader_ai_test_success"))
        assertTrue(readerViewModelSource.contains("R.string.reader_ai_test_failed"))
    }

    @Test
    fun retryCurrentPageRefreshesReaderStateWhenTranslationFinishes() {
        assertTrue(readerViewModelSource.contains("fun retryCurrentAiTranslationPage()"))
        assertTrue(readerViewModelSource.contains("private var currentPages: List<PageDto> = emptyList()"))
        assertTrue(readerViewModelSource.contains("currentPages = pages"))
        assertTrue(readerViewModelSource.contains("viewModelScope.launch"))
        assertTrue(readerViewModelSource.contains("startCurrentAiTranslationPageRetry(loaded, repository, currentPage)"))
        assertTrue(readerViewModelSource.contains("pageIndex: Int"))
        assertTrue(readerViewModelSource.contains("updateAiTranslationPageStatus(pageIndex, AiTranslationPageStatus.RUNNING)"))
        assertTrue(readerViewModelSource.contains("currentAiTranslationDisplayMode = AiTranslationDisplayMode.ON"))
        assertTrue(readerViewModelSource.contains("AiTranslationDisplayMode.OFF -> AiTranslationDisplayMode.ON"))
        assertTrue(readerViewModelSource.contains("AiTranslationDisplayMode.ON -> AiTranslationDisplayMode.OFF"))
        assertTrue(!readerViewModelSource.contains("AiTranslationDisplayMode.PAGE"))
        assertTrue(!readerViewModelSource.contains("AiTranslationDisplayMode.ALL"))
        assertTrue(readerViewModelSource.contains("val result = repository.retryPageTranslation("))
        assertTrue(readerViewModelSource.contains("book = loaded"))
        assertTrue(readerViewModelSource.contains("serverUrl = currentServerUrl"))
        assertTrue(readerViewModelSource.contains("pageIndex = pageIndex"))
        assertTrue(readerViewModelSource.contains("cachedPages = currentPages"))
        assertTrue(readerViewModelSource.contains("onPageUpdated = { page ->"))
        assertTrue(readerViewModelSource.contains("aiTranslatedBook = repository.readBookState(loaded.id)"))
        assertTrue(readerViewModelSource.contains("val pageUpdated = result.ok && updatedPage?.status == AiTranslationPageStatus.DONE"))
        assertTrue(readerViewModelSource.contains("if (!pageUpdated) updateAiTranslationPageStatus(pageIndex, AiTranslationPageStatus.FAILED)"))
        assertTrue(readerViewModelSource.contains("val failureSummary = updatedPage?.errorSummary?.takeIf { it.isNotBlank() } ?: result.summary.takeIf { it.isNotBlank() }"))
        assertTrue(readerViewModelSource.contains("buildAiRetryFallbackSummary("))
        assertTrue(readerViewModelSource.contains("R.string.reader_ai_retry_failed,"))
        assertTrue(!readerViewModelSource.contains("AI translation failed without a saved diagnostic summary."))
        assertTrue(readerViewModelSource.contains("R.string.reader_ai_retry_started"))
        assertTrue(readerViewModelSource.contains("R.string.reader_ai_retry_success"))
        assertTrue(readerViewModelSource.contains("R.string.reader_ai_retry_failed"))
    }

    @Test
    fun readerRefreshesAiTranslationStateWhileCurrentPageIsRunning() {
        assertTrue(readerViewModelSource.contains("fun refreshAiTranslationState()"))
        assertTrue(readerSource.contains("while (vm.currentAiTranslatedPage(vm.currentPage)?.status == AiTranslationPageStatus.RUNNING)"))
        assertTrue(readerSource.contains("vm.refreshAiTranslationState()"))
        assertTrue(readerSource.contains("delay(700)"))
    }

    @Test
    fun readerDisplayChoosesDecodeSizeAndExportKeepsOriginalImageQuality() {
        val rememberRequestStart = readerSource.indexOf("private fun rememberReaderPageRequest(")
        val rememberRequestEnd = readerSource.indexOf("private data class ReaderPageImageRequestState", rememberRequestStart)
        val rememberRequestSource = readerSource.substring(rememberRequestStart, rememberRequestEnd)
        val exportStart = readerSource.indexOf("suspend fun loadBitmap(pageUrl: String)")
        val exportEnd = readerSource.indexOf("val result = imageLoader.execute(req)", exportStart)
        val exportSource = readerSource.substring(exportStart, exportEnd)

        assertTrue(rememberRequestSource.contains("originalSize: Boolean"))
        assertTrue(rememberRequestSource.contains("originalSize = originalSize"))
        assertFalse(readerSource.contains("imageLoader.enqueue("))
        assertTrue(readerSource.contains("ensureReaderPageFileCached("))
        assertTrue(exportSource.contains("originalSize = true"))
    }
}
