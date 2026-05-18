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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fail.tiger.komgarot.KomgarotApp
import fail.tiger.komgarot.ui.admin.AdminScreen
import fail.tiger.komgarot.ui.admin.AdminViewModel
import fail.tiger.komgarot.ui.book.BookScreen
import fail.tiger.komgarot.ui.book.BookViewModel
import fail.tiger.komgarot.ui.bookdetail.BookDetailScreen
import fail.tiger.komgarot.ui.bookdetail.BookDetailViewModel
import fail.tiger.komgarot.ui.collection.CollectionDetailScreen
import fail.tiger.komgarot.ui.collection.CollectionScreen
import fail.tiger.komgarot.ui.collection.CollectionViewModel
import fail.tiger.komgarot.ui.library.LibraryScreen
import fail.tiger.komgarot.ui.library.LibraryViewModel
import fail.tiger.komgarot.ui.login.LoginScreen
import fail.tiger.komgarot.ui.login.LoginViewModel
import fail.tiger.komgarot.ui.metadata.MetadataScreen
import fail.tiger.komgarot.ui.metadata.MetadataViewModel
import fail.tiger.komgarot.ui.readlist.ReadListDetailScreen
import fail.tiger.komgarot.ui.readlist.ReadListScreen
import fail.tiger.komgarot.ui.readlist.ReadListViewModel
import fail.tiger.komgarot.ui.reader.ReaderScreen
import fail.tiger.komgarot.ui.reader.ReaderViewModel
import fail.tiger.komgarot.ui.series.SeriesScreen
import fail.tiger.komgarot.ui.series.SeriesViewModel
import fail.tiger.komgarot.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(app: KomgarotApp) {
    val navController = rememberNavController()
    val libraryVm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(app.libraryRepository, app.authRepository, app.authPreferences)
    )
    val sessionVm: SessionViewModel = viewModel(factory = SessionViewModel.Factory(app.userRepository))
    val serverUrl by libraryVm.prefs.serverUrl.collectAsState(initial = "")
    val alwaysIncognito by app.authPreferences.alwaysIncognito.collectAsState(initial = false)
    val user by sessionVm.user.collectAsState()
    val startDest = if (serverUrl.isNotEmpty()) Screen.Library.route else Screen.Login.route
    val scope = rememberCoroutineScope()

    LaunchedEffect(serverUrl) {
        if (serverUrl.isNotEmpty() && navController.currentDestination?.route != Screen.Library.route) {
            sessionVm.refresh()
            navController.navigate(Screen.Library.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    val slideDistance = rememberSlideDistance()
    val openSeries: (String, Int) -> Unit = { seriesId, booksCount ->
        if (booksCount == 1) {
            scope.launch {
                val book = runCatching { app.bookRepository.getBooks(seriesId, 0) }.getOrNull()?.content?.firstOrNull()
                if (book != null) {
                    navController.navigate(
                        Screen.BookDetail.go(
                            book.id,
                            book.metadata.title.ifEmpty { book.name },
                            book.seriesTitle.orEmpty(),
                            book.media.pagesCount,
                            true
                        )
                    )
                } else {
                    navController.navigate(Screen.Books.go(seriesId))
                }
            }
        } else {
            navController.navigate(Screen.Books.go(seriesId))
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelDestinations = topLevelDestinations(user?.isAdmin == true)
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

    AdaptiveShell(
        destinations = topLevelDestinations,
        currentRoute = currentRoute,
        showTopLevelNav = showTopLevelNav,
        bottomBarVisible = bottomBarVisible,
        onDestinationClick = { destination -> navigateTopLevel(destination.route) }
    ) { shellModifier ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = shellModifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            enterTransition = {
                materialSharedAxisX(forward = true, slideDistance = slideDistance).targetContentEnter
            },
            exitTransition = {
                materialSharedAxisX(forward = true, slideDistance = slideDistance).initialContentExit
            },
            popEnterTransition = {
                materialSharedAxisX(forward = false, slideDistance = slideDistance).targetContentEnter
            },
            popExitTransition = {
                materialSharedAxisX(forward = false, slideDistance = slideDistance).initialContentExit
            }
        ) {
        composable(Screen.Login.route) {
            val vm: LoginViewModel = viewModel(factory = LoginViewModel.Factory(app.authRepository))
            LoginScreen(onSuccess = {
                sessionVm.refresh()
                navController.navigate(Screen.Library.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }, vm = vm)
        }

        composable(Screen.Library.route) {
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
                vm = libraryVm,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSettings = { navigateTopLevel(Screen.Settings.route) },
                onBottomBarVisibleChange = { bottomBarVisible = it }
            )
        }

        composable(Screen.Browse.route) {
            val vm: SeriesViewModel = viewModel(factory = SeriesViewModel.Factory(app.seriesRepository, app.applicationContext))
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
            val vm: CollectionViewModel = viewModel(factory = CollectionViewModel.Factory(app.collectionRepository))
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
            val collectionId = back.arguments?.getString("collectionId") ?: return@composable
            val vm: CollectionViewModel = viewModel(factory = CollectionViewModel.Factory(app.collectionRepository))
            CollectionDetailScreen(
                collectionId = collectionId,
                serverUrl = serverUrl,
                vm = vm,
                onSeriesClick = openSeries,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ReadLists.route) {
            val vm: ReadListViewModel = viewModel(factory = ReadListViewModel.Factory(app.readListRepository))
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
            val readListId = back.arguments?.getString("readListId") ?: return@composable
            val vm: ReadListViewModel = viewModel(factory = ReadListViewModel.Factory(app.readListRepository))
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
            val vm: AdminViewModel = viewModel(factory = AdminViewModel.Factory(app.adminRepository))
            AdminScreen(
                isAdmin = user?.isAdmin == true,
                vm = vm,
                onBack = { navController.navigate(Screen.Library.route) }
            )
        }

        composable(
            Screen.Series.route,
            arguments = listOf(
                navArgument("libraryId") { type = NavType.StringType },
                navArgument("search") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { back ->
            val libraryId = back.arguments?.getString("libraryId")?.takeIf { it != "all" }
            val searchQuery = back.arguments?.getString("search")
            val vm: SeriesViewModel = viewModel(factory = SeriesViewModel.Factory(app.seriesRepository, app.applicationContext))

            LaunchedEffect(searchQuery) {
                if (!searchQuery.isNullOrEmpty()) {
                    vm.search(searchQuery)
                }
            }

            SeriesScreen(
                libraryId = libraryId, serverUrl = serverUrl,
                onSeriesClick = openSeries,
                onMetadataClick = { navController.navigate(Screen.Metadata.go("series", it)) },
                onBack = { navController.popBackStack() }, vm = vm
            )
        }

        composable(
            Screen.Books.route,
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
        ) { back ->
            val seriesId = back.arguments?.getString("seriesId") ?: return@composable
            val vm: BookViewModel = viewModel(factory = BookViewModel.Factory(app.bookRepository, app.seriesRepository))
            BookScreen(
                seriesId = seriesId, serverUrl = serverUrl,
                onBookClick = { id, name, pages, isOneShot ->
                    navController.navigate(Screen.BookDetail.go(id, name, "", pages, isOneShot)) {
                        launchSingleTop = true
                    }
                },
                onMetadataClick = { navController.navigate(Screen.Metadata.go("series", it)) },
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
            val bookId = back.arguments?.getString("bookId") ?: return@composable
            val bookName = java.net.URLDecoder.decode(back.arguments?.getString("bookName") ?: "", "UTF-8")
            val pageCount = back.arguments?.getInt("pageCount") ?: 0
            val isOneShot = back.arguments?.getBoolean("isOneShot") ?: false
            val vm: BookDetailViewModel = viewModel(
                factory = BookDetailViewModel.Factory(app.bookRepository, app.seriesRepository)
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
                    navController.navigate("reader/$id/$startPage?trackProgress=$effectiveTrack")
                },
                onMetadataClick = { navController.navigate(Screen.Metadata.go("book", it)) },
                onAuthorClick = { authorName, authorRole ->
                    navController.navigate(Screen.Series.go(null, "author:$authorName,$authorRole"))
                },
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
            val bookId = back.arguments?.getString("bookId") ?: return@composable
            val page = back.arguments?.getInt("page") ?: 1
            val trackProgress = back.arguments?.getBoolean("trackProgress") ?: true
            val vm: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory(app.bookRepository, app.authPreferences))
            ReaderScreen(
                bookId = bookId,
                startPage = page,
                trackProgress = trackProgress,
                onBack = { navController.popBackStack() },
                onOpenBook = { id, start, shouldTrack ->
                    navController.navigate(Screen.Reader.go(id, start, shouldTrack)) {
                        popUpTo(Screen.Reader.route) { inclusive = true }
                    }
                },
                vm = vm
            )
        }

        composable(
            Screen.Metadata.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) { back ->
            val type = back.arguments?.getString("type") ?: return@composable
            val id = back.arguments?.getString("id") ?: return@composable
            val vm: MetadataViewModel = viewModel(factory = MetadataViewModel.Factory(app.bookRepository))
            MetadataScreen(type = type, id = id, onBack = { navController.popBackStack() }, vm = vm)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() }, prefs = app.authPreferences)
        }
    }
    }
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private fun topLevelDestinations(isAdmin: Boolean): List<TopLevelDestination> =
    buildList {
        add(TopLevelDestination(Screen.Library.route, "首页", Icons.Default.Home))
        add(TopLevelDestination(Screen.Browse.route, "浏览", Icons.Default.Search))
        add(TopLevelDestination(Screen.Collections.route, "集合", Icons.Default.CollectionsBookmark))
        add(TopLevelDestination(Screen.ReadLists.route, "阅读列表", Icons.Default.FormatListBulleted))
        if (isAdmin) add(TopLevelDestination(Screen.Admin.route, "管理", Icons.Default.AdminPanelSettings))
        add(TopLevelDestination(Screen.Settings.route, "设置", Icons.Default.Settings))
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
    onDestinationClick: (TopLevelDestination) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp
        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = showTopLevelNav) {
                    NavigationRail {
                        destinations.forEach { destination ->
                            NavigationRailItem(
                                selected = currentRoute == destination.route,
                                onClick = { onDestinationClick(destination) },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label) }
                            )
                        }
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
                    AnimatedVisibility(
                        visible = showTopLevelNav && bottomBarVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        NavigationBar {
                            destinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { onDestinationClick(destination) },
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) }
                                )
                            }
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
