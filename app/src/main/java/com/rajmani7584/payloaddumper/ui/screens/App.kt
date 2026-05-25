package com.rajmani7584.payloaddumper.ui.screens

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rajmani7584.payloaddumper.ui.components.LocalColors
import kotlinx.coroutines.launch


@Composable
fun App() {
    val appNavController = rememberNavController()
    val homeNavController = rememberNavController()

    NavHost(appNavController, Screens.App.route) {
        composable(Screens.App.route, enterTransition = {
            fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End)
        }, exitTransition = {
            fadeOut() + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start)
        }) {
            AppLayout(appNavController, homeNavController)
        }
        composable(Screens.Selector.route, arguments = listOf(navArgument("directory") {
            type =
                NavType.BoolType
        })) {
            Selector(appNavController)
        }
    }
}

@Composable
fun AppLayout(
    appNavController: NavHostController,
    homeNavController: NavHostController
) {

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(0) { 4 }

    val navItems = listOf("Home", "Logs", "Analyzer", "Settings")

    NavigationSuiteScaffold(
//        navigationSuiteColors = NavigationSuiteDefaults.colors(navigationBarContainerColor = AppTheme.colors.surface),
        navigationItems = {
            navItems.forEachIndexed { index, name ->
                val icon = when (index) {
                    0 -> Icons.Default.Home
                    1 -> Icons.Default.Terminal
                    2 -> Icons.Default.FindInPage
                    3 -> Icons.Default.Settings
                    else -> Icons.Default.Home
                }
                val color = if (pagerState.currentPage == index) LocalColors.current.error else LocalColors.current.onSurface
                NavigationSuiteItem(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            if (pagerState.currentPage == index && index == 0) {
                                homeNavController.popBackStack(Screens.Home.route, false)
                            } else {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    },
                    icon = { Icon(icon, contentDescription = name, tint = color) },
                    label = { Text(name, color = color) })
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(pagerState) { page ->
                when (page) {
                    0 -> HomeScreen(appNavController, homeNavController)
                    1 -> LogScreen()
                    2 -> AnalyzeScreen()
                    3 -> SettingScreen()
                }
            }
        }
    }
}