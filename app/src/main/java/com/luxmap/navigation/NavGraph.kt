package com.luxmap.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luxmap.core.ui.components.BottomNavBar
import com.luxmap.core.ui.components.BottomNavItem
import com.luxmap.core.ui.components.PlaceholderScreen
import com.luxmap.feature.auth.ui.LoginScreen
import com.luxmap.feature.map.ui.MapScreen
import com.luxmap.feature.map.ui.PoleDetailRoute

// The 4 tabs of the main area, in the order given by spec section A5.
private val bottomNavItems =
    listOf(
        BottomNavItem(Routes.Home.route, "Việc hôm nay", Icons.Filled.Home),
        BottomNavItem(Routes.Survey.route, "Khảo sát", Icons.Filled.CameraAlt),
        BottomNavItem(Routes.Map.route, "Bản đồ", Icons.Filled.Map),
        BottomNavItem(Routes.Profile.route, "Cá nhân", Icons.Filled.Person),
    )

// Khung NavHost tối thiểu — nối các composable màn hình thật khi
// từng feature (auth, home, ...) được implement theo mã FM-XX tương ứng.
@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // The bottom bar only shows on the 4 tab screens, so Login and child screens (for example
    // pole detail) stay full screen.
    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        // Each screen handles its own status/navigation bar insets (see MainActivity), so the
        // Scaffold must only add the height of the bottom bar to innerPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onItemSelected = { item ->
                        // Map is the root of the main area (Login is removed from the back stack
                        // after sign-in), so each tab keeps its own state when you come back.
                        navController.navigate(item.route) {
                            popUpTo(Routes.Map.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Login.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.Map.route) {
                            popUpTo(Routes.Login.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.Home.route) {
                PlaceholderScreen(title = "Việc hôm nay")
            }
            composable(Routes.Survey.route) {
                PlaceholderScreen(title = "Khảo sát")
            }
            composable(Routes.Map.route) {
                MapScreen(
                    onOpenPoleDetail = { poleId ->
                        navController.navigate(Routes.PoleDetail.createRoute(poleId))
                    },
                )
            }
            composable(Routes.Profile.route) {
                PlaceholderScreen(title = "Cá nhân")
            }
            composable(
                route = Routes.PoleDetail.route,
                arguments = listOf(navArgument("poleId") { type = NavType.StringType }),
            ) {
                PoleDetailRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
