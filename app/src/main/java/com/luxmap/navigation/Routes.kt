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

    data object SurveyCapture : Routes {
        // workOrderId is a query param, not a required path segment, because some entry points
        // (F03 standalone plan, F05 redo) have no work order to pass yet - a required path
        // segment with an empty value does not match the route pattern and crashes navigate().
        override val route = "survey/capture/{surveySweepId}?workOrderId={workOrderId}"

        fun createRoute(
            workOrderId: String,
            surveySweepId: String,
        ) = "survey/capture/$surveySweepId?workOrderId=$workOrderId"
    }

    data object SurveyPlan : Routes {
        override val route = "survey/plan/{workOrderId}"

        fun createRoute(workOrderId: String) = "survey/plan/$workOrderId"
    }

    data object SurveyReview : Routes {
        override val route = "survey/review/{sessionId}"

        fun createRoute(sessionId: String) = "survey/review/$sessionId"
    }

    data object SurveySubmit : Routes {
        override val route = "survey/submit/{sessionId}"

        fun createRoute(sessionId: String) = "survey/submit/$sessionId"
    }

    data object AiConfirmation : Routes {
        override val route = "survey/ai-confirmation/{surveySweepId}"

        fun createRoute(surveySweepId: String) = "survey/ai-confirmation/$surveySweepId"
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

    data object Notification : Routes {
        override val route = "notification"
    }

    data object Profile : Routes {
        override val route = "profile"
    }
}
