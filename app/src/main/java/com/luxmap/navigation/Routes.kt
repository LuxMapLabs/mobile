package com.luxmap.navigation

sealed interface Routes {
    val route: String

    data object Login : Routes {
        override val route = "auth/login"
    }

    data object Home : Routes {
        override val route = "home"
    }

    data object Survey : Routes {
        // highlightWorkOrderId is an optional query param (same reasoning as SurveyCapture's
        // workOrderId below) - most entries into the Hub have nothing to highlight.
        override val route = "survey?highlightWorkOrderId={highlightWorkOrderId}"

        fun createRoute(highlightWorkOrderId: String? = null) =
            if (highlightWorkOrderId != null) "survey?highlightWorkOrderId=$highlightWorkOrderId" else "survey"
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
        override val route = "survey/pole-photos/{workOrderId}/capture/{poleId}?source={source}"

        fun createRoute(
            workOrderId: String,
            poleId: String,
            source: String = "camera",
        ) = "survey/pole-photos/$workOrderId/capture/$poleId?source=$source"
    }

    data object PoleVideoCapture : Routes {
        override val route = "survey/pole-videos/{workOrderId}/capture/{poleId}?source={source}"

        fun createRoute(
            workOrderId: String,
            poleId: String,
            source: String = "camera",
        ) = "survey/pole-videos/$workOrderId/capture/$poleId?source=$source"
    }

    data object AssetImportCapture : Routes {
        override val route = "asset-import/{tripId}/capture"

        fun createRoute(tripId: String) = "asset-import/$tripId/capture"
    }

    data object AssetImportTripList : Routes {
        override val route = "asset-import/{tripId}/list"

        fun createRoute(tripId: String) = "asset-import/$tripId/list"
    }

    data object AssetImportReview : Routes {
        override val route = "asset-import/{tripId}/review"

        fun createRoute(tripId: String) = "asset-import/$tripId/review"
    }

    data object AssetImportPending : Routes {
        override val route = "asset-import/{tripId}/pending"

        fun createRoute(tripId: String) = "asset-import/$tripId/pending"
    }

    data object Map : Routes {
        override val route = "map"
    }

    data object PoleDetail : Routes {
        override val route = "map/pole/{poleId}"

        fun createRoute(poleId: String) = "map/pole/$poleId"
    }

    data object WorkOrderDetail : Routes {
        override val route = "work-order/{workOrderId}"

        fun createRoute(workOrderId: String) = "work-order/$workOrderId"
    }

    data object WorkOrderEvidenceCapture : Routes {
        override val route = "work-order/{workOrderId}/complete/evidence"

        fun createRoute(workOrderId: String) = "work-order/$workOrderId/complete/evidence"
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

    data object PoleLocationList : Routes {
        override val route = "pole-location"
    }

    data object PoleLocationCapture : Routes {
        override val route = "pole-location/{poleId}/capture"

        fun createRoute(poleId: String) = "pole-location/$poleId/capture"
    }

    data object Notification : Routes {
        override val route = "notification"
    }

    data object Profile : Routes {
        override val route = "profile"
    }
}
