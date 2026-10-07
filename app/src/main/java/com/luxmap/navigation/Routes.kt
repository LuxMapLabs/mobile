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
        override val route = "survey"
    }

    data object SurveyCapture : Routes {
        override val route = "survey/capture/{surveySweepId}"

        fun createRoute(surveySweepId: String) = "survey/capture/$surveySweepId"
    }

    data object SurveyReview : Routes {
        override val route = "survey/review/{sessionId}"

        fun createRoute(sessionId: String) = "survey/review/$sessionId"
    }

    data object SurveySubmit : Routes {
        override val route = "survey/submit/{sessionId}"

        fun createRoute(sessionId: String) = "survey/submit/$sessionId"
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

    data object WorkOrderCompletion : Routes {
        override val route = "work-order/{workOrderId}/complete"

        fun createRoute(workOrderId: String) = "work-order/$workOrderId/complete"
    }

    data object ReportSubmit : Routes {
        override val route = "report/submit"
    }

    data object ReportList : Routes {
        override val route = "report/list"
    }

    data object ReportDetail : Routes {
        override val route = "report/detail/{reportId}"
    }

    data object ReportFeedback : Routes {
        override val route = "report/feedback/{reportId}"
    }

    data object Document : Routes {
        override val route = "document"
    }

    data object Assistant : Routes {
        override val route = "assistant"
    }

    data object Notification : Routes {
        override val route = "notification"
    }

    data object Profile : Routes {
        override val route = "profile"
    }
}
