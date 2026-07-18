package fail.tiger.komgarot.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fail.tiger.komgarot.KomgarotApp
import fail.tiger.komgarot.BuildConfig
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.local.ImageCacheInvalidator
import fail.tiger.komgarot.data.remote.dto.BookDto
import fail.tiger.komgarot.data.repository.deviceProfile
import fail.tiger.komgarot.data.repository.recommendAiLocalModelTier
import fail.tiger.komgarot.ui.aitranslation.AiTranslationTaskScreen
import fail.tiger.komgarot.ui.aitranslation.AiTranslationTaskViewModel
import fail.tiger.komgarot.ui.admin.AdminScreen
import fail.tiger.komgarot.ui.admin.AdminViewModel
import fail.tiger.komgarot.ui.book.BookScreen
import fail.tiger.komgarot.ui.book.BookViewModel
import fail.tiger.komgarot.ui.bookdetail.BookDetailScreen
import fail.tiger.komgarot.ui.bookdetail.BookDetailViewModel
import fail.tiger.komgarot.ui.cached.CachedBooksScreen
import fail.tiger.komgarot.ui.cached.CachedBooksViewModel
import fail.tiger.komgarot.ui.collection.CollectionDetailScreen
import fail.tiger.komgarot.ui.collection.CollectionScreen
import fail.tiger.komgarot.ui.collection.CollectionViewModel
import fail.tiger.komgarot.ui.i18n.AndroidUiTextProvider
import fail.tiger.komgarot.ui.library.LibraryScreen
import fail.tiger.komgarot.ui.library.LibraryViewModel
import fail.tiger.komgarot.ui.login.LoginScreen
import fail.tiger.komgarot.ui.login.LoginViewModel
import fail.tiger.komgarot.ui.metadata.canEditKomgaMetadata
import fail.tiger.komgarot.ui.metadata.MetadataScreen
import fail.tiger.komgarot.ui.metadata.MetadataViewModel
import fail.tiger.komgarot.ui.readlist.ReadListDetailScreen
import fail.tiger.komgarot.ui.readlist.ReadListScreen
import fail.tiger.komgarot.ui.readlist.ReadListViewModel
import fail.tiger.komgarot.ui.reader.ReaderScreen
import fail.tiger.komgarot.ui.reader.ReaderViewModel
import fail.tiger.komgarot.ui.series.SharedPreferencesSeriesSortStore
import fail.tiger.komgarot.ui.series.SeriesScreen
import fail.tiger.komgarot.ui.series.SeriesViewModel
import fail.tiger.komgarot.ui.settings.MeScreen
import fail.tiger.komgarot.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(app: KomgarotApp) {
    val navController = rememberNavController()
    val imageCacheInvalidator = remember(app) { ImageCacheInvalidator(app.applicationContext) }
    val textProvider = remember(app) { AndroidUiTextProvider(app.applicationContext) }
    val sessionVm: SessionViewModel = viewModel(factory = SessionViewModel.Factory(app.userRepository))
    val serverUrl by app.authPreferences.serverUrl.collectAsStateWithLifecycle()
    val alwaysIncognito by app.authPreferences.alwaysIncognito.collectAsStateWithLifecycle(initialValue = false)
    val einkMode by app.authPreferences.einkMode.collectAsStateWithLifecycle(initialValue = false)
    val sessionState by sessionVm.state.collectAsStateWithLifecycle()
    val user = sessionState.userOrNull
    val canEditMetadata = canEditKomgaMetadata(user)
    val aiTranslationAvailable = BuildConfig.AI_TRANSLATION_AVAILABLE
    val startDest = if (serverUrl.isNotEmpty()) Screen.Library.route else Screen.Login.route
    val scope = rememberCoroutineScope()

    LaunchedEffect(serverUrl) {
        if (serverUrl.isNotEmpty()) {
            sessionVm.refresh(force = true)
        }
        if (serverUrl.isNotEmpty() && navController.currentDestination?.route != Screen.Library.route) {
            navController.navigate(Screen.Library.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (serverUrl.isNotEmpty()) sessionVm.refresh(force = true)
    }

    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.AuthenticationRequired && navController.currentDestination?.route != Screen.Login.route) {
            navController.navigate(Screen.Login.route) {
                launchSingleTop = true
            }
        }
    }

    val slideDistance = rememberSlideDistance()
    val openSeries: (String, Int) -> Unit = { seriesId, bookCount ->
        navController.navigate(Screen.Books.go(seriesId, bookCount)) {
            launchSingleTop = true
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelDestinations = topLevelDestinations()
    val showTopLevelNav = currentRoute in topLevelDestinations.map { it.route }
    var bottomBarVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(currentRoute, showTopLevelNav) {
        if (showTopLevelNav) bottomBarVisible = true
    }

    fun navigateTopLevel(route: String) {
        if (route == Screen.Library.route && currentRoute != Screen.Library.route) {
            val popped = navController.popBackStack(Screen.Library.route, inclusive = false)
            if (popped) return
        }
        navController.navigate(route) {
            popUpTo(Screen.Library.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openReaderBookFromBoundary(book: BookDto, shouldTrack: Boolean) {
        val detailRoute = Screen.BookDetail.go(
            book.id,
            book.metadata.title.ifEmpty { book.name },
            book.seriesTitle.orEmpty(),
            book.media.pagesCount,
            book.oneshot
        )
        navController.navigate(detailRoute) {
            popUpTo(Screen.BookDetail.route) { inclusive = true }
        }
        navController.navigate(Screen.Reader.go(book.id, 1, shouldTrack)) {
            launchSingleTop = true
        }
    }

    AdaptiveShell(
        destinations = topLevelDestinations,
        currentRoute = currentRoute,
        showTopLevelNav = showTopLevelNav,
        bottomBarVisible = bottomBarVisible,
        einkMode = einkMode,
        onDestinationClick = { destination -> navigateTopLevel(destination.route) }
    ) { shellModifier ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = shellModifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            enterTransition = {
                if (einkMode) fadeIn(tween(0)) else materialSharedAxisX(forward = true, slideDistance = slideDistance).targetContentEnter
            },
            exitTransition = {
                if (einkMode) fadeOut(tween(0)) else materialSharedAxisX(forward = true, slideDistance = slideDistance).initialContentExit
            },
            popEnterTransition = {
                if (einkMode) fadeIn(tween(0)) else materialSharedAxisX(forward = false, slideDistance = slideDistance).targetContentEnter
            },
            popExitTransition = {
                if (einkMode) fadeOut(tween(0)) else materialSharedAxisX(forward = false, slideDistance = slideDistance).initialContentExit
            }
        ) {
        composable(Screen.Login.route) {
            val vm: LoginViewModel = viewModel(factory = LoginViewModel.Factory(app.authRepository, textProvider))
            LoginScreen(onSuccess = {
                scope.launch {
                    sessionVm.refreshCurrentUser()
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }, vm = vm)
        }

        composable(Screen.Library.route) {
            val vm: LibraryViewModel = viewModel(
                factory = LibraryViewModel.Factory(app.libraryRepository, app.authRepository, textProvider)
            )
            LibraryScreen(
                onLibraryClick = { navController.navigate(Screen.Series.go(it)) },
                onBookClick = { book ->
                    navController.navigate(
                        Screen.BookDetail.go(
                            book.id,
                            book.metadata.title.ifEmpty { book.name },
                            book.seriesTitle.orEmpty(),
                            book.media.pagesCount,
                            book.oneshot
                        )
                    )
                },
                onSeriesClick = openSeries,
                serverUrl = serverUrl,
                onBottomBarVisibleChange = { bottomBarVisible = it },
                vm = vm
            )
        }

        composable(Screen.Browse.route) {
            val vm: SeriesViewModel = viewModel(
                factory = SeriesViewModel.Factory(
                    app.seriesRepository,
                    app.bookRepository,
                    SharedPreferencesSeriesSortStore(app.applicationContext),
                    textProvider
                )
            )
            SeriesScreen(
                libraryId = null,
                serverUrl = serverUrl,
                onSeriesClick = openSeries,
                onMetadataClick = { navController.navigate(Screen.Metadata.go("series", it)) },
                onBack = { navController.navigate(Screen.Library.route) },
                vm = vm,
                onBottomBarVisibleChange = { bottomBarVisible = it }
            )
        }

        composable(Screen.Collections.route) {
            val vm: CollectionViewModel = viewModel(factory = CollectionViewModel.Factory(app.collectionRepository, textProvider))
            CollectionScreen(
                serverUrl = serverUrl,
                vm = vm,
                onCollectionClick = { navController.navigate(Screen.CollectionDetail.go(it)) },
                onBack = { navController.navigate(Screen.Library.route) },
                onBottomBarVisibleChange = { bottomBarVisible = it }
            )
        }

        composable(
            Screen.CollectionDetail.route,
            arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
        ) { back ->
            val collectionId = back.arguments?.getString("collectionId")?.let { Screen.decodeArg(it) } ?: return@composable
            val vm: CollectionViewModel = viewModel(factory = CollectionViewModel.Factory(app.collectionRepository, textProvider))
            CollectionDetailScreen(
                collectionId = collectionId,
                serverUrl = serverUrl,
                vm = vm,
                onSeriesClick = openSeries,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ReadLists.route) {
            val vm: ReadListViewModel = viewModel(factory = ReadListViewModel.Factory(app.readListRepository, textProvider))
            ReadListScreen(
                serverUrl = serverUrl,
                vm = vm,
                onReadListClick = { navController.navigate(Screen.ReadListDetail.go(it)) },
                onBack = { navController.navigate(Screen.Library.route) },
                onBottomBarVisibleChange = { bottomBarVisible = it }
            )
        }

        composable(
            Screen.ReadListDetail.route,
            arguments = listOf(navArgument("readListId") { type = NavType.StringType })
        ) { back ->
            val readListId = back.arguments?.getString("readListId")?.let { Screen.decodeArg(it) } ?: return@composable
            val vm: ReadListViewModel = viewModel(factory = ReadListViewModel.Factory(app.readListRepository, textProvider))
            ReadListDetailScreen(
                readListId = readListId,
                serverUrl = serverUrl,
                vm = vm,
                onBookClick = { book ->
                    navController.navigate(
                        Screen.BookDetail.go(
                            book.id,
                            book.metadata.title.ifEmpty { book.name },
                            book.seriesTitle.orEmpty(),
                            book.media.pagesCount,
                            book.oneshot
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Admin.route) {
            val vm: AdminViewModel = viewModel(factory = AdminViewModel.Factory(app.adminRepository, textProvider))
            AdminScreen(
                isAdmin = canEditMetadata,
                vm = vm,
                onBack = { navController.navigate(Screen.Library.route) }
            )
        }

        composable(
            Screen.Series.route,
            arguments = listOf(
                navArgument("libraryId") { type = NavType.StringType },
                navArgument("search") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("tag") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { back ->
            val libraryId = back.arguments?.getString("libraryId")?.let { Screen.decodeArg(it) }?.takeIf { it != "all" }
            val searchQuery = back.arguments?.getString("search")?.let { Screen.decodeArg(it) }
            val tag = back.arguments?.getString("tag")?.let { Screen.decodeArg(it) }
            val vm: SeriesViewModel = viewModel(
                factory = SeriesViewModel.Factory(
                    app.seriesRepository,
                    app.bookRepository,
                    SharedPreferencesSeriesSortStore(app.applicationContext),
                    textProvider
                )
            )

            SeriesScreen(
                libraryId = libraryId,
                initialSearch = searchQuery,
                initialTag = tag,
                serverUrl = serverUrl,
                onSeriesClick = openSeries,
                onMetadataClick = { navController.navigate(Screen.Metadata.go("series", it)) },
                onBack = { navController.popBackStack() }, vm = vm
            )
        }

        composable(
            Screen.Books.route,
            arguments = listOf(
                navArgument("seriesId") { type = NavType.StringType },
                navArgument("bookCount") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { back ->
            val seriesId = back.arguments?.getString("seriesId")?.let { Screen.decodeArg(it) } ?: return@composable
            val bookCount = back.arguments?.getInt("bookCount") ?: 0
            val vm: BookViewModel = viewModel(
                factory = BookViewModel.Factory(app.bookRepository, app.seriesRepository, imageCacheInvalidator, textProvider)
            )
            BookScreen(
                seriesId = seriesId,
                initialBookCount = bookCount,
                serverUrl = serverUrl,
                onBookClick = { id, name, pages, isOneShot ->
                    navController.navigate(Screen.BookDetail.go(id, name, "", pages, isOneShot)) {
                        if (isOneShot || bookCount == 1) {
                            popUpTo(Screen.Books.go(seriesId, bookCount)) { inclusive = true }
                        }
                        launchSingleTop = true
                    }
                },
                onMetadataClick = { navController.navigate(Screen.Metadata.go("series", it)) },
                onTagClick = { tag -> navController.navigate(Screen.Series.go(id = null, tag = tag)) },
                onBack = { navController.popBackStack() }, vm = vm
            )
        }

        composable(
            Screen.BookDetail.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("bookName") { type = NavType.StringType },
                navArgument("seriesName") { type = NavType.StringType },
                navArgument("pageCount") { type = NavType.IntType },
                navArgument("isOneShot") { type = NavType.BoolType; defaultValue = false }
            )
        ) { back ->
            val bookId = back.arguments?.getString("bookId")?.let { Screen.decodeArg(it) } ?: return@composable
            val bookName = back.arguments?.getString("bookName")?.let { Screen.decodeArg(it) }.orEmpty()
            val pageCount = back.arguments?.getInt("pageCount") ?: 0
            val isOneShot = back.arguments?.getBoolean("isOneShot") ?: false
            val vm: BookDetailViewModel = viewModel(
                factory = BookDetailViewModel.Factory(
                    app.applicationContext,
                    app.bookRepository,
                    app.seriesRepository,
                    imageCacheInvalidator,
                    textProvider,
                    if (aiTranslationAvailable) app.aiTranslationRepositoryOrNull else null
                )
            )
            BookDetailScreen(
                bookId = bookId,
                bookName = bookName,
                pageCount = pageCount,
                isOneShot = isOneShot,
                onBack = {
                    if (isOneShot) {
                        val popped = navController.popBackStack(Screen.Series.route, inclusive = false)
                        if (!popped) navController.popBackStack()
                    } else {
                        navController.popBackStack()
                    }
                },
                onReadClick = { id, trackProgress ->
                    val effectiveTrack = trackProgress && !alwaysIncognito
                    val startPage = if (effectiveTrack) vm.book?.readProgress?.page ?: 1 else 1
                    navController.navigate(Screen.Reader.go(id, startPage, effectiveTrack))
                },
                onMetadataClick = { navController.navigate(Screen.Metadata.go("book", it)) },
                onAuthorClick = { authorName, _ ->
                    navController.navigate(Screen.Series.go(null, "author:$authorName"))
                },
                onTagClick = { tag -> navController.navigate(Screen.Series.go(id = null, tag = tag)) },
                aiTranslationAvailable = aiTranslationAvailable,
                vm = vm,
                prefs = app.authPreferences
            )
        }

        composable(
            Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("page") { type = NavType.IntType; defaultValue = 1 },
                navArgument("trackProgress") { type = NavType.BoolType; defaultValue = true }
            ),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(200)) }
        ) { back ->
            val bookId = back.arguments?.getString("bookId")?.let { Screen.decodeArg(it) } ?: return@composable
            val page = back.arguments?.getInt("page") ?: 1
            val trackProgress = back.arguments?.getBoolean("trackProgress") ?: true
            val vm: ReaderViewModel = viewModel(
                factory = ReaderViewModel.Factory(
                    app.bookRepository,
                    app.authPreferences,
                    textProvider,
                    if (aiTranslationAvailable) app.aiTranslationRepositoryOrNull else null,
                    if (aiTranslationAvailable) app.aiLocalModelRepository else null,
                    { recommendAiLocalModelTier(deviceProfile(app.applicationContext)) }
                )
            )
            ReaderScreen(
                bookId = bookId,
                startPage = page,
                trackProgress = trackProgress,
                onBack = { navController.popBackStack() },
                onOpenBook = ::openReaderBookFromBoundary,
                onSetBookCover = { coverUri ->
                    navController.navigate(Screen.Metadata.goBookCover(bookId, coverUri))
                },
                onSetSeriesCover = { coverUri ->
                    val seriesId = vm.currentSeriesId
                    if (seriesId.isNotBlank()) {
                        navController.navigate(Screen.Metadata.goSeriesCover(seriesId, coverUri))
                    }
                },
                canEditMetadata = canEditMetadata,
                aiTranslationAvailable = aiTranslationAvailable,
                vm = vm
            )
        }

        composable(
            Screen.Metadata.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
                navArgument("coverUri") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("coverFocus") { type = NavType.BoolType; defaultValue = false }
            )
        ) { back ->
            val type = back.arguments?.getString("type")?.let { Screen.decodeArg(it) } ?: return@composable
            val id = back.arguments?.getString("id")?.let { Screen.decodeArg(it) } ?: return@composable
            val coverUri = back.arguments?.getString("coverUri")?.let { Screen.decodeArg(it) }
            val coverFocus = back.arguments?.getBoolean("coverFocus") ?: false
            val vm: MetadataViewModel = viewModel(
                factory = MetadataViewModel.Factory(app.bookRepository, imageCacheInvalidator, textProvider)
            )
            MetadataScreen(
                type = type,
                id = id,
                serverUrl = serverUrl,
                coverUri = coverUri,
                coverFocus = coverFocus,
                canEditMetadata = canEditMetadata,
                onBack = { navController.popBackStack() },
                vm = vm
            )
        }

        composable(Screen.Me.route) {
            MeScreen(
                userEmail = user?.email,
                isAdmin = canEditMetadata,
                sessionSyncing = sessionState is SessionState.Initializing,
                sessionRetryable = sessionState is SessionState.RetryableFailure,
                onSessionRetry = { sessionVm.refresh(force = true) },
                aiTranslationAvailable = aiTranslationAvailable,
                onCachedBooksClick = { navController.navigate(Screen.CachedBooks.route) },
                onAiTranslationTasksClick = { navController.navigate(Screen.AiTranslationTasks.route) },
                appUpdateRepository = app.appUpdateRepository,
                onAdminClick = { navController.navigate(Screen.Admin.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onLogout = {
                    scope.launch {
                        app.authRepository.logout()
                        sessionVm.clear()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.CachedBooks.route) {
            val vm: CachedBooksViewModel = viewModel(factory = CachedBooksViewModel.Factory(app.applicationContext.cacheDir))
            CachedBooksScreen(
                vm = vm,
                onBookClick = { entry ->
                    navController.navigate(
                        Screen.BookDetail.go(
                            entry.bookId,
                            entry.title,
                            entry.seriesTitle,
                            entry.pageCount,
                            entry.isOneShot
                        )
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        if (aiTranslationAvailable) {
            composable(Screen.AiTranslationTasks.route) {
                val vm: AiTranslationTaskViewModel = viewModel(
                    factory = AiTranslationTaskViewModel.Factory(app.aiTranslationStore, app.aiTranslationRepositoryOrNull, serverUrl)
                )
                AiTranslationTaskScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() }, prefs = app.authPreferences, aiTranslationAvailable = aiTranslationAvailable)
        }
    }
    }
}

private data class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
)

private fun topLevelDestinations(): List<TopLevelDestination> =
    buildList {
        add(TopLevelDestination(Screen.Library.route, R.string.home, Icons.Default.Home))
        add(TopLevelDestination(Screen.Browse.route, R.string.browse, Icons.Default.Search))
        add(TopLevelDestination(Screen.Collections.route, R.string.collections, Icons.Default.CollectionsBookmark))
        add(TopLevelDestination(Screen.ReadLists.route, R.string.read_lists, Icons.AutoMirrored.Filled.FormatListBulleted))
        add(TopLevelDestination(Screen.Me.route, R.string.me, Icons.Default.Person))
    }

private fun usesOverlayBottomBar(route: String?): Boolean =
    route == Screen.Library.route ||
        route == Screen.Browse.route ||
        route == Screen.Collections.route ||
        route == Screen.ReadLists.route

@Composable
private fun AdaptiveShell(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    showTopLevelNav: Boolean,
    bottomBarVisible: Boolean,
    einkMode: Boolean,
    onDestinationClick: (TopLevelDestination) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp
        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                if (einkMode) {
                    if (showTopLevelNav) {
                        ReaderNavigationRail(destinations, currentRoute, onDestinationClick)
                    }
                } else {
                    AnimatedVisibility(visible = showTopLevelNav) {
                        ReaderNavigationRail(destinations, currentRoute, onDestinationClick)
                    }
                }
                Box(Modifier.weight(1f)) {
                    content(Modifier.fillMaxSize())
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (einkMode) {
                        if (showTopLevelNav && bottomBarVisible) {
                            ReaderNavigationBar(destinations, currentRoute, onDestinationClick)
                        }
                    } else {
                        AnimatedVisibility(
                            visible = showTopLevelNav && bottomBarVisible,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            ReaderNavigationBar(destinations, currentRoute, onDestinationClick)
                        }
                    }
                }
            ) { padding ->
                val layoutDirection = LocalLayoutDirection.current
                val bottomPadding = if (usesOverlayBottomBar(currentRoute)) {
                    0.dp
                } else {
                    padding.calculateBottomPadding()
                }
                content(
                    Modifier.padding(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection),
                        bottom = bottomPadding
                    )
                )
            }
        }
    }
}

@Composable
private fun ReaderNavigationRail(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onDestinationClick: (TopLevelDestination) -> Unit
) {
    NavigationRail {
        destinations.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationClick(destination) },
                icon = { Icon(destination.icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun ReaderNavigationBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onDestinationClick: (TopLevelDestination) -> Unit
) {
    NavigationBar {
        destinations.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationClick(destination) },
                icon = { Icon(destination.icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
