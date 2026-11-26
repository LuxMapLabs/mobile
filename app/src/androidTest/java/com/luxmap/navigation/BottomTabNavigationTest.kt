package com.luxmap.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BottomTabNavigationTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var navigator: NavHostController

    @Test
    fun home_returns_to_the_original_entry_from_each_other_tab() {
        composeRule.setContent {
            navigator = rememberNavController()
            NavHost(navController = navigator, startDestination = Routes.Login.route) {
                composable(Routes.Login.route) { Text("Login") }
                composable(Routes.Home.route) { Text("Home") }
                composable(
                    route = Routes.Survey.route,
                    arguments =
                        listOf(
                            navArgument("highlightWorkOrderId") {
                                type = NavType.StringType
                                nullable = true
                            },
                        ),
                ) { Text("Survey") }
                composable(Routes.Map.route) { Text("Map") }
                composable(Routes.Profile.route) { Text("Profile") }
            }
        }
        composeRule.runOnIdle {
            navigator.navigate(Routes.Home.route) {
                popUpTo(Routes.Login.route) { inclusive = true }
            }
            val home = navigator.currentBackStackEntry
            navigator.navigate(Routes.Survey.createRoute(highlightWorkOrderId = "WO-1"))

            for (tab in listOf(Routes.Profile, Routes.Map, Routes.Survey)) {
                navigator.navigateToBottomTab(Routes.Survey.route)
                navigator.navigateToBottomTab(tab.route)
                assertEquals(tab.route, navigator.currentDestination?.route)
                val tabEntryId = navigator.currentBackStackEntry?.id
                navigator.navigateToBottomTab(Routes.Home.route)
                assertEquals(Routes.Home.route, navigator.currentDestination?.route)
                assertEquals(home, navigator.currentBackStackEntry)
                navigator.navigateToBottomTab(tab.route)
                assertEquals(tabEntryId, navigator.currentBackStackEntry?.id)
                navigator.navigateToBottomTab(Routes.Home.route)
            }
        }
    }

    @Test
    fun survey_tab_leaves_map_opened_from_a_work_order() {
        composeRule.setContent {
            navigator = rememberNavController()
            NavHost(navController = navigator, startDestination = Routes.Login.route) {
                composable(Routes.Login.route) { Text("Login") }
                composable(Routes.Home.route) { Text("Home") }
                composable(
                    route = Routes.Survey.route,
                    arguments =
                        listOf(
                            navArgument("highlightWorkOrderId") {
                                type = NavType.StringType
                                nullable = true
                            },
                        ),
                ) { Text("Survey") }
                composable(Routes.WorkOrderDetail.route) { Text("Detail") }
                composable(Routes.Map.route) { Text("Map") }
                composable(Routes.Profile.route) { Text("Profile") }
            }
        }
        composeRule.runOnIdle {
            navigator.navigate(Routes.Home.route) {
                popUpTo(Routes.Login.route) { inclusive = true }
            }
            navigator.navigateToBottomTab(Routes.Survey.route)
            navigator.navigate(Routes.WorkOrderDetail.createRoute("WO-1"))
            navigator.navigate(Routes.Map.createRoute(focusPoleId = "POLE-1"))
            assertEquals(Routes.Map.route, navigator.currentDestination?.route)

            navigator.navigateToBottomTab(Routes.Survey.route)
            assertEquals(Routes.Survey.route, navigator.currentDestination?.route)

            navigator.navigateToBottomTab(Routes.Home.route)
            navigator.navigateToBottomTab(Routes.Survey.route)
            assertEquals(Routes.Survey.route, navigator.currentDestination?.route)
        }
    }
}
