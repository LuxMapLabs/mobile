package com.luxmap.navigation

import androidx.navigation.NavController

internal fun NavController.navigateToBottomTab(route: String) {
    if (route == currentDestination?.route) return

    if (route == Routes.Home.route) {
        if (!popBackStack(Routes.Home.route, inclusive = false, saveState = true)) {
            navigate(Routes.Home.route)
        }
        return
    }

    val target =
        when (route) {
            Routes.Survey.route -> Routes.Survey.createRoute()
            Routes.Map.route -> Routes.Map.createRoute()
            else -> route
        }
    navigate(target) {
        popUpTo(Routes.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
