package com.shimul.meetingsummarizer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shimul.meetingsummarizer.ui.screens.detail.MeetingDetailScreen
import com.shimul.meetingsummarizer.ui.screens.home.HomeScreen
import com.shimul.meetingsummarizer.ui.screens.importfile.ImportScreen
import com.shimul.meetingsummarizer.ui.screens.record.RecordScreen
import com.shimul.meetingsummarizer.ui.screens.settings.SettingsScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onRecord = { navController.navigate(Screen.Record.route) },
                onImport = { navController.navigate(Screen.Import.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onOpenMeeting = { id -> navController.navigate(Screen.Detail.createRoute(id)) }
            )
        }
        composable(Screen.Record.route) {
            RecordScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(Screen.Detail.createRoute(id)) {
                        // Back from detail should go Home, not back to Record.
                        popUpTo(Screen.Record.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Import.route) {
            ImportScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument(Screen.Detail.ARG_MEETING_ID) {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString(Screen.Detail.ARG_MEETING_ID).orEmpty()
            MeetingDetailScreen(
                meetingId = meetingId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
