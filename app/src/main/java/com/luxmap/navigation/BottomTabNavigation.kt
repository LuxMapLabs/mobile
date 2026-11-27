package com.luxmap.navigation

import androidx.navigation.NavController

private val tabRoutes =
    setOf(Routes.Home.route, Routes.Survey.route, Routes.History.route, Routes.Map.route, Routes.Profile.route)

internal fun NavController.navigateToBottomTab(route: String) {
    if (route == currentDestination?.route) return

    val keepTabState = currentBackStack.value.all { it.destination.route.let { r -> r == null || r in tabRoutes } }

    if (route == Routes.Home.route) {
        if (!popBackStack(Routes.Home.route, inclusive = false, saveState = keepTabState)) {
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
        popUpTo(Routes.Home.route) { saveState = keepTabState }
        launchSingleTop = true
        restoreState = keepTabState
    }
}
