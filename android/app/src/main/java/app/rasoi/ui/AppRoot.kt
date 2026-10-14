package app.rasoi.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import app.rasoi.R
import app.rasoi.sos.ComposeScreen
import app.rasoi.sos.ConfirmScreen
import app.rasoi.sos.SetupScreen
import app.rasoi.sos.SosViewModel
import app.rasoi.sos.StatusScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVS = "favs"
    const val SETTINGS = "settings"
    const val CATEGORY = "category/{c}"
    const val RECIPE = "recipe/{id}"

    // The report area. Plain route names; nothing user-visible refers to them.
    const val SETUP = "s/1"
    const val COMPOSE = "s/2"
    const val CONFIRM = "s/3"
    const val STATUS = "s/4"

    fun isHidden(route: String?) = route?.startsWith("s/") == true
}

fun NavHostController.goHome() = navigate(Routes.HOME) {
    popUpTo(graph.findStartDestination().id) { inclusive = true }
    launchSingleTop = true
}

@Composable
fun AppRoot(
    recipeVm: RecipeViewModel,
    sosVm: SosViewModel,
    onNavReady: (NavHostController) -> Unit,
    setSecure: (Boolean) -> Unit,
) {
    val nav = rememberNavController()
    SideEffect { onNavReady(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val hidden = Routes.isHidden(route)
    SideEffect { setSecure(hidden) } // FLAG_SECURE exactly while a report screen is showing

    val favourites by recipeVm.favourites.collectAsState()
    val theme by recipeVm.theme.collectAsState()
    val ml by recipeVm.millilitres.collectAsState()
    var query by remember { mutableStateOf("") }
    val sos by sosVm.ui.collectAsState()

    fun quickExit() {
        sosVm.wipe()
        nav.goHome()
    }

    val tops = listOf(
        Triple(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
        Triple(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search),
        Triple(Routes.FAVS, R.string.nav_favourites, Icons.Filled.Favorite),
        Triple(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    )

    RasoiTheme(theme) {
        Scaffold(bottomBar = {
            if (tops.any { it.first == route }) NavigationBar {
                tops.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { nav.navigate(r) { popUpTo(Routes.HOME); launchSingleTop = true } },
                        icon = { Icon(icon, null) }, label = { Text(stringResource(label)) },
                    )
                }
            }
        }) { pad ->
            // No transitions anywhere, so leaving the report area never shows a reveal animation.
            NavHost(
                nav, Routes.HOME,
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(recipeVm, pad, { nav.navigate("category/$it") }, { nav.navigate("recipe/$it") })
                }
                composable(Routes.SEARCH) { SearchScreen(recipeVm, pad, query, { query = it }, { nav.navigate("recipe/$it") }) }
                composable(Routes.FAVS) { FavouritesScreen(recipeVm, pad, favourites) { nav.navigate("recipe/$it") } }
                composable(Routes.SETTINGS) { SettingsScreen(recipeVm, pad, theme, ml) }
                composable(Routes.CATEGORY) { e ->
                    CategoryScreen(recipeVm, e.arguments?.getString("c").orEmpty(), { nav.popBackStack() }) { nav.navigate("recipe/$it") }
                }
                composable(Routes.RECIPE) { e ->
                    val recipe = recipeVm.repo.byId(e.arguments?.getString("id").orEmpty())
                    if (recipe != null) RecipeDetailScreen(recipeVm, recipe, favourites, ml, { nav.popBackStack() }) {
                        setSecure(true) // before the first report frame is drawn
                        sosVm.open()
                        nav.navigate(if (sosVm.ui.value.choices.setupDone) Routes.COMPOSE else Routes.SETUP)
                    }
                }

                // After process death the nav stack can outlive the in-memory session: send her home, show nothing.
                composable(Routes.SETUP) { Guarded(sos.sessionOpen, nav) { SetupScreen(sosVm, { nav.navigate(Routes.COMPOSE) }, ::quickExit) } }
                composable(Routes.COMPOSE) {
                    Guarded(sos.sessionOpen, nav) {
                        ComposeScreen(sosVm, { nav.navigate(Routes.CONFIRM) }, { nav.navigate(Routes.SETUP) }, ::quickExit)
                    }
                }
                composable(Routes.CONFIRM) {
                    Guarded(sos.sessionOpen, nav) {
                        ConfirmScreen(sosVm, { nav.popBackStack() }, { nav.navigate(Routes.STATUS) }, ::quickExit)
                    }
                }
                composable(Routes.STATUS) { StatusScreen(sosVm, ::quickExit) }
            }
        }
    }
}

@Composable
private fun Guarded(sessionOpen: Boolean, nav: NavHostController, content: @Composable () -> Unit) {
    if (sessionOpen) content() else LaunchedEffect(Unit) { nav.goHome() }
}
