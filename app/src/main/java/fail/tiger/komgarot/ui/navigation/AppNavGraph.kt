package fail.tiger.komgarot.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fail.tiger.komgarot.KomgarotApp
import fail.tiger.komgarot.ui.book.BookScreen
import fail.tiger.komgarot.ui.book.BookViewModel
import fail.tiger.komgarot.ui.bookdetail.BookDetailScreen
import fail.tiger.komgarot.ui.bookdetail.BookDetailViewModel
import fail.tiger.komgarot.ui.library.LibraryScreen
import fail.tiger.komgarot.ui.library.LibraryViewModel
import fail.tiger.komgarot.ui.login.LoginScreen
import fail.tiger.komgarot.ui.login.LoginViewModel
import fail.tiger.komgarot.ui.metadata.MetadataScreen
import fail.tiger.komgarot.ui.metadata.MetadataViewModel
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
    val serverUrl by libraryVm.prefs.serverUrl.collectAsState(initial = "")

    LaunchedEffect(serverUrl) {
        if (serverUrl.isNotEmpty()) {
            navController.navigate(Screen.Library.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        enterTransition = { slideInVertically(tween(300)) { it / 8 } + fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutVertically(tween(300)) { it / 8 } + fadeOut(tween(300)) }
    ) {
        composable(Screen.Login.route) {
            val vm: LoginViewModel = viewModel(factory = LoginViewModel.Factory(app.authRepository))
            LoginScreen(onSuccess = {
                navController.navigate(Screen.Library.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }, vm = vm)
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onLibraryClick = { navController.navigate(Screen.Series.go(it)) },
                vm = libraryVm,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSettings = { navController.navigate(Screen.Settings.route) }
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

            val scope = rememberCoroutineScope()
            SeriesScreen(
                libraryId = libraryId, serverUrl = serverUrl,
                onSeriesClick = { seriesId, booksCount ->
                    if (booksCount == 1) {
                        scope.launch {
                            val book = runCatching { app.bookRepository.getBooks(seriesId, 0) }.getOrNull()?.content?.firstOrNull()
                            if (book != null) {
                                navController.navigate(Screen.BookDetail.go(book.id, book.metadata.title.ifEmpty { book.name }, "", book.media.pagesCount, true))
                            } else {
                                navController.navigate(Screen.Books.go(seriesId))
                            }
                        }
                    } else {
                        navController.navigate(Screen.Books.go(seriesId))
                    }
                },
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
                onMetadataClick = { navController.navigate(Screen.Metadata.go("book", it)) },
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
                    val startPage = if (trackProgress) vm.book?.readProgress?.page ?: 1 else 1
                    navController.navigate("reader/$id/$startPage?trackProgress=$trackProgress")
                },
                onAuthorClick = { authorName ->
                    navController.navigate(Screen.Series.go(null, "author:$authorName"))
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
            )
        ) { back ->
            val bookId = back.arguments?.getString("bookId") ?: return@composable
            val page = back.arguments?.getInt("page") ?: 1
            val trackProgress = back.arguments?.getBoolean("trackProgress") ?: true
            val vm: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory(app.bookRepository, app.authPreferences))
            ReaderScreen(bookId = bookId, startPage = page, trackProgress = trackProgress, onBack = { navController.popBackStack() }, vm = vm)
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
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
