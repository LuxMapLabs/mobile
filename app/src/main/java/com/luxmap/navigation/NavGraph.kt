package com.luxmap.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import com.luxmap.feature.notification.ui.NotificationBadgeViewModel
import com.luxmap.feature.notification.ui.NotificationListRoute
import com.luxmap.feature.profile.ui.ProfileRoute
import com.luxmap.feature.survey.ui.hub.SurveyHubRoute
import com.luxmap.feature.survey.ui.polephoto.PoleListRoute
import com.luxmap.feature.survey.ui.polephoto.PoleListViewModel
import com.luxmap.feature.survey.ui.polephoto.PolePhotoCaptureRoute
import com.luxmap.feature.survey.ui.polephoto.PolePhotoCaptureSource
import com.luxmap.feature.survey.ui.polephoto.PolePhotoResultRoute
import com.luxmap.feature.survey.ui.polephoto.PoleVideoCaptureRoute
import com.luxmap.feature.survey.ui.polephoto.PoleVideoCaptureSource
import com.luxmap.feature.workorder.polegps.ui.measure.PoleGpsMeasureRoute
import com.luxmap.feature.workorder.polegps.ui.pdf.PoleGpsPdfViewerRoute
import com.luxmap.feature.workorder.ui.completion.EvidenceCaptureRoute
import com.luxmap.feature.workorder.ui.detail.WorkOrderDetailRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// The 4 tabs of the main area, in the order given by spec section A5.
private val bottomNavItems =
    listOf(
        BottomNavItem(Routes.Home.route, "Hôm nay", Icons.Filled.Home),
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
private const val NOTIFICATION_POLL_INTERVAL_MS = 45_000L

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

    val notificationBadgeViewModel: NotificationBadgeViewModel = hiltViewModel()
    val unreadNotificationCount by notificationBadgeViewModel.unreadCount.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Gated on isLoggedIn, not just the Activity lifecycle: without this, the loop would poll an
    // authenticated endpoint with no token while the user sits on Login (risking a spurious
    // forceLogout from TokenAuthenticator), and the badge would keep showing the PREVIOUS
    // account's count for up to one tick after a different user signs in. reset() on logout
    // clears it immediately instead of leaving a stale number on screen.
    LaunchedEffect(lifecycleOwner, isLoggedIn) {
        if (isLoggedIn != true) {
            notificationBadgeViewModel.reset()
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                notificationBadgeViewModel.refresh()
                delay(NOTIFICATION_POLL_INTERVAL_MS)
            }
        }
    }

    Scaffold(
        // Each screen handles its own status/navigation bar insets (see MainActivity), so the
        // Scaffold must only add the height of the bottom bar to innerPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Fades in/out with the same durations as the content transition (screenEnter/screenExit
        // above) instead of popping instantly - showBottomBar flips the moment the back stack's
        // current entry changes, which happens before the NavHost's own crossfade finishes, so an
        // un-animated "if" here made the bell/bottom bar flash in while the old screen was still
        // fading out underneath it.
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(animationSpec = tween(FADE_IN_MS)),
                exit = fadeOut(animationSpec = tween(FADE_OUT_MS)),
            ) {
                BottomNavBar(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onItemSelected = { item -> navController.navigateToBottomTab(item.route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Login.route,
            // Only the bottom inset (bottomBar height) is applied here - see the Scaffold's own
            // comment above on why the top inset (bell bar) is deliberately left out.
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { screenEnter() },
            exitTransition = { screenExit() },
            popEnterTransition = { screenEnter() },
            popExitTransition = { screenExit() },
        ) {
            composable(Routes.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.Home.route) {
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
                    unreadNotificationCount = unreadNotificationCount,
                    onOpenNotifications = { navController.navigate(Routes.Notification.route) },
                )
            }
            composable(
                route = Routes.Survey.route,
                arguments =
                    listOf(
                        navArgument("highlightWorkOrderId") {
                            type = NavType.StringType
                            nullable = true
                        },
                    ),
            ) { backStackEntry ->
                SurveyHubRoute(
                    highlightWorkOrderId = backStackEntry.arguments?.getString("highlightWorkOrderId"),
                    onCapturePolePhotos = { workOrderId ->
                        navController.navigate(Routes.PolePhotoList.createRoute(workOrderId))
                    },
                    onOpenWorkOrderDetail = { workOrderId ->
                        navController.navigate(Routes.WorkOrderDetail.createRoute(workOrderId))
                    },
                    unreadNotificationCount = unreadNotificationCount,
                    onOpenNotifications = { navController.navigate(Routes.Notification.route) },
                )
            }
            composable(
                route = Routes.PolePhotoList.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val polePhotoWorkOrderId = backStackEntry.arguments?.getString("workOrderId") ?: return@composable
                PoleListRoute(
                    workOrderId = polePhotoWorkOrderId,
                    onOpenMap = { poleId, lat, lng ->
                        navController.navigate(
                            Routes.Map.createRoute(focusPoleId = poleId, focusLat = lat, focusLng = lng),
                        )
                    },
                    onCapturePole = { poleId, source ->
                        navController.navigate(
                            Routes.PolePhotoCapture.createRoute(polePhotoWorkOrderId, poleId, source.name.lowercase()),
                        )
                    },
                    onCaptureVideo = { poleId, source ->
                        navController.navigate(
                            Routes.PoleVideoCapture.createRoute(polePhotoWorkOrderId, poleId, source.name.lowercase()),
                        )
                    },
                    onViewAiResult = { photoId ->
                        navController.navigate(Routes.PolePhotoResult.createRoute(polePhotoWorkOrderId, photoId))
                    },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PolePhotoResult.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("photoId") { type = NavType.StringType },
                    ),
            ) { backStackEntry ->
                val resultWorkOrderId = backStackEntry.arguments?.getString("workOrderId") ?: return@composable
                val resultPhotoId = backStackEntry.arguments?.getString("photoId") ?: return@composable
                // Shares the PoleListViewModel instance already running for the list screen below
                // this one on the back stack, instead of re-fetching into a second ViewModel - the
                // list's cache/submit-review logic (PoleListViewModel.onViewAiResult/onSubmitReview)
                // already does exactly what this screen needs.
                val parentEntry =
                    remember(backStackEntry) {
                        navController.getBackStackEntry(Routes.PolePhotoList.createRoute(resultWorkOrderId))
                    }
                val sharedViewModel: PoleListViewModel = hiltViewModel(parentEntry)
                PolePhotoResultRoute(
                    photoId = resultPhotoId,
                    onBack = { navController.popBackStack() },
                    onRetake = { poleId, mediaType, source ->
                        navController.popBackStack()
                        val route =
                            if (mediaType == "video") {
                                Routes.PoleVideoCapture.createRoute(resultWorkOrderId, poleId, source)
                            } else {
                                Routes.PolePhotoCapture.createRoute(resultWorkOrderId, poleId, source)
                            }
                        navController.navigate(route)
                    },
                    viewModel = sharedViewModel,
                )
            }
            composable(
                route = Routes.PolePhotoCapture.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("poleId") { type = NavType.StringType },
                        navArgument("source") {
                            type = NavType.StringType
                            defaultValue = "camera"
                        },
                    ),
            ) { backStackEntry ->
                val capturePoleId = backStackEntry.arguments?.getString("poleId") ?: return@composable
                val captureSource =
                    PolePhotoCaptureSource.entries.firstOrNull {
                        it.name.equals(backStackEntry.arguments?.getString("source"), ignoreCase = true)
                    } ?: PolePhotoCaptureSource.CAMERA
                PolePhotoCaptureRoute(
                    poleId = capturePoleId,
                    source = captureSource,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PoleVideoCapture.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("poleId") { type = NavType.StringType },
                        navArgument("source") {
                            type = NavType.StringType
                            defaultValue = "camera"
                        },
                    ),
            ) { backStackEntry ->
                val captureVideoPoleId = backStackEntry.arguments?.getString("poleId") ?: return@composable
                val captureVideoSource =
                    PoleVideoCaptureSource.entries.firstOrNull {
                        it.name.equals(backStackEntry.arguments?.getString("source"), ignoreCase = true)
                    } ?: PoleVideoCaptureSource.CAMERA
                PoleVideoCaptureRoute(
                    poleId = captureVideoPoleId,
                    source = captureVideoSource,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.Map.route,
                arguments =
                    listOf(
                        navArgument("focusPoleId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("focusLat") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("focusLng") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("isProposed") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
            ) { backStackEntry ->
                val focusPoleId = backStackEntry.arguments?.getString("focusPoleId")
                val focusLat = backStackEntry.arguments?.getString("focusLat")?.toDoubleOrNull()
                val focusLng = backStackEntry.arguments?.getString("focusLng")?.toDoubleOrNull()
                val isProposed = backStackEntry.arguments?.getBoolean("isProposed") ?: false
                MapScreen(
                    initialFocusPoleId = focusPoleId,
                    initialFocusLat = focusLat,
                    initialFocusLng = focusLng,
                    initialIsProposed = isProposed,
                    onOpenPoleDetail = { poleId ->
                        navController.navigate(Routes.PoleDetail.createRoute(poleId))
                    },
                    onBack =
                        if (navController.previousBackStackEntry != null) {
                            { navController.popBackStack() }
                        } else {
                            null
                        },
                )
            }
            composable(Routes.Profile.route) {
                ProfileRoute(
                    unreadNotificationCount = unreadNotificationCount,
                    onOpenNotifications = { navController.navigate(Routes.Notification.route) },
                )
            }
            composable(Routes.Notification.route) {
                // Refreshes the shared badge on ANY way this destination leaves composition - the
                // explicit back arrow, the system Back gesture/button, or navigating onward to a
                // work order - not just the arrow's onClick, which the system gesture never calls.
                DisposableEffect(Unit) {
                    onDispose { notificationBadgeViewModel.refresh() }
                }
                NotificationListRoute(
                    onBack = { navController.popBackStack() },
                    onOpenWorkOrder = { workOrderId ->
                        navController.navigate(Routes.WorkOrderDetail.createRoute(workOrderId))
                    },
                    onOpenSurveyHub = { workOrderId ->
                        navController.navigate(Routes.Survey.createRoute(workOrderId))
                    },
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
                    onCaptureEvidence = { kind ->
                        navController.navigate(Routes.WorkOrderEvidenceCapture.createRoute(workOrderId, kind.wireValue))
                    },
                    onStartSurvey = {
                        navController.navigate(Routes.Survey.createRoute(workOrderId))
                    },
                    onOpenPdf = { attachmentId ->
                        navController.navigate(Routes.PoleGpsPdfViewer.createRoute(workOrderId, attachmentId))
                    },
                    onMeasurePole = { poleId ->
                        navController.navigate(Routes.PoleGpsMeasure.createRoute(workOrderId, poleId))
                    },
                    onOpenMap = { poleId, lat, lng, isProposed ->
                        navController.navigate(
                            Routes.Map.createRoute(
                                focusPoleId = poleId,
                                focusLat = lat,
                                focusLng = lng,
                                isProposed = isProposed,
                            ),
                        )
                    },
                )
            }
            composable(
                route = Routes.WorkOrderEvidenceCapture.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("kind") {
                            type = NavType.StringType
                            defaultValue = "after"
                        },
                    ),
            ) {
                EvidenceCaptureRoute(onDone = { navController.popBackStack() })
            }
            composable(
                route = Routes.PoleGpsMeasure.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("poleId") { type = NavType.StringType },
                    ),
            ) {
                PoleGpsMeasureRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PoleGpsPdfViewer.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("attachmentId") { type = NavType.StringType },
                    ),
            ) {
                PoleGpsPdfViewerRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
