package com.civicflow.navigation

sealed interface Routes {
    val route: String

    data object Login : Routes {
        override val route = "auth/login"
    }

    data object Home : Routes {
        override val route = "home"
    }

    data object Map : Routes {
        override val route = "map"
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
