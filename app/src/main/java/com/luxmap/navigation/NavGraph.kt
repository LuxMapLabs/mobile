package com.luxmap.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luxmap.feature.auth.ui.LoginScreen
import com.luxmap.feature.map.ui.MapScreen
import com.luxmap.feature.map.ui.PoleDetailRoute

// Khung NavHost tối thiểu — nối các composable màn hình thật khi
// từng feature (auth, home, ...) được implement theo mã FM-XX tương ứng.
@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.Login.route,
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
        }
        composable(Routes.Map.route) {
            MapScreen(
                onOpenPoleDetail = { poleId ->
                    navController.navigate(Routes.PoleDetail.createRoute(poleId))
                },
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
