package com.luxmap.navigation

import android.net.Uri

sealed interface Routes {
    val route: String

    data object Login : Routes {
        override val route = "auth/login"
    }

    data object Home : Routes {
        override val route = "home"
    }

    data object History : Routes {
        override val route = "history"
    }

    data object PolePhotoList : Routes {
        override val route = "survey/pole-photos/{workOrderId}"

        fun createRoute(workOrderId: String) = "survey/pole-photos/$workOrderId"
    }

    data object PolePhotoResult : Routes {
        override val route = "survey/pole-photos/{workOrderId}/result/{photoId}"

        fun createRoute(
            workOrderId: String,
            photoId: String,
        ) = "survey/pole-photos/$workOrderId/result/$photoId"
    }

    data object PolePhotoCapture : Routes {
        override val route =
            "survey/pole-photos/{workOrderId}/capture/{poleId}?source={source}&route={route}&saved={saved}"

        // route = true walks the survey route pole by pole (see PolePhotoCaptureViewModel.isRouteMode);
        // saved carries what happened to the previous pole (RouteHandoff.encode) for its banner.
        fun createRoute(
            workOrderId: String,
            poleId: String,
            source: String = "camera",
            route: Boolean = false,
            saved: String? = null,
        ): String {
            val base = "survey/pole-photos/$workOrderId/capture/$poleId?source=$source&route=$route"
            return if (saved == null) base else "$base&saved=${Uri.encode(saved)}"
        }
    }

    // End-of-route check before "Hoàn tất": poles where the AI disagrees with the engineer.
    data object PolePhotoSummary : Routes {
        override val route = "survey/pole-photos/{workOrderId}/summary"

        fun createRoute(workOrderId: String) = "survey/pole-photos/$workOrderId/summary"
    }

    data object PoleVideoCapture : Routes {
        override val route = "survey/pole-videos/{workOrderId}/capture/{poleId}?source={source}"

        fun createRoute(
            workOrderId: String,
            poleId: String,
            source: String = "camera",
        ) = "survey/pole-videos/$workOrderId/capture/$poleId?source=$source"
    }

    data object Map : Routes {
        override val route =
            "map?focusPoleId={focusPoleId}&focusLat={focusLat}&focusLng={focusLng}&isProposed={isProposed}"

        fun createRoute(
            focusPoleId: String? = null,
            focusLat: Double? = null,
            focusLng: Double? = null,
            isProposed: Boolean = false,
        ): String {
            val params = mutableListOf<String>()
            if (!focusPoleId.isNullOrBlank()) params.add("focusPoleId=$focusPoleId")
            if (focusLat != null) params.add("focusLat=$focusLat")
            if (focusLng != null) params.add("focusLng=$focusLng")
            if (isProposed) params.add("isProposed=true")
            return if (params.isEmpty()) "map" else "map?" + params.joinToString("&")
        }
    }

    data object PoleDetail : Routes {
        override val route = "map/pole/{poleId}"

        fun createRoute(poleId: String) = "map/pole/$poleId"
    }

    data object WorkOrderDetail : Routes {
        override val route = "work-order/{workOrderId}"

        fun createRoute(workOrderId: String) = "work-order/$workOrderId"
    }

    data object PoleGpsMeasure : Routes {
        override val route = "work-order/{workOrderId}/pole-gps/{poleId}"

        fun createRoute(
            workOrderId: String,
            poleId: String,
        ) = "work-order/$workOrderId/pole-gps/$poleId"
    }

    data object PoleGpsPdfViewer : Routes {
        override val route = "work-order/{workOrderId}/pole-gps/pdf/{attachmentId}"

        fun createRoute(
            workOrderId: String,
            attachmentId: String,
        ) = "work-order/$workOrderId/pole-gps/pdf/$attachmentId"
    }

    data object Notification : Routes {
        override val route = "notification"
    }

    data object Profile : Routes {
        override val route = "profile"
    }
}
