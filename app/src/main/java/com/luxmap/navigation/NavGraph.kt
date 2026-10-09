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
import com.luxmap.feature.auth.ui.LoginScreen
import com.luxmap.feature.auth.ui.SessionViewModel
import com.luxmap.feature.home.ui.HomeRoute
import com.luxmap.feature.map.ui.MapScreen
import com.luxmap.feature.map.ui.PoleDetailRoute
import com.luxmap.feature.profile.ui.ProfileScreen
import com.luxmap.feature.profile.ui.ProfileViewModel
import com.luxmap.feature.survey.ui.capture.CaptureScreen
import com.luxmap.feature.survey.ui.coverage.CoverageScreen
import com.luxmap.feature.survey.ui.plan.SurveyPlanScreen
import com.luxmap.feature.survey.ui.submit.SubmitScreen
import com.luxmap.feature.workorder.ui.completion.EvidenceCaptureRoute
import com.luxmap.feature.workorder.ui.completion.WorkOrderCompletionRoute
import com.luxmap.feature.workorder.ui.detail.WorkOrderDetailRoute

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
// Any change that involves the map screen is instant, with no fade at all. The map draws on its
// own GL surface, which ignores the fade alpha. NavHost also keeps the old screen drawn until the
// whole transition ends, so if the new screen faded in, the old map (and the buttons on top of
// it) would show through for that time. Fades are only used between the other screens.
private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210

private fun NavBackStackEntry.isMap() = destination.route == Routes.Map.route

private fun AnimatedContentTransitionScope<NavBackStackEntry>.involvesMap(): Boolean =
    initialState.isMap() || targetState.isMap()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(): EnterTransition =
    if (involvesMap()) {
        EnterTransition.None
    } else {
        fadeIn(animationSpec = tween(durationMillis = FADE_IN_MS, delayMillis = FADE_OUT_MS))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit(): ExitTransition =
    if (involvesMap()) ExitTransition.None else fadeOut(animationSpec = tween(durationMillis = FADE_OUT_MS))

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
                HomeRoute(
                    onOpenWorkOrder = { workOrderId ->
                        navController.navigate(Routes.WorkOrderDetail.createRoute(workOrderId))
                    },
                )
            }
            composable(Routes.Survey.route) {
                SurveyPlanScreen(
                    onEnterCaptureMode = { surveySweepId ->
                        // F03's standalone survey-plan entry has no work order in scope at all -
                        // threading a real workOrderId here is out of scope for this plan (fm-40
                        // only covers the Work Order "Bắt đầu" entry into capture). Same gap shape
                        // and same tracking as the onRedo path below.
                        navController.navigate(
                            Routes.SurveyCapture.createRoute(workOrderId = "", surveySweepId = surveySweepId),
                        )
                    },
                )
            }
            composable(
                route = Routes.SurveyCapture.route,
                arguments =
                    listOf(
                        navArgument("surveySweepId") { type = NavType.StringType },
                        navArgument("workOrderId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { backStackEntry ->
                val workOrderId = backStackEntry.arguments?.getString("workOrderId") ?: ""
                val surveySweepId = backStackEntry.arguments?.getString("surveySweepId") ?: return@composable
                CaptureScreen(
                    surveySweepId = surveySweepId,
                    workOrderId = workOrderId,
                    onSessionPackaged = { sessionId ->
                        navController.navigate(Routes.SurveyReview.createRoute(sessionId)) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
                )
            }
            composable(
                route = Routes.SurveyReview.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                CoverageScreen(
                    sessionId = sessionId,
                    onApprove = { approvedSessionId ->
                        navController.navigate(Routes.SurveySubmit.createRoute(approvedSessionId)) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
                    // "Redo" re-enters capture for a session that already exists in Room with a
                    // real workOrderId - CoverageScreen only passes surveySweepId today, so this
                    // path cannot forward the real value without changing CoverageScreen's own
                    // callback shape, which is out of scope here (F05 review flow, see the design
                    // spec's non-goals). SurveyCaptureService only uses workOrderId at session
                    // CREATION (Task 1/2 of this plan) - redo starts a brand new local session via
                    // CaptureViewModel, so this empty value is a real gap, not a cosmetic one: a
                    // session started via "redo" will not be upload-able until this is fixed.
                    // Tracked in docs/contract-drift.md, not silently left unflagged.
                    onRedo = { surveySweepId ->
                        navController.navigate(
                            Routes.SurveyCapture.createRoute(workOrderId = "", surveySweepId = surveySweepId),
                        ) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
                )
            }
            composable(
                route = Routes.SurveySubmit.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                SubmitScreen(sessionId = sessionId)
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
            composable(
                route = Routes.WorkOrderDetail.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val workOrderId = backStackEntry.arguments?.getString("workOrderId") ?: return@composable
                WorkOrderDetailRoute(
                    onBack = { navController.popBackStack() },
                    onComplete = { navController.navigate(Routes.WorkOrderCompletion.createRoute(workOrderId)) },
                    onStartSurvey = { startedWorkOrderId, surveySweepId ->
                        navController.navigate(Routes.SurveyCapture.createRoute(startedWorkOrderId, surveySweepId))
                    },
                )
            }
            composable(
                route = Routes.WorkOrderCompletion.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val workOrderId = backStackEntry.arguments?.getString("workOrderId") ?: return@composable
                WorkOrderCompletionRoute(
                    onBack = { navController.popBackStack() },
                    onCaptureEvidence = {
                        navController.navigate(Routes.WorkOrderEvidenceCapture.createRoute(workOrderId))
                    },
                )
            }
            composable(
                route = Routes.WorkOrderEvidenceCapture.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) {
                EvidenceCaptureRoute(onDone = { navController.popBackStack() })
            }
        }
    }
}
