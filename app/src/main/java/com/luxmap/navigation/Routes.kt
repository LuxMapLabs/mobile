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

    data object WorkOrderEvidenceCapture : Routes {
        override val route = "work-order/{workOrderId}/complete/evidence?kind={kind}"

        fun createRoute(
            workOrderId: String,
            kind: String,
        ) = "work-order/$workOrderId/complete/evidence?kind=$kind"
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
