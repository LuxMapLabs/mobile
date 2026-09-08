package com.luxmap.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luxmap.feature.auth.ui.LoginScreen
import com.luxmap.feature.map.ui.MapScreen

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
            MapScreen()
        }
    }
}
