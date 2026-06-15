package com.shimul.meetingsummarizer.ui.navigation

/** Type-safe-ish route definitions for the app's screens. */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Record : Screen("record")
    data object Import : Screen("import")
    data object Settings : Screen("settings")

    // Meeting detail takes a meeting id argument.
    data object Detail : Screen("detail/{meetingId}") {
        const val ARG_MEETING_ID = "meetingId"
        fun createRoute(meetingId: String) = "detail/$meetingId"
    }
}
