package com.luxmap.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
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
import com.luxmap.feature.auth.ui.SessionViewModel
import com.luxmap.feature.map.ui.MapScreen
import com.luxmap.feature.map.ui.PoleDetailRoute
import com.luxmap.feature.profile.ui.ProfileScreen
import com.luxmap.feature.profile.ui.ProfileViewModel

// The 4 tabs of the main area, in the order given by spec section A5.
private val bottomNavItems =
    listOf(
        BottomNavItem(Routes.Home.route, "Việc hôm nay", Icons.Filled.Home),
        BottomNavItem(Routes.Survey.route, "Khảo sát", Icons.Filled.CameraAlt),
        BottomNavItem(Routes.Map.route, "Bản đồ", Icons.Filled.Map),
        BottomNavItem(Routes.Profile.route, "Cá nhân", Icons.Filled.Person),
    )

// Fade-through between screens: the old screen fades out fast, then the new one fades in. Short
// on purpose — the default of NavHost is a 700 ms fade with both screens drawn at once, which
// feels slow and is heavy when one of them is the map. No slide, because the 4 tabs are equal.
//
// The map screen never fades. Its MapView draws on its own GL surface, which ignores the fade
// alpha, so a fade made the buttons on top of the map fade out over an empty area. Instead the
// map appears and disappears at once, and the other screen fades in without a delay when the
// map has just gone.
private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210

private fun NavBackStackEntry.isMap() = destination.route == Routes.Map.route

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(): EnterTransition =
    when {
        targetState.isMap() -> EnterTransition.None
        initialState.isMap() -> fadeIn(animationSpec = tween(durationMillis = FADE_IN_MS))
        else -> fadeIn(animationSpec = tween(durationMillis = FADE_IN_MS, delayMillis = FADE_OUT_MS))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit(): ExitTransition =
    if (initialState.isMap()) ExitTransition.None else fadeOut(animationSpec = tween(durationMillis = FADE_OUT_MS))

// Khung NavHost tối thiểu — nối các composable màn hình thật khi
// từng feature (auth, home, ...) được implement theo mã FM-XX tương ứng.
@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // The bottom bar only shows on the 4 tab screens, so Login and child screens (for example
    // pole detail) stay full screen.
    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    // When the session ends (logout, or a failed token refresh) while the user is inside the app,
    // go back to Login and clear the back stack so Back cannot return to a signed-in screen.
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val isLoggedIn by sessionViewModel.isLoggedIn.collectAsState()
    LaunchedEffect(isLoggedIn, currentRoute) {
        if (isLoggedIn == false && currentRoute != null && currentRoute != Routes.Login.route) {
            navController.navigate(Routes.Login.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

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
                        if (item.route != currentRoute) {
                            navController.navigate(item.route) {
                                popUpTo(Routes.Map.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
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
            enterTransition = { screenEnter() },
            exitTransition = { screenExit() },
            popEnterTransition = { screenEnter() },
            popExitTransition = { screenExit() },
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
                val profileViewModel: ProfileViewModel = hiltViewModel()
                val username by profileViewModel.username.collectAsState()
                ProfileScreen(
                    // Null only for a session saved before the app kept the username.
                    username = username ?: "Tài khoản",
                    isLoggingOut = profileViewModel.isLoggingOut,
                    onConfirmLogout = profileViewModel::onConfirmLogout,
                )
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
